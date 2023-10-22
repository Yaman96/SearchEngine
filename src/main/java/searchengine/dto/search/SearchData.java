package searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@AllArgsConstructor
public class SearchData {

    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private double relevance;
}
