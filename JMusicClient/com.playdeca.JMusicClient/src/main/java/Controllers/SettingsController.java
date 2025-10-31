package Controllers;

import API.WS.LogSocket;
import API.WS.MusicSocket;
import jakarta.annotation.PostConstruct;
import Models.Settings;
import Models.SettingsLog;
import Models.Song;
import Services.SettingsService;
import Services.SongService;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped; 
import jakarta.transaction.Transactional;
import java.io.File;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Collectors;

import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

@ApplicationScoped
public class SettingsController implements Serializable {

    @Inject
    private SongService songService;

    @Inject
    private SettingsService settingsService;

    @Inject
    private MusicSocket musicSocket;

    @Inject
    private LogSocket logSocket;

    private String musicLibraryPath;

    public void toggleAsService() {
        Settings currentSettings = settingsService.getOrCreateSettings();
        currentSettings.setRunAsService(!currentSettings.getRunAsService());
        settingsService.save(currentSettings);
        addLog("Run-as-service toggled");
    }

    @PostConstruct
    public void init() {
        if (musicLibraryPath == null) {
            musicLibraryPath = getDefaultMusicFolder();
            addLog("Default music folder initialized: " + musicLibraryPath);
        }
    }

    public void selectMusicLibrary() {
        addLog("Music library set to: " + musicLibraryPath);
    }

    @Transactional
    public void scanLibrary() {
        addLog("Scanning music library: " + musicLibraryPath);

        File folder = getMusicFolder();
        if (!folder.exists() || !folder.isDirectory()) {
            addLog("Music folder does not exist: " + folder.getAbsolutePath());
            return;
        }

        int totalAdded = scanFolderRecursively(folder);
        addLog("Scan completed. Total MP3 files added: " + totalAdded);
        musicSocket.broadcastAll(); // Trigger UI refresh
    }

    private int scanFolderRecursively(File folder) {
        int addedCount = 0;

        File[] files = folder.listFiles();
        if (files == null) {
            return addedCount;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                addedCount += scanFolderRecursively(file);
            } else if (file.isFile() && file.getName().toLowerCase().endsWith(".mp3")) {
                try {
                    File baseFolder = getMusicFolder();
                    String relativePath = baseFolder.toURI().relativize(file.toURI()).getPath();

                    Song song = songService.findByPath(relativePath);
                    if (song == null) {
                        song = new Song();
                        song.setPath(relativePath);
                    }

                    MP3File mp3File = (MP3File) AudioFileIO.read(file);
                    Tag tag = mp3File.getTag();

                    if (tag != null) {
                        song.setTitle(tag.getFirst(FieldKey.TITLE));
                        song.setArtist(tag.getFirst(FieldKey.ARTIST));
                        song.setAlbum(tag.getFirst(FieldKey.ALBUM));
                    } else {
                        String fileName = file.getName();
                        if (fileName.toLowerCase().endsWith(".mp3")) {
                            fileName = fileName.substring(0, fileName.length() - 4);
                        }
                        song.setTitle(fileName);
                        song.setArtist("Unknown Artist");
                    }
                    int trackLength = mp3File.getAudioHeader().getTrackLength();
                    addLog("DEBUG: Read duration for " + file.getName() + ": " + trackLength + " seconds.");
                    song.setDurationSeconds(trackLength);
                    addLog("DEBUG: Song object duration set to: " + song.getDurationSeconds() + " seconds.");

                    // Check for potentially corrupt files (Unknown Artist and 0:00 duration)
                    if (("Unknown Artist".equals(song.getArtist()) || song.getArtist() == null || song.getArtist().isBlank()) && song.getDurationSeconds() == 0) {
                        addLog("WARNING: Skipping potentially corrupt song (Unknown Artist and 0:00 duration): " + file.getName());
                        continue; // Skip saving this song
                    }

                    songService.save(song);
                    addLog("DEBUG: Song saved to service for " + file.getName() + ".");

                    addLog("Added/Updated song: " + file.getName() + " (title: " + song.getTitle() + ", artist: " + song.getArtist() + ")");
                    addedCount++;
                } catch (org.jaudiotagger.audio.exceptions.InvalidAudioFrameException e) {
                    addLog("WARNING: Skipping song " + file.getName() + " due to invalid audio frame (corrupted/malformed file?): " + e.getMessage());
                } catch (Exception e) {
                    addLog("Failed to add/update song " + file.getName() + ": " + e.getMessage(), e);
                }
            }
        }

        return addedCount;
    }

    public void clearLogs() {
        Settings settings = settingsService.getOrCreateSettings();
        settings.getLogs().clear();
        addLog("Logs cleared.");
        settingsService.save(settings);
    }

    private String getDefaultMusicFolder() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();
        File musicFolder;

        if (os.contains("win")) {
            String winProfile = System.getenv("USERPROFILE");
            if (winProfile != null && !winProfile.isBlank()) {
                userHome = winProfile;
            }
        }

        musicFolder = new File(userHome, "Music");

        if (!musicFolder.exists()) {
            boolean created = musicFolder.mkdirs();
            if (created) {
                addLog("Created default Music folder at: " + musicFolder.getAbsolutePath());
            } else {
                addLog("Failed to create default Music folder, using home directory instead.");
                return userHome;
            }
        }

        return musicFolder.getAbsolutePath();
    }

    public File getMusicFolder() {
        return new File(musicLibraryPath);
    }

    public void resetMusicLibrary() {
        musicLibraryPath = getDefaultMusicFolder();
        addLog("Music library reset to default: " + musicLibraryPath);
    }

    public List<String> getLogs() {
        return settingsService.getOrCreateSettings().getLogs().stream()
                .map(SettingsLog::getMessage)
                .collect(Collectors.toList());
    }

    public void addLog(String message, Throwable t) {
        Settings settings = settingsService.getOrCreateSettings();
        SettingsLog log = new SettingsLog();
        if (t != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            log.setMessage(message + "\n" + sw.toString());
        } else {
            log.setMessage(message);
        }
        settings.getLogs().add(log);
        settingsService.save(settings);
        logSocket.broadcast(log.getMessage());
    }

    public void addLog(String message) {
        addLog(message, null);
    }

    public String getMusicLibraryPath() {
        return musicLibraryPath;
    }

    public void setMusicLibraryPath(String path) {
        this.musicLibraryPath = path;
    }

    public Settings getOrCreateSettings() {
        return settingsService.getOrCreateSettings();
    }

    public void reloadAllSongsMetadata() {
        addLog("Reloading metadata for all songs...");
        List<Song> allSongs = songService.findAll();
        int updatedCount = 0;

        for (Song song : allSongs) {
            try {
                File songFile = new File(getMusicFolder(), song.getPath());
                if (songFile.exists() && songFile.isFile()) {
                    MP3File mp3File = (MP3File) AudioFileIO.read(songFile);
                    Tag tag = mp3File.getTag();

                    // Update existing song with new metadata
                    if (tag != null) {
                        song.setTitle(tag.getFirst(FieldKey.TITLE));
                        song.setArtist(tag.getFirst(FieldKey.ARTIST));
                        song.setAlbum(tag.getFirst(FieldKey.ALBUM));
                    } else {
                        String fileName = songFile.getName();
                        if (fileName.toLowerCase().endsWith(".mp3")) {
                            fileName = fileName.substring(0, fileName.length() - 4);
                        }
                        song.setTitle(fileName);
                        song.setArtist("Unknown Artist");
                    }
                    int trackLength = mp3File.getAudioHeader().getTrackLength();
                    addLog("DEBUG: Reloaded duration for " + song.getPath() + ": " + trackLength + " seconds.");
                    song.setDurationSeconds(trackLength);
                    addLog("DEBUG: Song object duration set to: " + song.getDurationSeconds() + " seconds.");

                    // Check for potentially corrupt files (Unknown Artist and 0:00 duration)
                    if (("Unknown Artist".equals(song.getArtist()) || song.getArtist() == null || song.getArtist().isBlank()) && song.getDurationSeconds() == 0) {
                        addLog("WARNING: Skipping potentially corrupt song (Unknown Artist and 0:00 duration) during reload: " + song.getPath());
                        continue; // Skip saving this song
                    }

                    songService.save(song); // This will merge the changes to the existing entity
                    addLog("DEBUG: Song saved to service for " + song.getPath() + ".");
                    updatedCount++;
                } else {
                    addLog("Skipping metadata reload for missing file: " + song.getPath());
                }
            } catch (org.jaudiotagger.audio.exceptions.InvalidAudioFrameException e) {
                addLog("WARNING: Skipping metadata reload for " + song.getPath() + " due to invalid audio frame (corrupted/malformed file?): " + e.getMessage());
            } catch (Exception e) {
                addLog("Failed to reload metadata for song " + song.getPath() + ": " + e.getMessage(), e);
            }
            addLog("Metadata reload completed. " + updatedCount + " songs updated.");
            musicSocket.broadcastAll(); // Trigger UI refresh
        }
    }

    public void deleteDuplicateSongs() {
        addLog("Deleting duplicate songs...");
        List<Song> allSongs = songService.findAll();
        List<Song> songsToDelete = new java.util.ArrayList<>();
        java.util.Set<String> uniqueSongs = new java.util.HashSet<>();

        for (Song song : allSongs) {
            // Using a combination of title, artist, album, and duration to identify duplicates
            String songIdentifier = song.getTitle() + "-" + song.getArtist() + "-" + song.getAlbum() + "-" + song.getDurationSeconds();
            if (uniqueSongs.contains(songIdentifier)) {
                songsToDelete.add(song);
            } else {
                uniqueSongs.add(songIdentifier);
            }
        }

        for (Song song : songsToDelete) {
            try {
                songService.delete(song);
                addLog("Deleted duplicate song: " + song.getTitle() + " by " + song.getArtist());
            } catch (Exception e) {
                addLog("Failed to delete duplicate song " + song.getTitle() + ": " + e.getMessage());
            }
        }
        addLog("Duplicate deletion completed. " + songsToDelete.size() + " songs deleted.");
        musicSocket.broadcastAll(); // Trigger UI refresh
    }

}
