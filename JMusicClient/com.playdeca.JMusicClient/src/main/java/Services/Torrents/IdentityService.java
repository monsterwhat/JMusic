package Services.Torrents;

import Models.Torrents.Identity.IdentityKey;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class IdentityService {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public IdentityKey findById(UUID id) {
        return em.find(IdentityKey.class, id);
    }

    @Transactional
    public Optional<IdentityKey> findByUserId(String userId) {
        return em.createQuery("SELECT i FROM IdentityKey i WHERE i.userId = :uid", IdentityKey.class)
                .setParameter("uid", userId)
                .getResultStream()
                .findFirst();
    }

    @Transactional
    public IdentityKey register(String userId, String publicKey, String privateKey) {
        IdentityKey identity = new IdentityKey();
        identity.setUserId(userId);
        identity.setPublicKey(publicKey);
        identity.setPrivateKey(privateKey); // transient, not persisted
        identity.setCreatedAt(java.time.Instant.now());
        em.persist(identity);
        return identity;
    }

    @Transactional
    public void delete(UUID id) {
        IdentityKey identity = em.find(IdentityKey.class, id);
        if (identity != null) {
            em.remove(identity);
        }
    }
}
