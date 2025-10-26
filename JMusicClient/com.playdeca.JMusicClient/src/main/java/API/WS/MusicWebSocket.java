package API.WS;

import Controllers.DesktopController;
import Controllers.PlaybackController;
import Models.PlaybackState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/api/music/ws/state")
@ApplicationScoped
public class MusicWebSocket {

    private static final Set<Session> sessions = ConcurrentHashMap.newKeySet();

    @Inject
    PlaybackController playbackController;
    @Inject
    DesktopController viewSession;

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        sendCurrentState(session);
        viewSession.clientConnected();
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        CompletableFuture.runAsync(() -> viewSession.clientDisconnected());
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        CompletableFuture.runAsync(() -> {
            try {
                playbackController.applyMessage(message); // <-- merges instead of replacing 
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void sendCurrentState(Session session) {
        CompletableFuture.runAsync(() -> {
            PlaybackState state = playbackController.getState();
            if (state != null && state.getCurrentSongId() != null) {
                safeSend(session, playbackController.toJson());
            }
        });
    }
 
    public void broadcastAll() {
        PlaybackState state = playbackController.getState();
        if (state == null || state.getCurrentSongId() == null) {
            return;
        }

        String json = playbackController.toJson();

        sessions.forEach(s -> safeSend(s, json));
    }

    /**
     * Safely send a message to a session and remove it if it's closed.
     */
    private void safeSend(Session session, String message) {
        if (session == null) {
            return;
        }

        if (!session.isOpen()) {
            sessions.remove(session);
            return;
        }

        try {
            session.getAsyncRemote().sendText(message, result -> {
                if (result.getException() != null) {
                    System.err.println("Failed to send WS message: " + result.getException());
                    sessions.remove(session);
                }
            });
        } catch (IllegalStateException e) {
            // session might be closed during send
            sessions.remove(session);
        }
    }
}
