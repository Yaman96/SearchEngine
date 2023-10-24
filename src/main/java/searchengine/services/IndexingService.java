package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;

import java.util.List;

public interface IndexingService {
     IndexingResponse startIndexing();
     IndexingResponse stopIndexing(Long siteId);
     IndexingResponse indexPage(String url);
}
