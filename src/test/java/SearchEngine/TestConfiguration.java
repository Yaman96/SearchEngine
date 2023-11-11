package SearchEngine;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.impl.SearchServiceImpl;

@Configuration
@Import({SearchServiceImpl.class,
        LemmaRepository.class,
        IndexRepository.class,
        SiteRepository.class,
        PageRepository.class})
public class TestConfiguration {
}
