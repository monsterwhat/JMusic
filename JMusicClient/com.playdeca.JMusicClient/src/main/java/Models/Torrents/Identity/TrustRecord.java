package Models.Torrents.Identity;
 
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
@Table(name = "trust_records")
public class TrustRecord {

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne
    private Torrent torrent;

    @ManyToOne
    private PeerReference peer;

    private boolean trusted;
    private String reason;
    private Instant timestamp;
}
