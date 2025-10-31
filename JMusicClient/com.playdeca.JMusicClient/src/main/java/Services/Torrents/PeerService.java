package Services.Torrents;

import Models.Torrents.Identity.PeerReference;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PeerService {

    @PersistenceContext
    private EntityManager em;

    /**
     * Registers a new peer or updates an existing one by peerId.
     */
    @Transactional
    public PeerReference registerOrUpdatePeer(String peerId, String publicKey, String ipAddress) {
        PeerReference peer = findByPeerId(peerId).orElseGet(PeerReference::new);
        boolean isNew = peer.getId() == null;

        peer.setPeerId(peerId);
        peer.setPublicKey(publicKey);
        peer.setIpAddress(ipAddress);
        peer.setLastSeen(java.time.Instant.now());

        if (isNew) {
            em.persist(peer);
        } else {
            em.merge(peer);
        }

        return peer;
    }

    @Transactional
    public PeerReference findById(UUID id) {
        return em.find(PeerReference.class, id);
    }

    @Transactional
    public Optional<PeerReference> findByPeerId(String peerId) {
        return em.createQuery("SELECT p FROM PeerReference p WHERE p.peerId = :id", PeerReference.class)
                .setParameter("id", peerId)
                .getResultStream()
                .findFirst();
    }

    @Transactional
    public void updateLastSeen(PeerReference peer) {
        if (peer == null || peer.getId() == null) {
            return;
        }

        PeerReference found = em.find(PeerReference.class, peer.getId());
        if (found != null) {
            found.setLastSeen(java.time.Instant.now());
        }
    }
}
