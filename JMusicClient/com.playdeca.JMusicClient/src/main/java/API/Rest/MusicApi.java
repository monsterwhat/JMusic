package API.Rest;

import Controllers.PlaybackController;
import Models.Playlist;
import Models.Settings;
import Models.Song;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Transactional
@Path("/api/music")
@Consumes(MediaType.APPLICATION_JSON)
public class MusicApi {

    @Inject
    private PlaybackController playbackController;

    @Inject
    private MusicUiApi ui;

    private static final Logger LOGGER = Logger.getLogger(MusicApi.class.getName());

    @POST
    @Path("/toggle-play")
    @Consumes("*/*")
    public Response togglePlay() {
        try {
            playbackController.applyMessage("{\"action\":\"toggle-play\"}");
            return Response.ok().build();
        } catch (IOException e) {
            return Response.serverError().build();
        }

    }

    // -------------------------
    // Streaming endpoint by song ID
    // -------------------------
    @GET
    @Path("/stream/{id}")
    @Produces({"audio/mpeg", "application/octet-stream"})
    public Response streamMusicById(@PathParam("id") Long id, @HeaderParam("Range") String rangeHeader) {
        try {
            Song song = playbackController.findSong(id);
            if (song == null) {
                LOGGER.warning("Stream requested for missing song ID " + id);
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            String songPath = song.getPath();
            File file = new File(getMusicFolder(), songPath);
            LOGGER.info("Streaming song: " + song.getTitle() + " from path: " + file.getAbsolutePath());

            if (!file.exists() || file.isDirectory()) {
                LOGGER.warning("File not found or is directory: " + file.getAbsolutePath());
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            long len = file.length(), start = 0, end = len - 1;
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String[] parts = rangeHeader.replace("bytes=", "").split("-");
                try {
                    start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        end = Long.parseLong(parts[1]);
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warning("Invalid Range header: " + rangeHeader);
                }
            }

            if (start >= len) {
                LOGGER.warning("Requested range start beyond file length: start=" + start + ", len=" + len);
                return Response.status(Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header("Content-Range", "bytes */" + len)
                        .build();
            }

            long contentLength = end - start + 1;
            final long trueStart = start;

            StreamingOutput stream = out -> {
                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    raf.seek(trueStart);
                    byte[] buf = new byte[16 * 1024];
                    long left = contentLength;
                    int read;
                    while ((read = raf.read(buf)) != -1 && left > 0) {
                        int toWrite = (int) Math.min(read, left);
                        try {
                            out.write(buf, 0, toWrite);
                        } catch (IOException e) {
                            // Client disconnected: just stop streaming, log at fine/debug level
                            LOGGER.fine(() -> "Client disconnected while streaming " + file.getName() + ": " + e.getMessage());
                            break;
                        }
                        left -= toWrite;
                    }
                    // No flush needed: container handles it
                } catch (IOException e) {
                    // Other I/O errors
                    LOGGER.log(Level.WARNING, "Error streaming file " + file.getName(), e);
                }
            };

            LOGGER.info("Streaming response prepared: bytes=" + start + "-" + end + "/" + len);
            return Response.status(rangeHeader != null ? 206 : 200)
                    .header("Accept-Ranges", "bytes")
                    .header("Content-Range", "bytes " + start + "-" + end + "/" + len)
                    .header("Cache-Control", "public,max-age=3600")
                    .entity(stream)
                    .build();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error streaming music file", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    // -------------------------
    // Playlist endpoints (JSON)
    // -------------------------
    @GET
    @Consumes("*/*")
    @Path("/playlists")
    public Response listPlaylists() {
        List<Playlist> playlists = playbackController.getPlaylists();
        if (playlists == null) {
            playlists = new ArrayList<>();
        }
        return Response.ok(playlists).build();
    }

    @GET
    @Path("/playlists/{id}")
    public Response getPlaylist(@PathParam("id") Long id) {
        return Response.ok(requirePlaylist(id)).build();
    }

    @POST
    @Path("/playlists")
    public Response createPlaylist(Playlist playlist) {
        if (playlist == null || playlist.getName() == null || playlist.getName().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"Name required\"}").build();
        }
        if (playlist.getSongs() == null) {
            playlist.setSongs(new ArrayList<>());
        }
        playbackController.createPlaylist(playlist);
        return Response.ok(playlist).build();
    }

    @POST
    @Path("/playlists/{id}/select")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.TEXT_HTML)
    public String selectPlaylist(@PathParam("id") Long id) {
        if (id == null || id == 0) {
            // Special case: All Songs
            playbackController.selectPlaylistForBrowsing(null); // deselect any playlist
            return ui.songsFragment(); // return all songs
        }

        Playlist playlist = playbackController.findPlaylist(id);
        if (playlist != null) {
            playbackController.selectPlaylistForBrowsing(playlist);
            return ui.playlistSongsFragment(playlist);
        }

        return "<tbody></tbody>";
    }

    @PUT
    @Path("/playlists/{id}")
    public Response updatePlaylist(@PathParam("id") Long id, Playlist playlist) {
        Playlist e = requirePlaylist(id);
        e.setName(playlist.getName());
        e.setDescription(playlist.getDescription());
        e.setSongs(playlist.getSongs() != null ? playlist.getSongs() : new ArrayList<>());
        playbackController.updatePlaylist(e);
        return Response.ok(e).build();
    }

    @DELETE
    @Path("/playlists/{id}")
    public Response deletePlaylist(@PathParam("id") Long id) {
        Playlist p = requirePlaylist(id);
        playbackController.deletePlaylist(p);
        return Response.ok("{\"status\":\"deleted\"}").build();
    }

    @POST
    @Path("/playlists/{playlistId}/songs/{songId}")
    public Response addSongToPlaylist(@PathParam("playlistId") Long pid, @PathParam("songId") Long sid) {
        Playlist p = requirePlaylist(pid);
        Song s = requireSong(sid);
        if (p.getSongs().stream().noneMatch(song -> song.id.equals(sid))) {
            p.getSongs().add(s);
            playbackController.updatePlaylist(p);
        }
        return Response.ok(p).build();
    }

    @DELETE
    @Path("/playlists/{playlistId}/songs/{songId}")
    public Response removeSongFromPlaylist(@PathParam("playlistId") Long pid, @PathParam("songId") Long sid) {
        Playlist p = requirePlaylist(pid);
        p.getSongs().removeIf(song -> song.id.equals(sid));
        playbackController.updatePlaylist(p);
        return Response.ok(p).build();
    }

    // -------------------------
    // Playback endpoints
    // -------------------------
    @GET
    @Path("/current")
    public Response getCurrentSong() {
        var current = playbackController.getCurrentSong();
        return Response.ok(current).build();
    }

    @POST
    @Path("/play")
    @Consumes("*/*")
    public Response play() {
        try {
            playbackController.applyMessage("{\"action\":\"play\"}");
            return Response.ok().build();
        } catch (Exception e) {
            return Response.serverError().build();
        }

    }

    @POST
    @Path("/pause")
    @Consumes("*/*")
    public Response pause() {
        try {
            playbackController.applyMessage("{\"action\":\"pause\"}");
            return Response.ok().build();
        } catch (Exception e) {
            return Response.serverError().build();
        }

    } 

    @POST
    @Path("/next")
    @Consumes("*/*")
    public Response next() {
        try {
            playbackController.applyMessage("{\"action\":\"next\"}");
            return Response.ok().build();
        } catch (Exception e) {
            return Response.serverError().build();
        }

    }

    @POST
    @Path("/previous")
    @Consumes("*/*")
    public Response previous() {
        try {
            playbackController.applyMessage("{\"action\":\"previous\"}");
            return Response.ok().build();
        } catch (Exception e) {
            return Response.serverError().build();
        }

    }

    @POST
    @Path("/select/{id}")
    @Consumes("*/*")
    public Response selectSong(@PathParam("id") Long id) {
        try {
            playbackController.selectSong(id);
            return Response.ok().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    @POST
    @Path("/shuffle/{enabled}")
    @Consumes({"application/json", "text/plain", "*/*"})
    public Response toggleShuffle(@PathParam("enabled") boolean enabled) {
        playbackController.toggleShuffle();
        return Response.ok().build();
    }

    @POST
    @Path("/repeat/{enabled}")
    @Consumes({"application/json", "text/plain", "*/*"})
    public Response toggleRepeat(@PathParam("enabled") boolean enabled) {
        playbackController.toggleRepeat();
        return Response.ok().build();
    }

    @POST
    @Path("/volume/{level}")
    public Response setVolume(@PathParam("level") float level) {
        playbackController.changeVolume(level);
        return Response.ok().build();
    }

    @POST
    @Path("/position/{seconds}")
    public Response setPosition(@PathParam("seconds") double seconds) {
        playbackController.setSeconds(seconds);
        return Response.ok().build();
    }

    private Playlist requirePlaylist(Long id) {
        Playlist p = playbackController.findPlaylist(id);
        if (p == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        if (p.getSongs() == null) {
            p.setSongs(new ArrayList<>());
        }
        return p;
    }

    private Song requireSong(Long id) {
        Song s = playbackController.findSong(id);
        if (s == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return s;
    }

    private File getMusicFolder() {
        Settings settings = playbackController.getSettings();
        if (settings == null || settings.getLibraryPath() == null) {
            return new File("./"); // fallback
        }
        return new File(settings.getLibraryPath());
    }

}
