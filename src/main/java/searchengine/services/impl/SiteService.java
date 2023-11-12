package searchengine.services.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.Site;
import searchengine.repositories.SiteRepository;

@Service
@AllArgsConstructor
public class SiteService {

    private SiteRepository siteRepository;

    public Site findByNameContainsIgnoreCase(String siteName) {
     return siteRepository.findByNameContainsIgnoreCase(siteName);
    }

    public Site findByUrlStartingWith(String url) {
        return siteRepository.findByUrlStartingWith(url);
    }

    public Site save(Site site) {
        return siteRepository.save(site);
    }

    public Iterable<Site> findAll() {
        return siteRepository.findAll();
    }
}
