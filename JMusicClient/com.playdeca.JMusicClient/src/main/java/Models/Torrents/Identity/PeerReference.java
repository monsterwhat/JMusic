package Models.Torrents.Identity;

import Models.Torrents.Core.Torrent;
import Models.Torrents.Network.SharedPeerList;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(name = "peers")
public class PeerReference {

    @Id
    @GeneratedValue
    private UUID id;

    private String peerId;
    private String ipAddress;         // ip:port or domain
    private String publicKey;         // for verification
    private Instant lastSeen;

    @ManyToMany(mappedBy = "peers")
    private List<Torrent> torrents;

    @OneToMany(mappedBy = "peer", cascade = CascadeType.ALL)
    private List<SharedPeerList> sharedPeers; // peers shared by this peer
}
