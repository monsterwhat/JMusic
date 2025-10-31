package Models.Torrents.Core;

import Models.Torrents.Identity.IdentityKey;
import Models.Torrents.Identity.PeerReference;
import Models.Torrents.Identity.TrustRecord;
import Models.Torrents.Network.BanRecord;
import Models.Torrents.Network.EventLog;
import Models.Torrents.Network.TorrentRating;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
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
@Table(name = "torrents")
public class Torrent {

    @Id
    @GeneratedValue
    private UUID id;

    // Base BitTorrent fields
    private String name;
    @Column(unique = true, nullable = false)
    private String infoHash;         // SHA-1 or SHA-256 hash
    private long size;
    private int pieceCount;
    private Instant createdAt;
    private Instant lastSeen;
    private boolean verified;
    private boolean active;

    // Overlay metadata
    @ManyToOne
    private IdentityKey creator;     // creator's identity key

    @ElementCollection
    private List<String> tags;

    @OneToMany(mappedBy = "torrent", cascade = CascadeType.ALL)
    private List<TorrentFile> files;

    @ManyToMany
    @JoinTable(
            name = "torrent_peers",
            joinColumns = @JoinColumn(name = "torrent_id"),
            inverseJoinColumns = @JoinColumn(name = "peer_id")
    )
    private List<PeerReference> peers;

    @OneToMany(mappedBy = "torrent", cascade = CascadeType.ALL)
    private List<TrustRecord> trustRecords;

    @OneToMany(mappedBy = "torrent", cascade = CascadeType.ALL)
    private List<TorrentRating> ratings;

    @OneToMany(mappedBy = "torrent", cascade = CascadeType.ALL)
    private List<BanRecord> banRecords;

    @OneToMany(mappedBy = "torrent", cascade = CascadeType.ALL)
    private List<EventLog> events;
}
