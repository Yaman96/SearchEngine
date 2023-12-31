package searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Data
@AllArgsConstructor
public class SearchSuccessResult implements SearchResult {

    private boolean result;
    private int count;
    private Set<SearchData> data;

    @Override
    public boolean getResult() {
        return result;
    }
}
