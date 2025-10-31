package Controllers;

import Models.Torrents.Core.Torrent;
import Models.Torrents.Identity.IdentityKey;
import Models.Torrents.Identity.PeerReference;
import Models.Torrents.Network.TorrentRating;
import Services.Torrents.IdentityService;
import Services.Torrents.TorrentService;
import Services.Torrents.PeerService;
import Services.Torrents.RatingService;
import Services.Torrents.TrustService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@ApplicationScoped
public class TorrentController {

    @Inject
    TorrentService torrentService;
    @Inject
    PeerService peerService;
    @Inject
    RatingService ratingService;
    @Inject
    TrustService trustService;
    @Inject
    IdentityService identityService;

    private final Map<UUID, Torrent> activeCache = new ConcurrentHashMap<>();

    // Fetch a torrent (cached if available)
    public Torrent getTorrent(UUID id) {
        return activeCache.computeIfAbsent(id, torrentService::findById);
    }

    public List<Torrent> listAllTorrents() {
        return torrentService.findAll();
    }

    // Called when a peer connects via WebSocket
    public void onPeerConnected(PeerReference peer) {
        if (peer == null) {
            return;
        }
        peerService.updateLastSeen(peer);
        broadcastPeerEvent(peer, "peer_connected");
    }

    // Called when a peer rates a torrent
    public void onPeerRatedTorrent(UUID torrentId, UUID peerId, boolean liked, byte[] signature) {
        Torrent torrent = getTorrent(torrentId);
        PeerReference peer = peerService.findById(peerId);
        if (torrent == null || peer == null) {
            return;
        }

        TorrentRating rating = ratingService.addOrUpdateRating(torrent, peer, liked, signature);
        broadcastRatingUpdate(torrent, rating);
    }

    // Create a new torrent
    public Torrent createTorrent(String name, String infoHash, UUID creatorId, List<String> tags) {
        IdentityKey creator = identityService.findById(creatorId);
        if (creator == null) {
            throw new IllegalArgumentException("Creator not found");
        }

        Torrent torrent = torrentService.createTorrent(name, infoHash, creator, tags);
        activeCache.put(torrent.getId(), torrent);

        // Initialize trust only if a PeerReference exists for this identity
        peerService.findByPeerId(creator.getUserId())
                .ifPresent(peer -> trustService.initializeTrustForCreator(torrent, peer));

        broadcastNewTorrent(torrent);
        return torrent;
    }

    // -----------------------
    // Broadcast helpers
    // -----------------------
    private void broadcastNewTorrent(Torrent torrent) {
        if (torrent != null) {
            System.out.println("[Socket] Broadcasting new torrent: " + torrent.getName());
        }
    }

    private void broadcastPeerEvent(PeerReference peer, String eventType) {
        if (peer != null) {
            System.out.println("[Socket] Peer event: " + eventType + " from " + peer.getPeerId());
        }
    }

    private void broadcastRatingUpdate(Torrent torrent, TorrentRating rating) {
        if (torrent != null && rating != null) {
            System.out.println("[Socket] Torrent rating updated: " + torrent.getName() + " liked=" + rating.isLiked());
        }
    }
}
