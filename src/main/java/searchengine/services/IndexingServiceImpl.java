package searchengine.services;

import org.jsoup.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingErrorResponse;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.indexing.IndexingSuccessResponse;
import searchengine.model.*;
import searchengine.repositories.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinPool;

@Service
public class IndexingServiceImpl implements IndexingService {

    private final List<searchengine.config.Site> sites;
    private List<Site> createdSites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final IndexJdbcRepositoryImpl indexJdbcRepository;
    private final LemmaFinderService lemmaFinderService;
    private final ConcurrentHashMap<Site, Thread> MAIN_THREADS = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Site, ForkJoinPool> FORK_JOIN_POOLS = new ConcurrentHashMap<>();
    private final Set<Site> STOPPED_SITES = new CopyOnWriteArraySet<>();
    private final static Map<Site, Boolean> SITE_ERROR = new ConcurrentHashMap<>();
    private static volatile boolean indexingIsRunning = false;
    private static volatile boolean stopIndexing = false;


    @Autowired
    public IndexingServiceImpl(SiteRepository siteRepository, SitesList sitesFromConfig, PageRepository pageRepository, LemmaFinderService lemmaFinderService, LemmaRepository lemmaRepository, IndexRepository indexRepository, IndexJdbcRepositoryImpl indexJdbcRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.sites = sitesFromConfig.getSites();
        this.lemmaFinderService = lemmaFinderService;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.indexJdbcRepository = indexJdbcRepository;
    }

    @Override
    public IndexingResponse startIndexing() {
        if (indexingIsRunning) {
            return new IndexingErrorResponse(false, "Indexing is not finished yet");
        }
        indexingIsRunning = true;
        stopIndexing = false;
        clearAllMapsAndListsAfterPreviousIndexing();
        createdSites = createNewSites(sites);
        deleteSiteInfo(null, true);
        for (Site site : createdSites) {
            Thread thread = new Thread(() -> {
                try {
                    ForkJoinPool forkJoinPool = new ForkJoinPool(3);
                    PageExtractorService task = new PageExtractorService(site.getUrl(), site, pageRepository);
                    savePagesEvery200pages(task);
                    FORK_JOIN_POOLS.put(site, forkJoinPool);
                    forkJoinPool.invoke(task);
                    System.err.println("forkJoinPool.isShutdown(): " + forkJoinPool.isShutdown());
                    System.err.println("Thread.currentThread().isInterrupted(): " + Thread.currentThread().isInterrupted());
                    if (!forkJoinPool.isShutdown() || !Thread.currentThread().isInterrupted()) {
                        task.savePages();
                        ArrayList<Long> pagesId = pageRepository.getPagesIdBySiteId(site.getId());
                        ArrayList<ArrayList<Long>> pageIdListBatches = getPageIdListBatches(pagesId);
                        for (ArrayList<Long> batch : pageIdListBatches) {
                            createLemmasAndIndexes(batch, site);
                        }
                    } else {
                        System.out.println("inside search() current thread is interrupted");
                        if (!STOPPED_SITES.contains(site)) {
                            STOPPED_SITES.forEach(site1 -> {
                                System.out.println(site1.getId() + " " + site.getId());
                            });
                            System.out.println("!STOPPED_SITES.contains(site) -> SITE_ERROR.put(site, true)");
                            SITE_ERROR.put(site, true);
                        }
                    }
                } catch (CancellationException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("catch (Exception e)");
                    SITE_ERROR.put(site, true);
                }
            });
            MAIN_THREADS.put(site, thread);
        }
        MAIN_THREADS.forEach((site, thread) -> {
            thread.start();
            updateIndexingTime(site, thread);
        });
        awaitThreadFinish();
//        createdSites.forEach(this::changeSiteStatus);
        indexingIsRunning = false;
        if (SITE_ERROR.values().stream().allMatch(error -> error.equals(true))) {
            return new IndexingErrorResponse(false, "No site has been indexed. An error occurred during the indexing of all sites. Or indexing was stopped by user");
        }
        if (SITE_ERROR.values().stream().anyMatch(error -> error.equals(true))) {
            return new IndexingErrorResponse(true, "Not all sites has been indexed");
        }
        return new IndexingSuccessResponse(true);
    }

    private void clearAllMapsAndListsAfterPreviousIndexing() {
        MAIN_THREADS.clear();
        FORK_JOIN_POOLS.clear();
        STOPPED_SITES.clear();
        SITE_ERROR.clear();
    }

    public IndexingResponse stopIndexing(Long siteId) {
        if (!indexingIsRunning) {
            return new IndexingErrorResponse(false, "Nothing to stop. Indexing is not running.");
        }
        try {
            if (siteId != null) {
                Optional<Site> siteOptional = createdSites.stream().filter(s -> s.getId() == siteId).findFirst();
                Site site;
                if (siteOptional.isPresent()) {
                    site = siteOptional.get();
                    STOPPED_SITES.add(site);
                } else {
                    return new IndexingErrorResponse(false, "There is no such site. Incorrect siteId");
                }
                if (createdSites.size() - STOPPED_SITES.size() > 1) {
                    stopIndexing = false;
                    indexingIsRunning = true;
                } else {
                    stopIndexing = true;
                    indexingIsRunning = false;
                }
                MAIN_THREADS.get(site).interrupt();
                FORK_JOIN_POOLS.get(site).shutdownNow();
                deleteSiteInfo(site, false);
            } else {
                stopIndexing = true;
                indexingIsRunning = false;
                STOPPED_SITES.addAll(createdSites);
                MAIN_THREADS.forEach((site, thread) -> thread.interrupt());
                FORK_JOIN_POOLS.forEach((site, fjp) -> fjp.shutdownNow());
                deleteSiteInfo(null, true);
                clearAllMapsAndListsAfterPreviousIndexing();
            }
            return new IndexingSuccessResponse(true);
        } catch (Exception e) {
            stopIndexing = false;
            indexingIsRunning = true;
            System.err.println(Arrays.toString(e.getStackTrace()));
            throw new RuntimeException(e);
//            return new IndexingErrorResponse(false, "Indexing is not stopped. An error occurred. Error: " + Arrays.toString(e.getStackTrace()));
        }
    }

    @Override
    public IndexingResponse indexPage(String url) {
        if (sites.stream().noneMatch(site -> site.getUrl().startsWith(Objects.requireNonNull(getBaseUrl(url))))) {
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

        if (HTML != null && !HTML.isEmpty() && !HTML.isBlank()) {
            Map<String, Integer> lemmaCountMapFromNewPage = lemmaFinderService.collectLemmas(HTML);
            if (page != null) {
                List<Index> oldPageIndexes = indexRepository.findAllByPageId(page.getId());
                if (oldPageIndexes.size() == 0) {
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
                        Lemma newLemma = new Lemma(site.getId(), lemmaString, 1);
                        lemmaRepository.save(newLemma);
                        newLemma = lemmaRepository.findByLemmaEquals(lemmaString).get();
                        Index newIndex = new Index(pageId, newLemma.getId(), count);
                        indexRepository.save(newIndex);
                    }
                });
                indexRepository.saveAll(newIndexes);
            }
        }
        return new IndexingSuccessResponse(true);
    }

    /*Deletes site from DB*/
    private void deleteSiteInfo(Site site, boolean deleteAll) {
        if (deleteAll) {
            indexRepository.deleteAllIndexes();
            lemmaRepository.deleteAllLemmas();
            pageRepository.deleteAll();
            return;
        }
        for (Page page : site.getPages()) {
            indexRepository.deleteByPageId(page.getId());
        }
        lemmaRepository.deleteAllBySiteId(site.getId());
        pageRepository.deleteAllBySiteIs(site);
    }

    /*Creates model.Site objects from simple config.Site objects*/
    private List<Site> createNewSites(List<searchengine.config.Site> sitesFromConfigToCreate) {
        List<Site> createdSites = new ArrayList<>();
        for (searchengine.config.Site site : sitesFromConfigToCreate) {
            Site newSite = siteRepository.findByNameContainsIgnoreCase(site.getName());
            if (newSite == null) {
                newSite = new Site();
                newSite.setName(site.getName());
                newSite.setUrl(site.getUrl());
                newSite.setStatus(Status.INDEXING.toString());
                newSite.setStatusTime(LocalDateTime.now());
                createdSites.add(siteRepository.save(newSite));
            } else {
                newSite.setStatus(Status.INDEXING.toString());
                newSite.setStatusTime(LocalDateTime.now());
                newSite.setLastError("");
                createdSites.add(siteRepository.save(newSite));
            }
        }
        return createdSites;
    }

    //Update indexing time every 2 sec
    @SuppressWarnings("All")
    private void updateIndexingTime(Site currentSite, Thread currentThread) {
        Thread updateThread = new Thread(() -> {
            while (currentThread.isAlive()) {
                currentSite.setStatusTime(LocalDateTime.now());
                siteRepository.save(currentSite);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (stopIndexing) {
                currentSite.setLastError("Indexing is stopped by user because stopIndexing is true");
                currentSite.setStatus(Status.FAILED.toString());
                siteRepository.save(currentSite);
            } else if (STOPPED_SITES.contains(currentSite)) {
                currentSite.setLastError("Indexing is stopped by user because STOPPED_SITES.contains(currentSite)");
                currentSite.setStatus(Status.FAILED.toString());
                siteRepository.save(currentSite);
            } else if (SITE_ERROR.getOrDefault(currentSite, false)) {
                currentSite.setLastError("An error occurred. Indexing is stopped");
                currentSite.setStatus(Status.FAILED.toString());
                siteRepository.save(currentSite);
            } else {
                currentSite.setStatus(Status.INDEXED.toString());
                siteRepository.save(currentSite);
            }
        });
        updateThread.start();
    }

    /*Changes site's status to INDEXED or FAILED*/
    private void changeSiteStatus(Site site) {
        if (STOPPED_SITES.contains(site)) {
            site.setStatus(Status.FAILED.toString());
            site.setLastError("Indexing is stopped by user from changeSiteStatus");
            siteRepository.save(site);
        } else if (SITE_ERROR.getOrDefault(site, false)) {
            site.setStatus(Status.FAILED.toString());
            site.setLastError("An error occurred. Indexing is stopped");
            siteRepository.save(site);
        } else {
            site.setStatus(Status.INDEXED.toString());
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

    private ArrayList<ArrayList<Long>> getPageIdListBatches(List<Long> pagesId) {
        if (Thread.currentThread().isInterrupted()) {
            System.out.println("inside getPageIdListBatches() Current thread is interrupted");
            return new ArrayList<>();
        }
        ArrayList<ArrayList<Long>> batches = new ArrayList<>();
        int batchSize = 50;
        int total = pagesId.size();
        for (int i = 0; i < total; i += batchSize) {
            int endIndex = Math.min(i + batchSize, total);
            ArrayList<Long> batch = new ArrayList<>(pagesId.subList(i, endIndex));
            batches.add(batch);
        }
        return batches;
    }

    private void createLemmasAndIndexes(List<Long> batch, Site site) {
        if (Thread.currentThread().isInterrupted()) {
            System.out.println("inside createLemmasAndIndexes() Current thread is interrupted");
            return;
        }
        long siteId = site.getId();
        List<Page> savedPages = new ArrayList<>();
        batch.forEach(pageId -> savedPages.add(pageRepository.findById(pageId)));
        List<Index> indexList = new ArrayList<>();

        for (Page page : savedPages) {
            long pageId = page.getId();
            Map<String, Integer> lemma_Count = lemmaFinderService.collectLemmas(page.getContent());


            lemma_Count.forEach((lemmaString, count) -> {
                Lemma lemma = new Lemma(siteId, lemmaString, 1);
                long lemmaId = lemmaRepository.saveOrUpdate(lemma);
                Index index = new Index(pageId, lemmaId, count);
                indexList.add(index);
            });
        }
        String sqlIndexInsertQuery = prepareIndexSqlInsertQuery(prepareIndexValuesForSqlInsertQuery(indexList));
        indexJdbcRepository.executeSql(sqlIndexInsertQuery);
    }

    private String prepareIndexValuesForSqlInsertQuery(List<Index> indexList) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < indexList.size(); i++) {
            Index currentIndex = indexList.get(i);
            if (i == indexList.size() - 1) {
                result.append("(").append(currentIndex.getPageId()).append(", ")
                        .append(currentIndex.getLemmaId()).append(", ")
                        .append(currentIndex.getRank()).append(");");
                break;
            }
            result.append("(").append(currentIndex.getPageId()).append(", ")
                    .append(currentIndex.getLemmaId()).append(", ")
                    .append(currentIndex.getRank()).append("), ");
        }
        return result.toString();
    }

    private String prepareIndexSqlInsertQuery(String values) {
        return "INSERT INTO index_1 (page_id, lemma_id, rank_1) VALUES" + values;
    }

    private void awaitThreadFinish() {
        while (MAIN_THREADS.values().stream().anyMatch(Thread::isAlive)) {
            System.out.println("Waiting main threads to finish");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void savePagesEvery200pages(PageExtractorService task) {
        Thread currentThread = Thread.currentThread();
        Thread thread = new Thread(() -> {
            while (!currentThread.isInterrupted()) {
                if (!currentThread.isAlive()) {
                    break;
                }
                System.out.println("Page list size: " + task.pageList.size() + " from thread: " + Thread.currentThread().getName());
                System.out.println("Link list size: " + task.links.size() + " from thread: " + Thread.currentThread().getName());
                if (task.pageList.size() >= 200) {
                    task.savePages();
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread.start();
    }
}
