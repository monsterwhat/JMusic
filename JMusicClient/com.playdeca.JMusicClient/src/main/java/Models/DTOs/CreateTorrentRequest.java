package Models.DTOs;

import java.util.List;
import java.util.UUID;

public record CreateTorrentRequest(String name, String infoHash, UUID creatorId, List<String> tags, byte[] signature) {
}
