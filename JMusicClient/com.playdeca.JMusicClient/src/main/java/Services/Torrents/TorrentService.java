 
package Services.Torrents;

import Models.Torrents.Core.Torrent;
import Models.Torrents.Identity.IdentityKey;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

import java.util.UUID;

@ApplicationScoped 
public class TorrentService {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public Torrent createTorrent(String name, String infoHash, IdentityKey creator, List<String> tags) {
        Torrent torrent = new Torrent();
        torrent.setName(name);
        torrent.setInfoHash(infoHash);
        torrent.setCreator(creator);
        torrent.setTags(tags);
        torrent.setCreatedAt(Instant.now());
        torrent.setActive(true);
        torrent.setVerified(false);
        em.persist(torrent);
        return torrent;
    }

    @Transactional
    public Torrent findById(UUID id) {
        return em.find(Torrent.class, id);
    }

    @Transactional
    public Torrent updateTorrent(Torrent torrent) {
        return em.merge(torrent);
    }

    @Transactional
    public List<Torrent> findAll() {
        return em.createQuery("SELECT t FROM Torrent t", Torrent.class).getResultList();
    }

    @Transactional
    public List<Torrent> findAll(int pageNumber, int pageSize, String sortBy, String order) {
        String orderByClause = "ORDER BY t." + sortBy + " " + order;
        return em.createQuery("SELECT t FROM Torrent t " + orderByClause, Torrent.class)
                .setFirstResult(pageNumber * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }

    @Transactional
    public long countAll() {
        return em.createQuery("SELECT COUNT(t) FROM Torrent t", Long.class).getSingleResult();
    }

    @Transactional
    public List<Torrent> findAllByTag(String tag, int pageNumber, int pageSize, String sortBy, String order) {
        String orderByClause = "ORDER BY t." + sortBy + " " + order;
        return em.createQuery("SELECT t FROM Torrent t JOIN t.tags tag WHERE tag = :tag " + orderByClause, Torrent.class)
                .setParameter("tag", tag)
                .setFirstResult(pageNumber * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }

    @Transactional
    public long countAllByTag(String tag) {
        return em.createQuery("SELECT COUNT(t) FROM Torrent t JOIN t.tags tag WHERE tag = :tag", Long.class)
                .setParameter("tag", tag)
                .getSingleResult();
    }
  
    @Transactional
    public void deleteTorrent(UUID id) {
        Torrent torrent = em.find(Torrent.class, id);
        if (torrent != null) {
            em.remove(torrent);
        }
    }
}
