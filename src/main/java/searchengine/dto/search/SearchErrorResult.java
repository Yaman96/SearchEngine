package searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SearchErrorResult implements SearchResult {
    private boolean result;
    private String error;
}
