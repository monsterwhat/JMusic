package API.WS;

import Controllers.DesktopController;
import Controllers.PlaybackController;
import Models.PlaybackState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@ServerEndpoint("/api/music/ws")
@ApplicationScoped
public class MusicSocket {

    @Inject
    WebSocketManager webSocketManager;

    @Inject
    PlaybackController playbackController;

    @Inject
    DesktopController viewSession;

    private final ObjectMapper mapper = new ObjectMapper();

    @OnOpen
    public void onOpen(Session session) {
        CompletableFuture.runAsync(() -> {
            webSocketManager.addMusicSession(session);
            sendCurrentState(session);
            viewSession.clientConnected();
        });
    }

    @OnClose
    public void onClose(Session session) {
        CompletableFuture.runAsync(() -> {
            webSocketManager.removeMusicSession(session);
            viewSession.clientDisconnected();
        });
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        CompletableFuture.runAsync(() -> {
            try {
                ObjectNode node = mapper.readValue(message, ObjectNode.class);
                String type = node.get("type").asText();
                JsonNode payload = node.get("payload");

                switch (type) {
                    case "seek":
                        playbackController.setSeconds(payload.get("value").asDouble());
                        break;
                    case "volume":
                        playbackController.changeVolume((float) payload.get("value").asDouble());
                        break;
                    case "next":
                        playbackController.next();
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void sendCurrentState(Session session) {
        PlaybackState state = playbackController.getState();
        if (state != null && state.getCurrentSongId() != null) {
            try {
                ObjectNode message = mapper.createObjectNode();
                message.put("type", "state");
                message.set("payload", mapper.valueToTree(state));
                session.getAsyncRemote().sendText(mapper.writeValueAsString(message));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void broadcastAll() {
        PlaybackState state = playbackController.getState();
        if (state == null) {
            return;
        }

        try {
            ObjectNode message = mapper.createObjectNode();
            message.put("type", "state");
            message.set("payload", mapper.valueToTree(state));
            webSocketManager.broadcastToMusic(mapper.writeValueAsString(message));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
