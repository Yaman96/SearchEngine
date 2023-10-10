package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingErrorResponse;
import searchengine.dto.indexing.IndexingResponse;

import searchengine.dto.indexing.IndexingSuccessResponse;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sitesListFromConfig;

    private final SiteRepository siteRepository;

    private final PageRepository pageRepository;

    Map<String,Future> futures = new HashMap<>();

    private boolean indexingIsRunning = false;
    private boolean stopIndexing = false;

    @Autowired
    public IndexingServiceImpl(SiteRepository siteRepository, SitesList sitesListFromConfig, PageRepository pageRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.sitesListFromConfig = sitesListFromConfig;
    }

    @Override
    public IndexingResponse startIndexing() {
        if (indexingIsRunning) {
            return new IndexingErrorResponse(false,"Indexing is already running");
        }
        indexingIsRunning = true;
        stopIndexing = false;
        deleteSiteInfo(sitesListFromConfig.getSites());
        List<Site> createdSites = createNewSites(sitesListFromConfig.getSites());

        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1);
        futures = new HashMap<>();

        for (Site site : createdSites) {
            futures.put(site.getUrl(), executorService.submit( () -> {
                NewLinkExtractorService linkExtractor = new NewLinkExtractorService(site.getUrl(), site);
                return linkExtractor.invoke();
            }));
        }

        for (Site site : createdSites) {
            new Thread(() -> {
                while (!futures.get(site.getUrl()).isDone()) {
                    updateIndexingTime(site);
                    System.out.println("size: " + NewLinkExtractorService.pageList.size());
                    if(NewLinkExtractorService.pageList.size() > 100) {
                        new Thread(() -> {
                            synchronized (NewLinkExtractorService.pageList) {
                            List<Page> pagesToSave = new ArrayList<>(NewLinkExtractorService.pageList);
                            pageRepository.saveAll(pagesToSave);
                            NewLinkExtractorService.pageList.removeAll(pagesToSave);
                            pagesToSave.clear();
                            System.out.println("Links list: " + NewLinkExtractorService.links.size());
                        }}).start();
                    }
                }
                changeSiteStatusToIndexed(site);
            }).start();
        }

        for (Site site : createdSites) {
            try {
                futures.get(site.getUrl()).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            pageRepository.saveAll(NewLinkExtractorService.pageList);
        } catch (EntityNotFoundException e) {
            indexingIsRunning = false;
            System.out.println("ALKDNF:LADNFLKADLFNLAKDNDFLKNALKFLKASLKFN ]");
            throw new RuntimeException(e);
        }
        indexingIsRunning = false;
        NewLinkExtractorService.links.clear();
        NewLinkExtractorService.pageList.clear();
        return new IndexingSuccessResponse(true);
    }

    public IndexingResponse stopIndexing() {
        stopIndexing = true;
        futures.forEach((x,y) -> y.cancel(true));

        return new IndexingErrorResponse(false, "Indexing is stopped by user");
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
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
    }

    private void changeSiteStatusToIndexed(Site site) {
        if(!site.getStatus().equals(Status.FAILED.toString()) && !stopIndexing) {
        site.setStatus(Status.INDEXED.toString());
        siteRepository.save(site);
        }else {
            site.setStatus(Status.FAILED.toString());
            site.setLastError("Indexing is stopped by user");
            siteRepository.save(site);
        }
    }
}
