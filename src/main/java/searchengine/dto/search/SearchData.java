package searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;

@Data
@AllArgsConstructor
public class SearchData implements Comparable<SearchData> {

    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private double relevance;


    @Override
    public int compareTo(@NotNull SearchData o) {
        if (relevance == o.relevance) {
            return uri.compareTo(o.uri);
        }
        return Double.compare(relevance,o.relevance);
    }
}
