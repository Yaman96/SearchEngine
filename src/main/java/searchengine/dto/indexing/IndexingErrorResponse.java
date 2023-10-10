package searchengine.dto.indexing;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IndexingErrorResponse implements IndexingResponse {
    private boolean result;
    private String error;
}
