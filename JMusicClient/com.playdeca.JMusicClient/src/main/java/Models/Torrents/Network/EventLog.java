package Models.Torrents.Network;

import Models.Torrents.Core.Torrent;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(name = "event_logs")
public class EventLog {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    private Torrent torrent;

    private String eventType;        // "added", "downloaded", "peerConnected", etc.
    private String description;
    private Instant timestamp;
}
