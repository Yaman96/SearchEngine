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
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinPool;

@Service
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sitesListFromConfig;

    private final SiteRepository siteRepository;

    private final PageRepository pageRepository;

    private final ApplicationContext applicationContext;

    private volatile boolean isIndexing = false;

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
            isIndexing = true;
            new Thread(() -> updateIndexingTime(site)).start();
            NewLinkExtractorService linkExtractor = new NewLinkExtractorService(site.getUrl(),site);
//            setUpLinkExtractorService(site,linkExtractor);
            ForkJoinPool forkJoinPool = new ForkJoinPool();
            CopyOnWriteArraySet<Page> pages = forkJoinPool.invoke(linkExtractor);
//            assignSiteToPages(site);
            pageRepository.saveAll(pages);
            isIndexing = false;
            changeSiteStatusToIndexed(site);
//            resetLinkExtractor();
//            System.out.println("Result " + result);
        }
        return null;
    }

    private void assignSiteToPages(Site site) {
        for (Page page : LinkExtractorService.pageList) {
            page.setSite(site);
        }
    }

    private void resetLinkExtractor() {
        LinkExtractorService.pageList.clear();
        LinkExtractorService.links.clear();
        LinkExtractorService.currentSite = null;
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

    @SuppressWarnings("All")
    private void updateIndexingTime(Site site) {
        while (isIndexing) {
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void changeSiteStatusToIndexed(Site site) {
        if(!site.getStatus().equals(Status.FAILED.toString())) {
        site.setStatus(Status.INDEXED.toString());
        siteRepository.save(site);
        }
    }

    private void setUpLinkExtractorService(Site site, LinkExtractorService linkExtractor) {
        LinkExtractorService.setStartURL(site.getUrl());
        LinkExtractorService.currentSite = site;
        linkExtractor.setUrl(site.getUrl());
    }
}
