package Models.Torrents.Network;

import Models.Torrents.Core.Torrent;
import Models.Torrents.Identity.PeerReference;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table; 
import jakarta.persistence.*;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(name = "torrent_ratings")
public class TorrentRating {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    private Torrent torrent;

    @ManyToOne
    private PeerReference peer; // peer casting the vote

    private boolean liked;       // true = like, false = dislike

    private Instant createdAt;

    @Lob
    private byte[] signature;    // cryptographic signature of the vote

    // ----------------------
    // Utility Methods
    // ----------------------
    /**
     * Generate a signature for this vote using the peer's private key. The
     * signature covers: torrentId + peerId + liked + createdAt
     */
    public void signVote(PrivateKey privateKey) throws Exception {
        String data = torrent.getId().toString() + peer.getId() + liked + createdAt.toString();
        java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(data.getBytes());
        this.signature = sig.sign();
    }

    /**
     * Verify this vote using the peer's public key. Returns true if the vote is
     * authentic and untampered.
     */
    public boolean verifyVote(PublicKey publicKey) throws Exception {
        String data = torrent.getId().toString() + peer.getId() + liked + createdAt.toString();
        java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
        sig.initVerify(publicKey);
        sig.update(data.getBytes());
        return sig.verify(signature);
    }
}
