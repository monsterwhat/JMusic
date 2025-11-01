package Models.DTOs;

import java.util.List;
import java.util.UUID;

public record UpdateTorrentRequest(String name, String infoHash, UUID creatorId, List<String> tags, boolean verified, boolean active) {
}