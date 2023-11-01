package SearchEngine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import searchengine.dto.search.SearchResult;
import searchengine.model.Lemma;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.SearchService;

import java.util.ArrayList;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = TestConfiguration.class)
public class SearchServiceTest {

    @Autowired
    private SearchService searchService;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private SiteRepository siteRepository;

    @Test
    public void testSearchIfAllQueryWordsAreTooFrequent() {
        Site site = siteRepository.findAnyIndexedSite();
        ArrayList<Lemma> lemmasFromDataBase = new ArrayList<>(lemmaRepository.findBySiteId());
        SearchResult searchResult = searchService.search("лазерное излучение", "ipfran",0,20);
    }

}
