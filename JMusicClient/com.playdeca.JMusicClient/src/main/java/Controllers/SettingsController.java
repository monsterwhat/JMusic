package Controllers;

import Models.Settings;
import Models.Song;
import Services.SettingsService;
import Services.SongService;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class SettingsController implements Serializable {

    @Inject
    private SongService songService;
    
    @Inject 
    private SettingsService settings;

    private String musicLibraryPath;
    private final List<String> logs = new ArrayList<>();

    public void toggleAsService(){
        Settings currentSettings = settings.getOrCreateSettings();
        currentSettings.setRunAsService(!currentSettings.getRunAsService());
        settings.save(currentSettings);
        logs.add("Run-as-service toggled");
    }
    
    public void init() {
        if (musicLibraryPath == null) {
            musicLibraryPath = getDefaultMusicFolder();
            logs.add("Default music folder initialized: " + musicLibraryPath);
        }
    }

    public void selectMusicLibrary() {
        logs.add("Music library set to: " + musicLibraryPath);
    }

    public void scanLibrary() {
        logs.add("Scanning music library: " + musicLibraryPath);

        File folder = getMusicFolder();
        if (!folder.exists() || !folder.isDirectory()) {
            logs.add("Music folder does not exist: " + folder.getAbsolutePath());
            return;
        }

        int totalAdded = scanFolderRecursively(folder);
        logs.add("Scan completed. Total MP3 files added: " + totalAdded);
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
                    Song song = new Song();
                    File baseFolder = getMusicFolder();
                    String relativePath = baseFolder.toURI().relativize(file.toURI()).getPath();
                    song.setPath(relativePath);

                    String fileName = file.getName();
                    if (fileName.toLowerCase().endsWith(".mp3")) {
                        fileName = fileName.substring(0, fileName.length() - 4);
                    }
                    song.setTitle(fileName);

                    // Persist via Panache
                    songService.save(song);

                    logs.add("Added song: " + file.getName() + " (title: " + fileName + ")");
                    addedCount++;
                } catch (Exception e) {
                    logs.add("Failed to add song " + file.getName() + ": " + e.getMessage());
                }
            }
        }

        return addedCount;
    }

    public void clearLogs() {
        logs.clear();
        logs.add("Logs cleared.");
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
                logs.add("Created default Music folder at: " + musicFolder.getAbsolutePath());
            } else {
                logs.add("Failed to create default Music folder, using home directory instead.");
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
        logs.add("Music library reset to default: " + musicLibraryPath);
    }

    public List<String> getLogs() {
        return logs;
    }

    public String getMusicLibraryPath() {
        return musicLibraryPath;
    }

    public void setMusicLibraryPath(String path) {
        this.musicLibraryPath = path;
    }
    
    public Settings getOrCreateSettings(){
        return settings.getOrCreateSettings();
    }
}
