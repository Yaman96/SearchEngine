package searchengine.dto.search;

import lombok.Data;

@Data
public class SearchErrorResult implements SearchResult {
    private boolean result;
    private String error;
}
