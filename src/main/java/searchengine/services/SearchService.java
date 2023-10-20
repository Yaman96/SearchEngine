package searchengine.services;

import searchengine.dto.search.SearchResult;

import java.util.List;

public interface SearchService {

    public List<SearchResult> search(String query, String site, int offset, int limit);
}
