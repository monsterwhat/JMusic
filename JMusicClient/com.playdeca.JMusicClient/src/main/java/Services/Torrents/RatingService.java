package Services.Torrents;

import Models.Torrents.Core.Torrent;
import Models.Torrents.Identity.PeerReference;
import Models.Torrents.Network.TorrentRating;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RatingService {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public TorrentRating addOrUpdateRating(Torrent torrent, PeerReference peer, boolean liked, byte[] signature) {
        Optional<TorrentRating> existing = findRating(torrent, peer);
        TorrentRating rating = existing.orElseGet(TorrentRating::new);

        rating.setTorrent(torrent);
        rating.setPeer(peer);
        rating.setLiked(liked);
        rating.setSignature(signature);
        rating.setCreatedAt(java.time.Instant.now());

        if (existing.isEmpty()) {
            em.persist(rating);
        } else {
            em.merge(rating);
        }

        return rating;
    }

    @Transactional
    public Optional<TorrentRating> findRating(Torrent torrent, PeerReference peer) {
        return em.createQuery("SELECT r FROM TorrentRating r WHERE r.torrent = :torrent AND r.peer = :peer", TorrentRating.class)
                .setParameter("torrent", torrent)
                .setParameter("peer", peer)
                .getResultStream()
                .findFirst();
    }

    @Transactional
    public List<TorrentRating> getRatingsForTorrent(UUID torrentId) {
        return em.createQuery("SELECT r FROM TorrentRating r WHERE r.torrent.id = :id", TorrentRating.class)
                .setParameter("id", torrentId)
                .getResultList();
    }
}
