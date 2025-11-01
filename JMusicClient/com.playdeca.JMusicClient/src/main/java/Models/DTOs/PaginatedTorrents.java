package Models.DTOs;

import Models.Torrents.Core.Torrent;
import java.util.List;

public record PaginatedTorrents(List<Torrent> torrents, long totalCount) {
}