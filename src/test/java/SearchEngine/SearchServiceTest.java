package SearchEngine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import searchengine.dto.search.SearchResult;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.SearchService;

@SpringBootTest
@ComponentScan(basePackages = "searchengine")
public class SearchServiceTest {

    @InjectMocks
    private SearchService searchService;

    @Test
    public void testSearch() {
        SearchResult searchResult = searchService.search("лазерное излучение", "ipfran",0,20);
    }

}
