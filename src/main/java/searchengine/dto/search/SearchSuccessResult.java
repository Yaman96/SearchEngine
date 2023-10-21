package searchengine.dto.search;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public record SearchSuccessResult(boolean result, int count, List<SearchData> data) implements SearchResult {
}
