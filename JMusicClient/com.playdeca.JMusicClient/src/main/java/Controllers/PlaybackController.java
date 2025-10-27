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

    public synchronized void updateState(PlaybackState newState, boolean shouldBroadcast) {
        if (memoryState == null) {
            memoryState = playbackStateService.getState();
        }

        if (newState.getCurrentSongId() != null
                && newState.getCurrentSongId().equals(memoryState.getCurrentSongId())
                && newState.getCurrentTime() == 0) {
            newState.setCurrentTime(memoryState.getCurrentTime());
            newState.setArtistName(memoryState.getArtistName());
            newState.setSongName(memoryState.getSongName());
        }

        if (newState.getCue() == null) {
            newState.setCue(new ArrayList<>());
        }
        if (newState.getLastSongs() == null) {
            newState.setLastSongs(new ArrayList<>());
        }

        memoryState = newState;

        maybePersistThrottled(); // persist only

        if (shouldBroadcast && ws != null) {
            ws.broadcastAll();
        }
    }

    // Updated maybePersistThrottled()
    private synchronized void maybePersistThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastSaveTime >= MIN_SAVE_INTERVAL_MS) {
            persistMemoryState(false);
        }
    }

    private PlaybackState lastPersistedState = null;

    // Updated persistMemoryState()
    private synchronized void persistMemoryState(boolean force) {
        if (memoryState == null) {
            return;
        }
        long now = System.currentTimeMillis();

        if (!force && now - lastSaveTime < MIN_SAVE_INTERVAL_MS) {
            return;
        }

        // Avoid persisting if nothing has changed
        if (!force && memoryState.equals(lastPersistedState)) {
            return;
        }

        System.out.println("[PlaybackController] Persisting in-memory state: " + safeSummary(memoryState));

        playbackStateService.updateState(memoryState);

        try {
            lastPersistedState = mapper.readValue(mapper.writeValueAsString(memoryState), PlaybackState.class);
        } catch (IOException e) {
            System.out.println("Error Persisting memory!");
            lastPersistedState = memoryState;
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
            updateState(incoming, true);
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
            Song newSong = findSong(id);
            st.setArtistName(newSong != null ? newSong.getArtist() : "Unknown Artist");
            st.setSongName(newSong != null ? newSong.getTitle() : "Unknown Title");
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

        updateState(st, true);
        System.out.println("Updated Selection");
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
        Song newSong = findSong(songId);
        st.setArtistName(newSong != null ? newSong.getArtist() : "Unknown Artist");
        st.setSongName(newSong != null ? newSong.getTitle() : "Unknown Title");
        st.setPlaying(true);
        st.setCurrentTime(0);
        updateLastSongs(st, songId);

        updateState(st, true); // persists + broadcasts
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
        updateState(st, true);
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
            if (forward && !st.isRepeatEnabled()) {
                // Remove current song if not repeating
                if (idx >= 0 && idx < cue.size()) {
                    cue.remove(idx);
                    if (idx >= cue.size()) {
                        idx = cue.size() - 1;
                    }
                }
            }

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
        Song newSong = findSong(songId);
        st.setArtistName(newSong != null ? newSong.getArtist() : "Unknown Artist");
        st.setSongName(newSong != null ? newSong.getTitle() : "Unknown Title");
        st.setCurrentTime(0);
        st.setPlaying(true);
        updateLastSongs(st, songId);

        updateState(st, true);
    }

    public synchronized void next() {
        System.out.println("Next");
        advanceSong(true);
    }

    public synchronized void previous() {
        System.out.println("Previous");
        advanceSong(false);
    }

    public synchronized void togglePlay() {
        System.out.println("Toggle");
        PlaybackState state = getState();
        state.setPlaying(!state.isPlaying());
        updateState(state, true);
    }

    /**
     * Toggles shuffle mode on/off and persists state
     */
    public synchronized void toggleShuffle() {
        PlaybackState state = getState();
        state.setShuffleEnabled(!state.isShuffleEnabled());
        updateState(state, true);
    }

    /**
     * Toggles repeat mode on/off and persists state
     */
    public synchronized void toggleRepeat() {
        PlaybackState state = getState();
        state.setRepeatEnabled(!state.isRepeatEnabled());
        updateState(state, true);
    }

    /**
     * Sets the playback volume (0.0 - 1.0) and persists state
     */
    public synchronized void changeVolume(float level) {
        PlaybackState st = getState();
        st.setVolume(Math.max(0f, Math.min(1f, level)));
        updateState(st, true);
    }

    /**
     * Sets the playback position in seconds and persists state
     */
    public synchronized void setSeconds(double seconds) {
        PlaybackState st = getState();
        st.setCurrentTime(Math.max(0, seconds));
        updateState(st, true);
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

    public synchronized void replaceQueueWithPlaylist(Playlist playlist) {
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

        updateState(st, true); // persist + broadcast 
    }

    // -----------------------------
// Queue management helpers
// -----------------------------
    /**
     * Adds one or more songs to the current queue. If `playNext` is true, songs
     * are inserted after the current index. Otherwise, appended to the end.
     */
    public synchronized void addToQueue(List<Long> songIds, boolean playNext) {
        PlaybackState st = getState();
        if (st.getCue() == null) {
            st.setCue(new ArrayList<>());
        }

        List<Long> cue = st.getCue();
        int insertIndex = playNext
                ? Math.min(st.getCueIndex() + 1, cue.size())
                : cue.size();

        for (Long id : songIds) {
            if (!cue.contains(id)) {
                cue.add(insertIndex, id);
                insertIndex++;
            }
        }

        st.setCue(cue);
        updateState(st, true);
    }

    /**
     * Removes a specific song from the queue. If it's currently playing, moves
     * to next or stops.
     */
    public synchronized void removeFromQueue(Long songId) {
        PlaybackState st = getState();
        List<Long> cue = st.getCue();
        if (cue == null || cue.isEmpty()) {
            return;
        }

        int currentIndex = st.getCueIndex();
        Long currentId = st.getCurrentSongId();

        int removeIndex = cue.indexOf(songId);
        if (removeIndex == -1) {
            return;
        }

        cue.remove(removeIndex);

        if (songId.equals(currentId)) {
            // If we removed the currently playing song
            if (cue.isEmpty()) {
                stopPlayback();
                return;
            }
            // Try to move to next song (wrap if necessary)
            currentIndex = Math.min(removeIndex, cue.size() - 1);
            st.setCueIndex(currentIndex);
            st.setCurrentSongId(cue.get(currentIndex));
            st.setCurrentTime(0);
        } else if (removeIndex < currentIndex) {
            st.setCueIndex(currentIndex - 1);
        }

        st.setCue(cue);
        updateState(st, true);
    }

    /**
     * Clears the entire queue and stops playback.
     */
    public synchronized void clearQueue() {
        PlaybackState st = getState();
        st.setCue(new ArrayList<>());
        st.setCueIndex(-1);
        st.setCurrentSongId(null);
        st.setPlaying(false);
        st.setCurrentTime(0);
        updateState(st, true);
    }

    /**
     * Moves a song within the queue (drag-and-drop style reordering).
     */
    public synchronized void moveInQueue(int fromIndex, int toIndex) {
        PlaybackState st = getState();
        List<Long> cue = st.getCue();
        if (cue == null || cue.isEmpty()) {
            return;
        }

        if (fromIndex < 0 || fromIndex >= cue.size()
                || toIndex < 0 || toIndex >= cue.size()) {
            return;
        }

        Long songId = cue.remove(fromIndex);
        cue.add(toIndex, songId);

        // Adjust cue index if needed
        int currentIdx = st.getCueIndex();
        if (fromIndex == currentIdx) {
            st.setCueIndex(toIndex);
        } else if (fromIndex < currentIdx && toIndex >= currentIdx) {
            st.setCueIndex(currentIdx - 1);
        } else if (fromIndex > currentIdx && toIndex <= currentIdx) {
            st.setCueIndex(currentIdx + 1);
        }

        st.setCue(cue);
        updateState(st, true);
    }

    /**
     * Sets the currently selected playlist for UI purposes only. Does NOT alter
     * the current queue or playback.
     */
    public synchronized void selectPlaylistForBrowsing(Playlist playlist) {
        PlaybackState st = getState();
        st.setCurrentPlaylistId(playlist != null ? playlist.id : null);
        updateState(st, true); // only persists & broadcasts selection
    }

    /**
     * Adds all songs from a playlist to the queue.
     *
     * @param playlist the playlist whose songs to add
     * @param playNext if true, insert after current song; else append at end
     */
    public synchronized void addPlaylistToQueue(Playlist playlist, boolean playNext) {
        if (playlist == null || playlist.getSongs() == null || playlist.getSongs().isEmpty()) {
            return;
        }
        List<Long> songIds = playlist.getSongs().stream()
                .map(s -> s.id)
                .toList();
        addToQueue(songIds, playNext);
    }

}
