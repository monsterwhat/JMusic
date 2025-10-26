package API.Rest;

import Controllers.PlaybackController;
import Models.Playlist;
import Models.Song;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

@Path("/api/music/ui")
@Produces(MediaType.TEXT_HTML)
public class MusicUiApi {

    @Inject
    private PlaybackController playbackController;

    // -------------------------
    // Playlists fragment
    // -------------------------
    @GET
    @Path("/playlists-fragment")
    @Produces(MediaType.TEXT_HTML)
    public String playlistsFragment() {
        List<Playlist> playlists = playbackController.getPlaylists();
        if (playlists == null) {
            playlists = new ArrayList<>();
        }

        StringBuilder html = new StringBuilder("<ul>");

        // ---- Add "All Songs" button at the top ----
        html.append("<li><button hx-post='/api/music/playlists/0/select' ")
                .append("hx-target='#songTable tbody' hx-swap='innerHTML'>")
                .append("All Songs")
                .append("</button></li>");

        // ---- Render existing playlists ----
        for (Playlist p : playlists) {
            if (p == null) {
                continue;
            }
            String name = p.getName() != null ? p.getName() : "Unnamed Playlist";
            html.append("<li><button hx-post='/api/music/playlists/")
                    .append(p.id)
                    .append("/select' ")
                    .append("hx-target='#songTable tbody' hx-swap='innerHTML'>")
                    .append(name)
                    .append("</button></li>");
        }

        html.append("</ul>");
        return html.toString();
    }

    @Produces(MediaType.TEXT_HTML)
    public String playlistSongsFragment(Playlist playlist) {
        if (playlist == null || playlist.getSongs() == null) {
            return "<tbody></tbody>";
        }

        List<Song> songs = playlist.getSongs();
        Song currentSong = playbackController.getCurrentSong();
        boolean isPlaying = playbackController.getState().isPlaying();

        StringBuilder html = new StringBuilder("<tbody>");

        for (Song s : songs) {
            if (s == null) {
                continue;
            }

            String title = s.getTitle() != null ? s.getTitle() : "Unknown Title";
            String artist = s.getArtist() != null ? s.getArtist() : "Unknown Artist";
            int duration = s.getDurationSeconds() >= 0 ? s.getDurationSeconds() : 0;

            boolean isCurrent = currentSong != null && s.id.equals(currentSong.id);
            boolean showPause = isCurrent && isPlaying;

            String buttonClass = "button is-success is-rounded is-small" + (showPause ? " is-loading" : "");

            html.append("<tr class='").append(isCurrent ? "has-background-dark" : "").append("'>")
                    .append("<td>").append(title).append("</td>")
                    .append("<td>").append(artist).append("</td>")
                    .append("<td>").append(formatTime(duration)).append("</td>")
                    .append("<td>")
                    .append("<button class='").append(buttonClass).append("' ")
                    .append("hx-post='/api/music/select/").append(s.id).append("' ")
                    .append("hx-trigger='click' ")
                    .append("hx-swap='outerHTML' ")
                    .append("hx-on:afterRequest=\"htmx.ajax('GET','/api/music/ui/songs-fragment',{target:'#songTable tbody'})\">")
                    .append(showPause ? "Pause" : "Play")
                    .append("</button>")
                    .append("</td>")
                    .append("</tr>");
        }

        html.append("</tbody>");
        return html.toString();
    }

    // -------------------------
    // Songs fragment
    // -------------------------
    @GET
    @Path("/songs-fragment")
    @Produces(MediaType.TEXT_HTML)
    public String songsFragment() {
        List<Song> songs = playbackController.getSongs();
        if (songs == null) {
            songs = new ArrayList<>();
        }

        Song currentSong = playbackController.getCurrentSong();
        boolean isPlaying = playbackController.getState().isPlaying(); // get actual playback state

        StringBuilder html = new StringBuilder("<tbody>");

        for (Song s : songs) {
            if (s == null) {
                continue;
            }

            String title = s.getTitle() != null ? s.getTitle() : "Unknown Title";
            String artist = s.getArtist() != null ? s.getArtist() : "Unknown Artist";
            int duration = s.getDurationSeconds() >= 0 ? s.getDurationSeconds() : 0;

            boolean isCurrent = currentSong != null && s.id.equals(currentSong.id);
            boolean showPause = isCurrent && isPlaying;

            String buttonClass = "button is-success is-rounded is-small" + (showPause ? " is-loading" : "");

            html.append("<tr class='").append(isCurrent ? "has-background-dark" : "").append("'>")
                    .append("<td>").append(title).append("</td>")
                    .append("<td>").append(artist).append("</td>")
                    .append("<td>").append(formatTime(duration)).append("</td>")
                    .append("<td>")
                    .append("<button class='").append(buttonClass).append("' ")
                    .append("hx-post='/api/music/select/").append(s.id).append("' ")
                    .append("hx-trigger='click' ")
                    .append("hx-swap='outerHTML' ")
                    .append("hx-on:afterRequest=\"htmx.ajax('GET','/api/music/ui/songs-fragment',{target:'#songTable tbody'})\">")
                    .append(showPause ? "Pause" : "Play")
                    .append("</button>")
                    .append("</td>")
                    .append("</tr>");

        }

        html.append("</tbody>");
        return html.toString();
    }

    private String formatTime(double seconds) {
        int m = (int) (seconds / 60);
        int s = (int) (seconds % 60);
        return String.format("%d:%02d", m, s);
    }
}
