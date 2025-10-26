package API.Rest;

import Models.Settings;
import Models.Song;
import Services.SettingsService;
import Services.SongService;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;

@Path("/api/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SettingsApi {

    @Inject
    private SettingsService settingsService;

    @Inject
    private SongService songService;

    // -----------------------------
    // GET CURRENT SETTINGS
    // -----------------------------
    @GET
    @Transactional
    public Response getSettings() {
        Settings settings = getSafeSettings();
        return Response.ok(settings).build();
    }

    // -----------------------------
    // RESET LIBRARY PATH
    // -----------------------------
    @POST
    @Path("/resetLibrary")
    @Transactional
    public Response resetLibrary() {
        Settings settings = getSafeSettings();
        String defaultPath = getDefaultMusicFolder();
        settingsService.setLibraryPath(settings, defaultPath);
        settingsService.addLog(settings, "Music library reset to default: " + defaultPath);
        settingsService.flushLogs(settings); // persist batched logs
        return Response.ok(settings).build();
    }

    // -----------------------------
// SCAN LIBRARY
// -----------------------------
    @POST
    @Path("/scanLibrary")
    public Response scanLibrary() {
        Settings settings = getSafeSettings();
        File folder = new File(settings.getLibraryPath());

        if (!folder.exists() || !folder.isDirectory()) {
            folder.mkdirs();
            settingsService.addLog(settings, "Created missing music folder: " + folder.getAbsolutePath());
            settingsService.flushLogs(settings);
        }

        new Thread(() -> {
            try {
                settingsService.addLog(settings, "Started scanning library...");

                // recursively scan and persist songs with proper transaction
                int totalAdded = scanFolderRecursivelyWithTx(folder, settings);

                settingsService.addLog(settings, "Scan completed. Total MP3 files added: " + totalAdded);
            } catch (Exception e) {
                settingsService.addLog(settings, "Scan failed: " + e.getMessage());
            } finally {
                settingsService.flushLogs(settings);
            }
        }, "LibraryScanThread").start();

        return Response.ok("{\"status\":\"Scan started\"}").build();
    }

    /**
     * Recursively scan a folder and persist each song in a new transaction.
     * This replaces the old scanFolderRecursively call for the background
     * thread.
     */
    private int scanFolderRecursivelyWithTx(File folder, Settings settings) {
        int addedCount = 0;
        File[] files = folder.listFiles();
        if (files == null) {
            return addedCount;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                addedCount += scanFolderRecursivelyWithTx(file, settings);
            } else if (file.isFile() && file.getName().toLowerCase().endsWith(".mp3")) {
                try {
                    String relativePath = new File(settings.getLibraryPath())
                            .toURI().relativize(file.toURI()).getPath();

                    Song song = new Song();
                    song.setPath(relativePath);

                    AudioFile audioFile = AudioFileIO.read(file);
                    Tag tag = audioFile.getTag();

                    if (tag != null) {
                        String titleTag = tag.getFirst(FieldKey.TITLE);
                        song.setTitle(titleTag.isEmpty() ? file.getName().replace(".mp3", "") : titleTag);
                        song.setArtist(tag.getFirst(FieldKey.ARTIST));
                        song.setAlbum(tag.getFirst(FieldKey.ALBUM));
                        song.setGenre(tag.getFirst(FieldKey.GENRE));
                    } else {
                        song.setTitle(file.getName().replace(".mp3", ""));
                    }

                    song.setDurationSeconds(audioFile.getAudioHeader().getTrackLength());

                    // persist each song in a new transaction
                    songService.persistSongInNewTx(song);

                    settingsService.addLog(settings,
                            "Added song: " + relativePath + " (title: " + song.getTitle() + ")");
                    addedCount++;
                } catch (IOException | CannotReadException | InvalidAudioFrameException
                        | ReadOnlyFileException | KeyNotFoundException | TagException e) {
                    settingsService.addLog(settings,
                            "Failed to add song " + file.getName() + ": " + e.getMessage());
                }
            }
        }

        return addedCount;
    }

    // -----------------------------
    // CLEAR LOGS
    // -----------------------------
    @POST
    @Path("/clearLogs")
    @Transactional
    public Response clearLogs() {
        Settings settings = getSafeSettings();
        settingsService.clearLogs(settings);
        return Response.ok(settings).build();
    }

    // -----------------------------
    // GET LOGS
    // -----------------------------
    @GET
    @Path("/logs")
    @Transactional
    public Response getLogs() {
        return Response.ok(settingsService.getLogs(getSafeSettings())).build();
    }

    // -----------------------------
    // CLEAR SONGS DATABASE
    // -----------------------------
    @POST
    @Path("/clearSongs")
    @Transactional
    public Response clearSongs() {
        try {
            List<Song> allSongs = Song.listAll();
            for (Song song : allSongs) {
                song.delete();
            }
            settingsService.addLog(getSafeSettings(), "All songs cleared from database.");
            settingsService.flushLogs(getSafeSettings());
            return Response.ok("{\"status\":\"All songs deleted\"}").build();
        } catch (Exception e) {
            e.printStackTrace();
            settingsService.addLog(getSafeSettings(), "Failed to clear songs DB: " + e.getMessage());
            settingsService.flushLogs(getSafeSettings());
            return Response.status(500).entity("{\"error\":\"Failed to clear songs\"}").build();
        }
    }

    // -----------------------------
    // HELPER METHODS
    // -----------------------------
    private String getDefaultMusicFolder() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            String winProfile = System.getenv("USERPROFILE");
            if (winProfile != null && !winProfile.isBlank()) {
                userHome = winProfile;
            }
        }

        File musicFolder = new File(userHome, "Music");
        if (!musicFolder.exists() && !musicFolder.mkdirs()) {
            settingsService.addLog(getSafeSettings(), "Failed to create default Music folder, using home directory instead.");
            settingsService.flushLogs(getSafeSettings());
            return userHome;
        }

        return musicFolder.getAbsolutePath();
    }
  
    private Settings getSafeSettings() {
        List<Settings> all = Settings.listAll();
        Settings settings;
        if (all.isEmpty()) {
            settings = new Settings();
            settings.setLibraryPath(getDefaultMusicFolder());
            settings.setLogs(new ArrayList<>());
            settings.persist();
        } else {
            settings = all.get(0);
            if (settings.getLibraryPath() == null || settings.getLibraryPath().isBlank()) {
                settings.setLibraryPath(getDefaultMusicFolder());
            }
            if (settings.getLogs() == null) {
                settings.setLogs(new ArrayList<>());
            }
        }
        return settings;
    }
}
