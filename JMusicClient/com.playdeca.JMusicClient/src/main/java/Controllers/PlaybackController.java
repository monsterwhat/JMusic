package Controllers;

import API.WS.MusicWebSocket;
import Models.PlaybackState;
import Models.Playlist;
import Models.Settings;
import Models.Song;
import Services.PlaybackStateService;
import Services.PlaylistService;
import Services.SongService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PlaybackController {

    private static final long MIN_SAVE_INTERVAL_MS = 1000; // 1 second between saves

    private final ObjectMapper mapper = new ObjectMapper();
    private PlaybackState memoryState;
    private long lastSaveTime = 0;

    @Inject
    PlaybackStateService playbackStateService;
    @Inject
    SettingsController currentSettings;
    @Inject
    SongService songService;
    @Inject
    PlaylistService playlistService;
    @Inject
    MusicWebSocket ws;

    public PlaybackController() {
        System.out.println("[PlaybackController] PlaybackController instance created");
    }

    @PostConstruct
    public void init() {
        memoryState = playbackStateService.getState();
        if (memoryState == null) {
            memoryState = new PlaybackState();
        }
        System.out.println("[PlaybackController] Initial state loaded: " + safeSummary(memoryState));
    }

    @PreDestroy
    public void onShutdown() {
        System.out.println("[PlaybackController] Shutdown: forcing final persist...");
        persistMemoryState(true);
    }

    // -----------------------------
    // Playback state methods
    // -----------------------------
    public synchronized PlaybackState getState() {
        if (memoryState == null) {
            memoryState = playbackStateService.getState();
            if (memoryState == null) {
                memoryState = new PlaybackState();
            }
        }
        return memoryState;
    }

    public synchronized void updateState(PlaybackState newState) {
        if (memoryState == null) {
            memoryState = playbackStateService.getState();
        }

        PlaybackState current = memoryState;

        // Preserve current time if only metadata updated
        if (newState.getCurrentSongId() != null
                && newState.getCurrentSongId().equals(current.getCurrentSongId())
                && newState.getCurrentTime() == 0) {
            newState.setCurrentTime(current.getCurrentTime());
        }

        if (newState.getCue() == null) {
            newState.setCue(new ArrayList<>());
        }
        if (newState.getLastSongs() == null) {
            newState.setLastSongs(new ArrayList<>());
        }

        memoryState = newState;

        maybePersistThrottled(); // will persist + broadcast if enough time passed
    }

    private synchronized void maybePersistThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastSaveTime >= MIN_SAVE_INTERVAL_MS) {
            persistMemoryState(false);
        }
    }

    private PlaybackState lastPersistedState = null;

    private synchronized void persistMemoryState(boolean force) {
        long now = System.currentTimeMillis();
        if (memoryState == null) {
            return;
        }
        if (!force && now - lastSaveTime < MIN_SAVE_INTERVAL_MS) {
            return;
        }

        // Avoid persisting if nothing has changed
        if (!force && memoryState.equals(lastPersistedState)) {
            return;
        }

        System.out.println("[PlaybackController] Persisting in-memory state: " + safeSummary(memoryState));

        playbackStateService.updateState(memoryState);
        lastPersistedState = memoryState;

        if (ws != null) {
            ws.broadcastAll();
        }

        lastSaveTime = now;
    }

    // -----------------------------
    // JSON serialization/deserialization
    // -----------------------------
    public PlaybackState fromJson(String json) throws IOException {
        if (json == null || json.isEmpty()) {
            return new PlaybackState();
        }
        return mapper.readValue(json, PlaybackState.class);
    }

    public synchronized String toJson() {
        try {
            return mapper.writeValueAsString(getState());
        } catch (IOException e) {
            System.err.println("[PlaybackController] ❌ Failed to serialize state: " + e.getMessage());
            return "{}";
        }
    }

    public synchronized void applyMessage(String json) throws IOException {
        if (json == null || json.isEmpty()) {
            return;
        }

        var node = mapper.readTree(json);
        if (node.has("action")) {
            handleAction(node);
        } else {
            PlaybackState incoming = mapper.treeToValue(node, PlaybackState.class);
            incoming.setVolume(memoryState.getVolume()); // preserve existing
            updateState(incoming);
        }
    }

    public synchronized void selectSong(Long id) {
        PlaybackState st = getState();
        Song current = getCurrentSong();

        if (current != null && current.id.equals(id)) {
            // Toggle play/pause
            st.setPlaying(!st.isPlaying());
        } else {
            st.setCurrentSongId(id);
            st.setPlaying(true);
            st.setCurrentTime(0);

            // Add to cue if missing
            List<Long> cue = st.getCue();
            if (cue == null) {
                cue = new ArrayList<>();
            }
            if (!cue.contains(id)) {
                cue.add(Math.min(st.getCueIndex() + 1, cue.size()), id);
            }
            st.setCue(cue);
            st.setCueIndex(cue.indexOf(id));

            // Update recent songs
            List<Long> last = st.getLastSongs();
            if (last == null) {
                last = new ArrayList<>();
            }
            last.add(id);
            if (last.size() > 5) {
                last.remove(0);
            }
            st.setLastSongs(last);
        }

        updateState(st);
        ws.broadcastAll();
    }

    public synchronized void moveSong(boolean forward) {
        PlaybackState st = getState();
        List<Song> songs = getSongs(); // All available songs
        List<Long> cue = st.getCue();

        if (cue == null || cue.isEmpty()) {
            cue = new ArrayList<>(songs.stream().map(s -> s.id).toList());
            st.setCue(cue);
            st.setCueIndex(0);
            if (cue.isEmpty()) {
                stopPlayback();
                return;
            }
        }

        int idx = st.getCueIndex();
        if (idx < 0 || idx >= cue.size()) {
            idx = 0;
        }

        if (st.isShuffleEnabled()) {
            idx = pickShuffleIndex(st, cue);
        } else {
            idx = forward ? idx + 1 : idx - 1;
            if (idx < 0) {
                idx = st.isRepeatEnabled() ? cue.size() - 1 : 0;
            }
            if (idx >= cue.size()) {
                idx = st.isRepeatEnabled() ? 0 : cue.size() - 1;
            }
        }

        st.setCueIndex(idx);
        Long songId = cue.get(idx);
        st.setCurrentSongId(songId);
        st.setPlaying(true);
        st.setCurrentTime(0);
        updateLastSongs(st, songId);

        updateState(st); // persists + broadcasts
    }

    private synchronized void updateLastSongs(PlaybackState state, Long songId) {
        if (state == null || songId == null) {
            return;
        }

        List<Long> last = state.getLastSongs();
        if (last == null) {
            last = new ArrayList<>();
            state.setLastSongs(last);
        }

        last.add(songId); // append newest
        final int MAX_LAST_SONGS = 5;

        if (last.size() > MAX_LAST_SONGS) {
            // remove oldest
            last.remove(0);
        }
    }

    /**
     * Picks a random index from the cue for shuffle playback. Avoids the last
     * few songs for better shuffle effect.
     */
    private synchronized int pickShuffleIndex(PlaybackState state, List<Long> cue) {
        if (cue == null || cue.isEmpty()) {
            return 0;
        }

        List<Long> last = state.getLastSongs();
        final int maxAttempts = cue.size() * 2;
        int attempts = 0;
        int index;

        do {
            index = (int) (Math.random() * cue.size());
            attempts++;
        } while (last != null && last.contains(cue.get(index)) && attempts < maxAttempts);

        return index;
    }

    private synchronized void stopPlayback() {
        PlaybackState st = getState();
        st.setCurrentSongId(null);
        st.setPlaying(false);
        st.setCueIndex(-1);
        updateState(st);
    }

    private void handleAction(com.fasterxml.jackson.databind.JsonNode node) {
        String action = node.get("action").asText();
        PlaybackState state = getState();

        switch (action) {
            case "seek":
                if (node.has("value")) {
                    state.setCurrentTime(node.get("value").asDouble());
                }
                break;
            case "volume":
                if (node.has("value")) {
                    state.setVolume((float) node.get("value").asDouble());
                }
                break;
            case "toggle-play":
                togglePlay();
                break;
            case "play":
                play();
                break;
            case "pause":
                pause();
                break;
            case "next":
                next();
                break;
            case "previous":
                previous();
                break;
            case "shuffle":
                toggleShuffle();
                break;
            case "repeat":
                toggleRepeat();
                break;
        }

        memoryState = state;
        // Persist throttled to DB
        maybePersistThrottled();
    }

    // -----------------------------
    // Playlist + Song helpers
    // -----------------------------
    public Playlist findPlaylist(Long id) {
        return playlistService.find(id);
    }

    public List<Playlist> getPlaylists() {
        return playlistService.findAll();
    }

    public void createPlaylist(Playlist playlist) {
        playlistService.save(playlist);
    }

    public void updatePlaylist(Playlist playlist) {
        playlistService.save(playlist);
    }

    public void deletePlaylist(Playlist playlist) {
        playlistService.delete(playlist);
    }

    public Playlist getSelectedPlaylist(Long id) {
        return id == null ? null : playlistService.find(id);
    }

    public List<Song> getSongs() {
        return songService.findAll();
    }

    public Song findSong(Long id) {
        return songService.find(id);
    }

    public Settings getSettings() {
        return currentSettings.getOrCreateSettings();
    }

    private String safeSummary(PlaybackState s) {
        if (s == null) {
            return "null";
        }
        return String.format("{playing=%s, songId=%s, time=%.2f, vol=%.2f}",
                s.isPlaying(), s.getCurrentSongId(), s.getCurrentTime(), s.getVolume());
    }

    // Helper method to advance song
    private synchronized void advanceSong(boolean forward) {
        PlaybackState st = getState();
        List<Long> cue = st.getCue();

        // Populate cue if empty
        if (cue == null || cue.isEmpty()) {
            List<Song> allSongs = getSongs();
            cue = allSongs.stream().map(s -> s.id).toList();
            st.setCue(new ArrayList<>(cue));
            st.setCueIndex(cue.isEmpty() ? -1 : 0);
            if (cue.isEmpty()) {
                stopPlayback();
                return;
            }
        }

        int idx = st.getCueIndex();
        if (idx < 0 || idx >= cue.size()) {
            idx = 0;
        }

        // Shuffle logic
        if (st.isShuffleEnabled()) {
            idx = pickShuffleIndex(st, cue);
        } else {
            idx = forward ? idx + 1 : idx - 1;

            if (idx < 0) {
                if (st.isRepeatEnabled()) {
                    idx = cue.size() - 1;
                } else {
                    idx = 0;
                }
            }
            if (idx >= cue.size()) {
                if (st.isRepeatEnabled()) {
                    idx = 0;
                } else {
                    idx = cue.size() - 1;
                }
            }
        }

        Long songId = cue.get(idx);
        st.setCueIndex(idx);
        st.setCurrentSongId(songId);
        st.setCurrentTime(0);
        st.setPlaying(true);
        updateLastSongs(st, songId);

        updateState(st);
        ws.broadcastAll();
    }

    public synchronized void next() {
        advanceSong(true);
    }

    public synchronized void previous() {
        advanceSong(false);
    }

    public synchronized void togglePlay() {
        PlaybackState state = getState();
        state.setPlaying(!state.isPlaying());
        updateState(state);
        ws.broadcastAll();
    }

    public synchronized void pause() {
        PlaybackState state = getState();
        state.setPlaying(false);
        updateState(state);
    }

    public synchronized void play() {
        PlaybackState state = getState();
        state.setPlaying(true);
        updateState(state);
    }

    /**
     * Toggles shuffle mode on/off and persists state
     */
    public synchronized void toggleShuffle() {
        PlaybackState state = getState();
        state.setShuffleEnabled(!state.isShuffleEnabled());
        updateState(state);
        ws.broadcastAll();
    }

    /**
     * Toggles repeat mode on/off and persists state
     */
    public synchronized void toggleRepeat() {
        PlaybackState state = getState();
        state.setRepeatEnabled(!state.isRepeatEnabled());
        updateState(state);
        ws.broadcastAll();
    }

    /**
     * Sets the playback volume (0.0 - 1.0) and persists state
     */
    public synchronized void changeVolume(float level) {
        PlaybackState st = getState();
        st.setVolume(Math.max(0f, Math.min(1f, level)));
        updateState(st);
        ws.broadcastAll();
    }

    /**
     * Sets the playback position in seconds and persists state
     */
    public synchronized void setSeconds(double seconds) {
        PlaybackState st = getState();
        st.setCurrentTime(Math.max(0, seconds));
        updateState(st);
        ws.broadcastAll();
    }

    /**
     * Returns the currently playing song, or null if none
     */
    public synchronized Song getCurrentSong() {
        PlaybackState st = getState();
        Long currentId = st.getCurrentSongId();
        if (currentId != null) {
            return findSong(currentId);
        }
        // If no currentId, fallback to first song in cue or playlist
        List<Long> cue = st.getCue();
        if (cue != null && !cue.isEmpty()) {
            return findSong(cue.get(0));
        }
        List<Song> allSongs = getSongs();
        return allSongs.isEmpty() ? null : allSongs.get(0);
    }

    public synchronized void setCurrentPlaylist(Playlist playlist) {
        PlaybackState st = getState();

        if (playlist == null) {
            // Deselect any playlist
            st.setCurrentPlaylistId(null);

            List<Long> cue = st.getCue();
            // If queue is empty, populate with all songs
            if (cue == null || cue.isEmpty()) {
                List<Song> allSongs = getSongs();
                cue = allSongs.stream().map(s -> s.id).toList();
                st.setCue(new ArrayList<>(cue));
                st.setCueIndex(cue.isEmpty() ? -1 : 0);
                st.setCurrentSongId(cue.isEmpty() ? null : cue.get(0));
                st.setPlaying(!cue.isEmpty());
                st.setCurrentTime(0);
            }

        } else {
            // Set the current playlist normally
            st.setCurrentPlaylistId(playlist.id);

            List<Song> songs = playlist.getSongs();
            if (songs != null && !songs.isEmpty()) {
                List<Long> cue = songs.stream().map(s -> s.id).toList();
                st.setCue(new ArrayList<>(cue));
                st.setCueIndex(0);
                st.setCurrentSongId(cue.get(0));
                st.setCurrentTime(0);
                st.setPlaying(true);
            } else {
                // empty playlist → stop playback
                st.setCue(new ArrayList<>());
                st.setCueIndex(-1);
                st.setCurrentSongId(null);
                st.setCurrentTime(0);
                st.setPlaying(false);
            }
        }

        updateState(st); // persist + broadcast
        ws.broadcastAll();
    }

}
