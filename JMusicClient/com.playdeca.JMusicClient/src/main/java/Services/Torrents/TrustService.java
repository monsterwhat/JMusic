package Services.Torrents;

import Models.Torrents.Core.Torrent;
import Models.Torrents.Identity.PeerReference;
import Models.Torrents.Identity.TrustRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class TrustService {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void updateTrust(PeerReference peer, boolean trusted, String reason) {
        TrustRecord record = new TrustRecord();
        record.setPeer(peer);
        record.setTrusted(trusted);
        record.setReason(reason);
        record.setTimestamp(java.time.Instant.now());
        em.persist(record);
    }

    @Transactional
    public void initializeTrustForCreator(Torrent torrent, PeerReference creator) {
        if (creator == null) {
            return;
        }

        TrustRecord record = new TrustRecord();
        record.setPeer(creator);
        record.setTorrent(torrent);
        record.setTrusted(true);
        record.setReason("Creator of torrent");
        record.setTimestamp(java.time.Instant.now());
        em.persist(record);
    }

    @Transactional
    public double getTrustScore(UUID peerId) {
        long total = em.createQuery("SELECT COUNT(t) FROM TrustRecord t WHERE t.peer.id = :id", Long.class)
                .setParameter("id", peerId)
                .getSingleResult();
        long trusted = em.createQuery("SELECT COUNT(t) FROM TrustRecord t WHERE t.peer.id = :id AND t.trusted = true", Long.class)
                .setParameter("id", peerId)
                .getSingleResult();
        return total == 0 ? 0.0 : (double) trusted / total;
    }
}
