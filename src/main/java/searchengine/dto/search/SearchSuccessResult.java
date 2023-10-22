package searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@AllArgsConstructor
public class SearchSuccessResult implements SearchResult {

    private boolean result;
    private int count;
    private List<SearchData> data;
}
