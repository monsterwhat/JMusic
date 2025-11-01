package API.WS;

import Controllers.TorrentController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.UUID;
import java.util.Base64;
import jakarta.websocket.CloseReason;

@ServerEndpoint("/api/torrents/ws")
@ApplicationScoped
public class TorrentSocket {

    @Inject
    WebSocketManager webSocketManager;

    @Inject
    TorrentController controller;

    private final ObjectMapper mapper = new ObjectMapper();

    @OnOpen
    public void onOpen(Session session) {
        webSocketManager.addTorrentSession(session);
        System.out.println("[Socket] Connection opened: " + session.getId());
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        try {
            JsonNode node = mapper.readTree(message);
            String type = node.get("type").asText();
            JsonNode payload = node.get("payload");

            if ("rate".equals(type)) {
                UUID torrentId = UUID.fromString(payload.get("torrentId").asText());
                UUID peerId = UUID.fromString(payload.get("peerId").asText());
                boolean liked = payload.get("liked").asBoolean();
                byte[] signature = Base64.getDecoder().decode(payload.get("signature").asText());
                controller.onPeerRatedTorrent(torrentId, peerId, liked, signature);
            } else if ("register_peer".equals(type)) {
                String peerId = payload.get("peerId").asText();
                String publicKey = payload.get("publicKey").asText();
                // Attempt to get IP address from the session
                String ipAddress = null;
                try {
                    ipAddress = session.getUserProperties().get("jakarta.websocket.endpoint.remoteAddress").toString();
                } catch (Exception e) {
                    System.err.println("Could not get remote address for peer: " + peerId + ", " + e.getMessage());
                }
                boolean isBanned = controller.onPeerRegistered(peerId, publicKey, ipAddress);
                if (isBanned) {
                    try {
                        session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Banned peer"));
                    } catch (IOException e) {
                        System.err.println("Error closing banned peer session: " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @OnClose
    public void onClose(Session session) {
        webSocketManager.removeTorrentSession(session);
        System.out.println("[Socket] Connection closed: " + session.getId());
    }
}
