package Services;

import Models.Playlist;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class PlaylistService {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void save(Playlist playlist) {
        if (playlist.id == null || em.find(Playlist.class, playlist.id) == null) {
            em.persist(playlist);
        } else {
            em.merge(playlist);
        }
    }

    @Transactional
    public void delete(Playlist playlist) {
        if (playlist != null) {
            Playlist managed = em.contains(playlist) ? playlist : em.merge(playlist);
            em.remove(managed);
        }
    }

    public Playlist find(Long id) {
        return em.find(Playlist.class, id);
    }

    public List<Playlist> findAll() {
        return em.createQuery("SELECT p FROM Playlist p", Playlist.class)
                 .getResultList();
    }
}
