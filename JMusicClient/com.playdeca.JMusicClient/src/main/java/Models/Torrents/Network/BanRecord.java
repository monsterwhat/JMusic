package Models.Torrents.Network;
 
import Models.Torrents.Core.Torrent;
import Models.Torrents.Identity.PeerReference;
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
@Table(name = "ban_records")
public class BanRecord {

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne
    private Torrent torrent;

    @ManyToOne
    private PeerReference peer;

    private boolean banned;
    private String reason;
    private Instant lastUpdated;

    private String machineId;       // optional machine ID ban
    private String ipAddress;       // optional IP/domain ban
}
