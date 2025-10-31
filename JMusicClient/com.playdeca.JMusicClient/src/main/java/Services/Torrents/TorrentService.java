 
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
    public List<Torrent> findAll() {
        return em.createQuery("SELECT t FROM Torrent t", Torrent.class).getResultList();
    }
}
