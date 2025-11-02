package Services;

import Models.PlaybackState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.ArrayList;

@ApplicationScoped
public class PlaybackStateService {

    private static final Long SINGLETON_ID = 1L;

    @Inject
    Provider<EntityManager> emProvider;

    @Transactional
    public synchronized PlaybackState getState() {
        EntityManager em = emProvider.get();
        PlaybackState state = em.find(PlaybackState.class, SINGLETON_ID);
        if (state == null) {
            state = new PlaybackState();
            state.setId(SINGLETON_ID);
            state.setVolume(0.8f);
            state.setCue(new ArrayList<>());
            state.setLastSongs(new ArrayList<>());
            state.setCueIndex(-1);
            em.persist(state);
        }
        return state;
    }

    @Transactional
    public synchronized void updateState(PlaybackState newState) {
        if (newState == null) {
            return;
        }
        newState.setId(SINGLETON_ID);
        EntityManager em = emProvider.get();
        em.merge(newState);
        em.flush();
    }

    @Transactional
    public synchronized void resetState() {
        PlaybackState state = new PlaybackState();
        state.setId(SINGLETON_ID);
        EntityManager em = emProvider.get();
        em.merge(state);
        em.flush();
    }
}
