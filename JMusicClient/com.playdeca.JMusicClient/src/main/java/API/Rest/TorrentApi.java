package API.Rest;

import API.ApiResponse;
import Controllers.TorrentController;
import Models.Torrents.Core.Torrent;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response; 
import Models.DTOs.CreateTorrentRequest;
import Models.DTOs.UpdateTorrentRequest;
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
    @Path("/paginated")
    public Response listAllPaginated(@QueryParam("page") @DefaultValue("0") int pageNumber, @QueryParam("size") @DefaultValue("10") int pageSize, @QueryParam("tag") String tag, @QueryParam("sortBy") String sortBy, @QueryParam("order") String order) {
        return Response.ok(ApiResponse.success(controller.listAllTorrentsPaginatedAndFiltered(pageNumber, pageSize, tag, sortBy, order))).build();
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
            Torrent created = controller.createTorrent(request);
            return Response.status(Response.Status.CREATED).entity(ApiResponse.success(created)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error(e.getMessage())).build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") UUID id, UpdateTorrentRequest request) {
        try {
            Torrent updated = controller.updateTorrent(id, request);
            return Response.ok(ApiResponse.success(updated)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error(e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") UUID id) {
        try {
            controller.deleteTorrent(id);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(ApiResponse.error(e.getMessage())).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error(e.getMessage())).build();
        }
    }}
