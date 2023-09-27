package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;

import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

@Service
public class IndexingServiceImpl implements IndexingService {

    private SitesList sitesListFromConfig;

    private SiteRepository siteRepository;

    private PageRepository pageRepository;

    private ApplicationContext applicationContext;

    @Autowired
    public IndexingServiceImpl(SiteRepository siteRepository, SitesList sitesListFromConfig, ApplicationContext applicationContext, PageRepository pageRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.sitesListFromConfig = sitesListFromConfig;
        this.applicationContext = applicationContext;
    }

    @Override
    public IndexingResponse startIndexing() {
        deleteSiteInfo(sitesListFromConfig.getSites());
        List<Site> createdSites = createNewSites(sitesListFromConfig.getSites());

        for (Site site : createdSites) {
//            List<LinkExtractorService> linkExtractorTasks = new ArrayList<>();
            LinkExtractorService linkExtractor = applicationContext.getBean(LinkExtractorService.class);
            LinkExtractorService.setStartURL(site.getUrl());
            linkExtractor.setUrl(site.getUrl());
//            linkExtractorTasks.add(linkExtractor);
            ForkJoinPool forkJoinPool = new ForkJoinPool();
            String result = forkJoinPool.invoke(linkExtractor);
            for (Page page : LinkExtractorService.pageList) {
                page.setSite(site);
            }
            pageRepository.saveAll(LinkExtractorService.pageList);
            LinkExtractorService.pageList.clear();
            LinkExtractorService.linksListReset();
            System.out.println("Result " + result);
        }

        return null;
    }

    private void deleteSiteInfo(List<searchengine.config.Site> sitesToDelete) {
        for (searchengine.config.Site site: sitesToDelete) {
            siteRepository.deleteByNameContainsIgnoreCase(site.getName()); //TODO check what CRUDRepository can return instead of void
            //TODO here we can add logging info
        }
    }

    private List<Site> createNewSites(List<searchengine.config.Site> sitesFromConfigToCreate) {
        List<Site> createdSites = new ArrayList<>();
        for (searchengine.config.Site site: sitesFromConfigToCreate) {
            Site newSite = new Site();
            newSite.setName(site.getName());
            newSite.setUrl(site.getUrl());
            newSite.setStatus(Status.INDEXING.toString());
            newSite.setStatusTime(LocalDateTime.now());
            createdSites.add(newSite);
        }
        siteRepository.saveAll(createdSites);
        return createdSites;
    }
}
