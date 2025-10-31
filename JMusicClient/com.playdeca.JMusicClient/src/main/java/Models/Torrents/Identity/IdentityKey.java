package Models.Torrents.Identity;
 
import Models.Torrents.Core.Torrent;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(name = "identity_keys")
public class IdentityKey {

    @Id @GeneratedValue
    private UUID id;

    @Transient
    private String privateKey;        // stored securely
    private String publicKey;         // Base64 
    private String userId;            // unique user ID
    private Instant createdAt;

    @OneToMany(mappedBy = "creator")
    private List<Torrent> torrentsCreated;
}
