package searchengine.dto.indexing;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IndexingSuccessResponse implements IndexingResponse {
    private boolean result;

    @Override
    public boolean getResult() {
        return result;
    }
}
