package Models.Torrents.Network;

import Models.Torrents.Identity.PeerReference;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(name = "shared_peer_lists")
public class SharedPeerList {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    private PeerReference peer;      // the peer sharing their list

    @ElementCollection
    private List<String> peerIds;    // list of peer IDs shared

    private Instant lastUpdated;
}
