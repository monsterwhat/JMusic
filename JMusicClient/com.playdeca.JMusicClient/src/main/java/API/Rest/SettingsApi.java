package API.Rest;

import API.ApiResponse;
import Controllers.SettingsController;
import Models.Settings;
import Models.Song;
import Services.PlaybackHistoryService;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SettingsApi {

    @Inject
    private SettingsController settingsController;

    @Inject
    private PlaybackHistoryService playbackHistoryService;

    // -----------------------------
    // GET CURRENT SETTINGS
    // -----------------------------
    @GET
    @Transactional
    public Response getSettings() {
        Settings settings = settingsController.getOrCreateSettings();
        return Response.ok(ApiResponse.success(settings)).build();
    }

    // -----------------------------
    // TOGGLE RUN AS SERVICE
    // -----------------------------
    @POST
    @Path("/toggle-run-as-service")
    @Transactional
    @Consumes(MediaType.WILDCARD)
    public Response toggleRunAsService() {
        settingsController.toggleAsService();
        return Response.ok(ApiResponse.success(settingsController.getOrCreateSettings())).build();
    }

    // -----------------------------
    // RESET LIBRARY PATH
    // -----------------------------
    @POST
    @Path("/resetLibrary")
    @Transactional
    public Response resetLibrary() {
        settingsController.resetMusicLibrary();
        return Response.ok(ApiResponse.success(settingsController.getOrCreateSettings())).build();
    }

    // -----------------------------
    // SCAN LIBRARY
    // -----------------------------
    @POST
    @Path("/scanLibrary")
    public Response scanLibrary() {
        new Thread(() -> {
            settingsController.scanLibrary();
        }, "LibraryScanThread").start();

        return Response.ok(ApiResponse.success("Scan started")).build();
    }

    // -----------------------------
    // CLEAR LOGS
    // -----------------------------
    @POST
    @Path("/clearLogs")
    @Transactional
    public Response clearLogs() {
        settingsController.clearLogs();
        return Response.ok(ApiResponse.success(settingsController.getOrCreateSettings())).build();
    }

    // -----------------------------
    // GET LOGS
    // -----------------------------
    @GET
    @Path("/logs")
    @Transactional
    public Response getLogs() {
        return Response.ok(ApiResponse.success(settingsController.getLogs())).build();
    }

    // -----------------------------
    // CLEAR PLAYBACK HISTORY
    // -----------------------------
    @POST
    @Path("/clearPlaybackHistory")
    @Transactional
    public Response clearPlaybackHistory() {
        playbackHistoryService.clearHistory();
        return Response.ok(ApiResponse.success("Playback history cleared")).build();
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
            settingsController.addLog("All songs cleared from database.");
            return Response.ok(ApiResponse.success("All songs deleted")).build();
        } catch (Exception e) {
            e.printStackTrace();
            settingsController.addLog("Failed to clear songs DB: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ApiResponse.error("Failed to clear songs")).build();
        }
    }

    // -----------------------------
    // RELOAD METADATA
    // -----------------------------
    @POST
    @Path("/reloadMetadata")
    public Response reloadMetadata() {
        new Thread(() -> {
            settingsController.reloadAllSongsMetadata();
        }, "MetadataReloadThread").start();

        return Response.ok(ApiResponse.success("Metadata reload started")).build();
    }

    // -----------------------------
    // DELETE DUPLICATES
    // -----------------------------
    @POST
    @Path("/deleteDuplicates")
    public Response deleteDuplicates() {
        new Thread(() -> {
            settingsController.deleteDuplicateSongs();
        }, "DeleteDuplicatesThread").start();

        return Response.ok(ApiResponse.success("Duplicate deletion started")).build();
    }

    // -----------------------------
    // TOGGLE TORRENT BROWSING
    // -----------------------------
    @POST
    @Path("/toggleTorrentBrowsing")
    @Transactional
    public Response toggleTorrentBrowsing(@QueryParam("enabled") boolean enabled) {
        settingsController.toggleTorrentBrowsing(enabled);
        return Response.ok(ApiResponse.success(settingsController.getOrCreateSettings())).build();
    }

    // -----------------------------
    // TOGGLE TORRENT PEER DISCOVERY
    // -----------------------------
    @POST
    @Path("/toggleTorrentPeerDiscovery")
    @Transactional
    public Response toggleTorrentPeerDiscovery(@QueryParam("enabled") boolean enabled) {
        settingsController.toggleTorrentPeerDiscovery(enabled);
        return Response.ok(ApiResponse.success(settingsController.getOrCreateSettings())).build();
    }

    // -----------------------------
    // TOGGLE TORRENT DISCOVERY
    // -----------------------------
    @POST
    @Path("/toggleTorrentDiscovery")
    @Transactional
    public Response toggleTorrentDiscovery(@QueryParam("enabled") boolean enabled) {
        settingsController.toggleTorrentDiscovery(enabled);
        return Response.ok(ApiResponse.success(settingsController.getOrCreateSettings())).build();
    }
}
