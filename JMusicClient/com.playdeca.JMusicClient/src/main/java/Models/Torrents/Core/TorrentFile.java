package Models.Torrents.Core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(name = "torrent_files")
public class TorrentFile {

    @Id
    @GeneratedValue
    private UUID id;

    private String fileName;          // the .torrent file name
    @Column(nullable = false)
    private String filePath;   // full or relative path on local or remote storage
    private long fileSize;

    @ManyToOne(fetch = FetchType.LAZY)
    private Torrent torrent;
}
