package Services.Torrents;

import Models.Torrents.Core.Torrent;
import Models.Torrents.Identity.PeerReference;
import Models.Torrents.Network.BanRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class BanService {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public BanRecord banPeer(PeerReference peer, String reason) {
        BanRecord banRecord = new BanRecord();
        banRecord.setPeer(peer);
        banRecord.setBanned(true);
        banRecord.setReason(reason);
        banRecord.setLastUpdated(Instant.now());
        em.persist(banRecord);
        return banRecord;
    }

    @Transactional
    public void unbanPeer(PeerReference peer) {
        Optional<BanRecord> existingBan = findActiveBan(peer);
        existingBan.ifPresent(record -> {
            record.setBanned(false);
            record.setLastUpdated(Instant.now());
            em.merge(record);
        });
    }

    @Transactional
    public boolean isBanned(PeerReference peer) {
        return findActiveBan(peer).isPresent();
    }

    @Transactional
    public Optional<BanRecord> findActiveBan(PeerReference peer) {
        return em.createQuery("SELECT b FROM BanRecord b WHERE b.peer = :peer AND b.banned = true", BanRecord.class)
                .setParameter("peer", peer)
                .getResultStream()
                .findFirst();
    }

    // You could also add methods to ban by IP address or machine ID if needed
}
