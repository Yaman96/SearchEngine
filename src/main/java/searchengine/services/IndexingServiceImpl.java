package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;

import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class IndexingServiceImpl implements IndexingService {

    private SitesList sitesListFromConfig;

    private SiteRepository siteRepository;

    private ApplicationContext applicationContext;

    @Autowired
    public IndexingServiceImpl(SiteRepository siteRepository, SitesList sitesListFromConfig, ApplicationContext applicationContext) {
        this.siteRepository = siteRepository;
        this.sitesListFromConfig = sitesListFromConfig;
        this.applicationContext = applicationContext;
    }

    @Override
    public IndexingResponse startIndexing() {
        deleteSiteInfo(sitesListFromConfig.getSites());
        List<Site> createdSites = createNewSites(sitesListFromConfig.getSites());

        for (Site site : createdSites) {
            LinkExtractorService linkExtractor = applicationContext.getBean(LinkExtractorService.class);
            linkExtractor.setStartURL(site.getUrl());
            linkExtractor.setUrl(site.getUrl());

        }

        return null;
    }

    private void deleteSiteInfo(List<searchengine.config.Site> sitesToDelete) {
        for (searchengine.config.Site site: sitesToDelete) {
            boolean siteIsDeleted = siteRepository.deleteByNameContainsIgnoreCase(site.getName());
            System.out.println("Site "+ site.getName() + " is deleted from DB: " + siteIsDeleted);
            //TODO here we can add logging info
        }
    }

    private List<Site> createNewSites(List<searchengine.config.Site> sitesFromConfigToCreate) {
        List<Site> createdSites = new ArrayList<>();
        for (searchengine.config.Site site: sitesFromConfigToCreate) {
            Site newSite = new Site();
            newSite.setName(site.getName());
            newSite.setUrl(site.getUrl());
            newSite.setStatus(Status.INDEXING);
            newSite.setStatusTime(LocalDateTime.now());
            createdSites.add(newSite);
        }
        siteRepository.saveAll(createdSites);
        return createdSites;
    }
}
