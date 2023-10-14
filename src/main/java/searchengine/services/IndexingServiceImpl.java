package searchengine.services;

import org.jsoup.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingErrorResponse;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.indexing.IndexingSuccessResponse;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import javax.persistence.EntityNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class IndexingServiceImpl implements IndexingService {

    private final List<searchengine.config.Site> sites;
    private final SiteRepository siteRepository;

    private final PageRepository pageRepository;

    private final LemmaRepository lemmaRepository;

    private final IndexRepository indexRepository;
    private final PageExtractorPrototypeFactory pageExtractorPrototypeFactory;

    private final LemmaFinderService lemmaFinderService;

    private Map<String,Future> futures = new HashMap<>();

    public static boolean indexingIsRunning = false;
    private boolean stopIndexing = false;

    @Autowired
    public IndexingServiceImpl(SiteRepository siteRepository, SitesList sitesFromConfig, PageRepository pageRepository, PageExtractorPrototypeFactory pageExtractorPrototypeFactory, LemmaFinderService lemmaFinderService, LemmaRepository lemmaRepository, IndexRepository indexRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.sites = sitesFromConfig.getSites();
        this.pageExtractorPrototypeFactory = pageExtractorPrototypeFactory;
        this.lemmaFinderService = lemmaFinderService;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
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
        if(sites.stream().noneMatch(site -> site.getUrl().startsWith(Objects.requireNonNull(getBaseUrl(url)))))
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
            Map<String, Integer> lemmaCountMapFromNewPage = lemmaFinderService.collectLemmas(HTML);
            if (page != null) {
                List<Index> oldPageIndexes = indexRepository.findAllByPageId(page.getId());
                if(oldPageIndexes.size() == 0) {
                    System.out.println("The page has not been indexed previously");
                }
                for (Index index : oldPageIndexes) {
                    Optional<Lemma> lemmaOptional = lemmaRepository.findById(index.getLemmaId());
                    if (lemmaOptional.isPresent()) {
                        Lemma lemmaFromDB = lemmaOptional.get();
                        String lemmaString = lemmaFromDB.getLemma();

                        if (lemmaCountMapFromNewPage.containsKey(lemmaString)) {
                            int count = lemmaCountMapFromNewPage.get(lemmaString);
                            index.setRank(count);
                        }
                    }
                }
            } else { //Если страницы не было нахой
                site = siteRepository.findByUrlStartingWith(getBaseUrl(url));
                page = new Page(url, code, HTML, site);
                pageRepository.save(page);
                long pageId = pageRepository.findByPath(url).getId();
                List<Index> newIndexes = new ArrayList<>();
                System.out.println("lemmaCountMapFromNewPage elements count: " + lemmaCountMapFromNewPage.size());
                lemmaCountMapFromNewPage.forEach((lemmaString, count) -> {
                    Optional<Lemma> lemmaOptional = lemmaRepository.findByLemmaEquals(lemmaString);
                    if (lemmaOptional.isPresent()) {
                        Lemma lemmaFromDB = lemmaOptional.get();
                        lemmaFromDB.setFrequency(lemmaFromDB.getFrequency() + 1);
                        lemmaRepository.save(lemmaFromDB);
                        newIndexes.add(new Index(pageId, lemmaFromDB.getId(), count));
                    } else {
                        Lemma newLemma = new Lemma(site.getId(),lemmaString,1);
                        lemmaRepository.save(newLemma);
                        newLemma = lemmaRepository.findByLemmaEquals(lemmaString).get();
                        Index newIndex = new Index(pageId,newLemma.getId(),count);
                        indexRepository.save(newIndex);
                    }
                });
                indexRepository.saveAll(newIndexes);
            }
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
            Site siteFromDB = siteRepository.findByUrlStartingWith(site.getUrl());
            for(Page page : siteFromDB.getPages()) {
                indexRepository.deleteByPageId(page.getId());
            }
            lemmaRepository.deleteAllBySiteId(siteFromDB.getId());
            pageRepository.deleteAllBySiteIs(siteFromDB);
            siteRepository.deleteById(siteFromDB.getId());
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
