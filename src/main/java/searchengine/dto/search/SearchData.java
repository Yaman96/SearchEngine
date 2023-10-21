package searchengine.dto.search;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public record SearchData(String site, String siteName, String uri, String title, String snippet, double relevance) {
}
