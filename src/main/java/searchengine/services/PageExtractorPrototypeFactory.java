package searchengine.services;

import org.springframework.stereotype.Component;
import searchengine.model.Site;

@Component
public class PageExtractorPrototypeFactory {

    public PageExtractorService createPageExtractorService(String mainPageLink, Site site) {
        return new PageExtractorService(mainPageLink,site);
    }
}
