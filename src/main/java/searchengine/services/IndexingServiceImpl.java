package searchengine.services;

import org.jsoup.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingErrorResponse;
import searchengine.dto.indexing.IndexingResponse;

import searchengine.dto.indexing.IndexingSuccessResponse;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import javax.persistence.EntityNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class IndexingServiceImpl implements IndexingService {

    private final List<searchengine.config.Site> sites;
    private final SiteRepository siteRepository;

    private final PageRepository pageRepository;

    private final PageExtractorPrototypeFactory pageExtractorPrototypeFactory;

    private final LemmaFinderService lemmaFinderService;

    private final LemmaRepository lemmaRepository;
    private Map<String,Future> futures = new HashMap<>();

    public static boolean indexingIsRunning = false;
    private boolean stopIndexing = false;

    @Autowired
    public IndexingServiceImpl(SiteRepository siteRepository, SitesList sitesFromConfig, PageRepository pageRepository, PageExtractorPrototypeFactory pageExtractorPrototypeFactory, LemmaFinderService lemmaFinderService, LemmaRepository lemmaRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.sites = sitesFromConfig.getSites();
        this.pageExtractorPrototypeFactory = pageExtractorPrototypeFactory;
        this.lemmaFinderService = lemmaFinderService;
        this.lemmaRepository = lemmaRepository;
    }

    @Override
    public IndexingResponse startIndexing() {
        if (indexingIsRunning) {
            return new IndexingErrorResponse(false,"Indexing is not finished yet");
        }
        indexingIsRunning = true;
        stopIndexing = false;
        deleteSiteInfo(sites);
        List<Site> createdSites = createNewSites(sites);

        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1);
        futures.clear();

        prepareFutures(createdSites, executorService);
        runActivityMonitoringThreads(createdSites);
        startFutures(createdSites);
        savePagesAndResetPageExtractorStaticFields();
        return new IndexingSuccessResponse(true);
    }

    public IndexingResponse stopIndexing() {
        stopIndexing = true;
        futures.forEach((x,y) -> y.cancel(true));
        return new IndexingErrorResponse(false, "Indexing is stopped by user");
    }

    @Override
    public IndexingResponse indexPage(String url) {
        if(sites.stream().noneMatch(site -> site.getUrl().startsWith(url)))
        {
            return new IndexingErrorResponse(false, "This page is outside the sites specified in the configuration file");
        }
        String HTML = null;
        int code = 418;
        try {
            Connection.Response response = PageExtractorService.getResponse(url);
            code = response.statusCode();
            HTML = PageExtractorService.getHTML(response);
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }

        Page page = pageRepository.findByPath(url);
        Site site;

        if(HTML != null && !HTML.isEmpty() && !HTML.isBlank()) {
            if(page != null) {
                page.setCode(code);
                page.setContent(HTML);
                site = page.getSite();
                //TODO check lemmas from the old page and compare with lemmas in new page.
                // If a lemma that was in the old page doesn't exist in the new page then
                // the frequency of the lemma must be decreased by 1
            }else {
                site = siteRepository.findByNameContainsIgnoreCase(getBaseUrl(url));
                page = new Page(url,code,HTML,site);
            }

            pageRepository.save(page);
            Map<String, Integer> lemmas = lemmaFinderService.collectLemmas(HTML);
            ArrayList<Lemma> lemmaArrayList = new ArrayList<>();
            lemmas.forEach((string, count) -> lemmaArrayList.add(new Lemma(site.getId(),string,1)));
            lemmaRepository.saveAll(lemmaArrayList);
        }

        return null;
    }

    private void savePagesAndResetPageExtractorStaticFields() {
        try {
            pageRepository.saveAll(PageExtractorService.pageList);
        } catch (EntityNotFoundException e) {
            indexingIsRunning = false;
            throw new RuntimeException(e);
        }
        indexingIsRunning = false;
        PageExtractorService.links.clear();
        PageExtractorService.pageList.clear();
    }

    private void startFutures(List<Site> createdSites) {
        for (Site site : createdSites) {
            try {
                futures.get(site.getUrl()).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Create and run threads for updating sites indexing time and
     * saves extracted pages if pageList size > 100 to prevent Java Heap exception
     */
    private void runActivityMonitoringThreads(List<Site> createdSites) {
        for (Site site : createdSites) {
            new Thread(() -> {
                while (!futures.get(site.getUrl()).isDone()) {
                    updateIndexingTime(site);
                    System.out.println("size: " + PageExtractorService.pageList.size());
                    pageListSizeMonitoring();
                }
                changeSiteStatus(site);
            }).start();
        }
    }

    /**
     * Checks if LinkExtractorService.pageList.size() > 100.
     * If true saves pages to DB and remove them from
     * LinkExtractorService.pageList
     */
    private void pageListSizeMonitoring() {
        if(PageExtractorService.pageList.size() > 100) {
            new Thread(() -> {
                synchronized (PageExtractorService.pageList) {
                List<Page> pagesToSave = new ArrayList<>(PageExtractorService.pageList);
                pageRepository.saveAll(pagesToSave);
                PageExtractorService.pageList.removeAll(pagesToSave);
                pagesToSave.clear();
                System.out.println("Links list: " + PageExtractorService.links.size());
            }}).start();
        }
    }
    /*Creates futures with pageExtractors for each site*/

    private void prepareFutures(List<Site> createdSites, ExecutorService executorService) {
        for (Site site : createdSites) {
            futures.put(site.getUrl(), executorService.submit( () -> {
//                PageExtractorService pageExtractor = new PageExtractorService(site.getUrl(), site);
                PageExtractorService pageExtractor = pageExtractorPrototypeFactory.createPageExtractorService(site.getUrl(),site);
                return pageExtractor.invoke();
            }));
        }
    }
    /*Stops indexing process*/

    /*Deletes site from DB*/
    private void deleteSiteInfo(List<searchengine.config.Site> sitesToDelete) {
        for (searchengine.config.Site site: sitesToDelete) {
            siteRepository.deleteByNameContainsIgnoreCase(site.getName());
        }
    }

    /*Creates model.Site objects from simple config.Site objects*/
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

    //Update indexing time every 1 sec
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

    /*Changes site's status to INDEXED or FAILED*/
    private void changeSiteStatus(Site site) {
        if(!site.getStatus().equals(Status.FAILED.toString()) && !stopIndexing) {
        site.setStatus(Status.INDEXED.toString());
        siteRepository.save(site);
        }else {
            site.setStatus(Status.FAILED.toString());
            site.setLastError("Indexing is stopped by user");
            siteRepository.save(site);
        }
    }

    public static String getBaseUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String authority = uri.getAuthority();
            return scheme + "://" + authority;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }
}
