package API.Rest;

import API.ApiResponse;
import Controllers.TorrentController;
import Models.Torrents.Core.Torrent;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@Path("/api/torrents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TorrentApi {

    private final TorrentController controller;

    public TorrentApi(TorrentController controller) {
        this.controller = controller;
    }

    @GET
    public Response listAll() {
        return Response.ok(ApiResponse.success(controller.listAllTorrents())).build();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") UUID id) {
        Torrent torrent = controller.getTorrent(id);
        if (torrent == null) {
            return Response.status(Response.Status.NOT_FOUND).entity(ApiResponse.error("Torrent not found")).build();
        }
        return Response.ok(ApiResponse.success(torrent)).build();
    }

    @POST
    public Response create(CreateTorrentRequest request) {
        try {
            Torrent created = controller.createTorrent(
                    request.name(),
                    request.infoHash(),
                    request.creatorId(),
                    request.tags()
            );
            return Response.status(Response.Status.CREATED).entity(ApiResponse.success(created)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error(e.getMessage())).build();
        }
    }

    // Use a dedicated DTO for requests instead of expecting full Torrent JSON
    public record CreateTorrentRequest(String name, String infoHash, UUID creatorId, List<String> tags) {

    }
}
