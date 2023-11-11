package searchengine.services.impl;

import org.jsoup.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.indexing.IndexingErrorResponse;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.indexing.IndexingSuccessResponse;
import searchengine.model.*;
import searchengine.repositories.*;
import searchengine.repositories.impl.IndexJdbcRepositoryImpl;
import searchengine.services.IndexingService;
import searchengine.services.LemmaFinderService;
import searchengine.services.PageExtractorService;

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

    public final static List<searchengine.config.Site> sites = new ArrayList<>();
    private List<Site> createdSites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final IndexJdbcRepositoryImpl indexJdbcRepository;
    private final LemmaFinderService lemmaFinderServiceImpl;
    private final ConcurrentHashMap<Site, Thread> MAIN_THREADS = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Site, ForkJoinPool> FORK_JOIN_POOLS = new ConcurrentHashMap<>();
    private final Set<Site> STOPPED_SITES = new CopyOnWriteArraySet<>();
    private final Map<Site, Boolean> SITE_ERROR = new ConcurrentHashMap<>();
    private final Set<Lemma> lemmasToSave = new CopyOnWriteArraySet<>();
    private static volatile boolean indexingIsRunning = false;
    private static volatile boolean stopIndexing = false;


    @Autowired
    public IndexingServiceImpl(SiteRepository siteRepository, PageRepository pageRepository, LemmaFinderService lemmaFinderServiceImpl, LemmaRepository lemmaRepository, IndexRepository indexRepository, IndexJdbcRepositoryImpl indexJdbcRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaFinderServiceImpl = lemmaFinderServiceImpl;
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
        createdSites = createNewSites();
        createdSites.forEach(site -> deleteSiteInfo(site, false));
        PageExtractorServiceImpl.pageRepository = pageRepository;
        for (Site site : createdSites) {
            Thread thread = new Thread(() -> {
                try {
                    parseAndIndexSite(site);
                } catch (CancellationException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                    SITE_ERROR.put(site, true);
                }
            });
            MAIN_THREADS.put(site, thread);
        }
        MAIN_THREADS.forEach((site, thread) -> {
            thread.start();
            updateIndexingTime(site, thread);
        });
        new Thread(this::awaitThreadFinish).start();
        return new IndexingSuccessResponse(true);
    }

    public IndexingResponse stopIndexing(Long siteId) {
        indexingIsRunning = isIndexingIsRunning();
        if (!indexingIsRunning) {
            return new IndexingErrorResponse(false, "Nothing to stop. Indexing is not running.");
        }
        try {
            if (siteId != null) {
                return stopIndexingWhenSiteIsProvided(siteId);
            } else {
                return stopIndexingWhenSiteIsNotProvided();
            }
        } catch (Exception e) {
            stopIndexing = false;
            indexingIsRunning = true;
            System.err.println(Arrays.toString(e.getStackTrace()));
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("all")
    @Override
    public IndexingResponse indexPage(String url) {
        if (sites.stream().noneMatch(site -> site.getUrl().startsWith(Objects.requireNonNull(getBaseUrl(url))))) {
            return new IndexingErrorResponse(false, "This page is outside the sites specified in the configuration file");
        }

        Connection.Response response;
        String HTML = null;
        try {
            response = PageExtractorService.getResponse(url);
            HTML = PageExtractorService.getHTML(response);
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            return new IndexingErrorResponse(false, "Connection failed");
        }

        int code = response.statusCode();
        Page page = pageRepository.findByPath(url);

        Map<String, Integer> lemmaCountMapFromNewPage = lemmaFinderServiceImpl.collectLemmas(HTML);
        if (page != null) {
            List<Index> oldPageIndexes = indexRepository.findAllByPageId(page.getId());
            updateIndexesAndLemmasWhenPageIsPresent(lemmaCountMapFromNewPage, oldPageIndexes);
        } else {
            Site site = siteRepository.findByUrlStartingWith(getBaseUrl(url));
            page = new Page(url, code, HTML, site);
            pageRepository.save(page);
            long pageId = pageRepository.findByPath(url).getId();
            List<Index> newIndexes = new ArrayList<>();
            lemmaCountMapFromNewPage.forEach((lemmaString, count) -> {
                updateIndexesAndLemmasWhenPageIsNotPresent(lemmaString, count, newIndexes, pageId, site);
            });
            indexRepository.saveAll(newIndexes);
        }
        return new IndexingSuccessResponse(true);
    }

    private IndexingResponse stopIndexingWhenSiteIsProvided(Long siteId) {
        Optional<Site> siteOptional = createdSites.stream().filter(s -> s.getId() == siteId).findFirst();
        Site site;
        if (siteOptional.isPresent()) {
            site = siteOptional.get();
            STOPPED_SITES.add(site);
        } else {
            return new IndexingErrorResponse(false, "There is no such site. Incorrect siteId");
        }
        if (createdSites.size() - STOPPED_SITES.size() >= 1) {
            stopIndexing = false;
            indexingIsRunning = true;
        } else {
            stopIndexing = true;
            indexingIsRunning = false;
        }
        MAIN_THREADS.get(site).interrupt();
        FORK_JOIN_POOLS.get(site).shutdownNow();
        if (createdSites.size() == STOPPED_SITES.size()) {
            deleteSiteInfo(null, true);
            clearAllMapsAndListsAfterPreviousIndexing();
        } else {
            deleteSiteInfo(site, false);
        }
        return new IndexingSuccessResponse(true);
    }

    private IndexingResponse stopIndexingWhenSiteIsNotProvided() {
        stopIndexing = true;
        indexingIsRunning = false;
        STOPPED_SITES.addAll(createdSites);
        MAIN_THREADS.forEach((site, thread) -> thread.interrupt());
        FORK_JOIN_POOLS.forEach((site, fjp) -> fjp.shutdownNow());
        for (Site site : createdSites) {
            deleteSiteInfo(site, true);
        }
        clearAllMapsAndListsAfterPreviousIndexing();
        return new IndexingSuccessResponse(true);
    }
    private void parseAndIndexSite(Site site) throws CancellationException {
        ForkJoinPool forkJoinPool = new ForkJoinPool(12);
        PageExtractorServiceImpl task = new PageExtractorServiceImpl(site.getUrl(), site);
        savePagesEvery200pages(task);
        FORK_JOIN_POOLS.put(site, forkJoinPool);
        forkJoinPool.invoke(task);
        if (!forkJoinPool.isShutdown() || !Thread.currentThread().isInterrupted()) {
            task.savePages();
            ArrayList<Long> pagesId = pageRepository.getPagesIdBySiteId(site.getId());
            ArrayList<ArrayList<Long>> pageIdListBatches = getPageIdListBatches(pagesId);
            for (ArrayList<Long> batch : pageIdListBatches) {
                createLemmasAndIndexes(batch, site);
            }
        } else {
            if (!STOPPED_SITES.contains(site)) {
                SITE_ERROR.put(site, true);
            }
        }
    }

    private void clearAllMapsAndListsAfterPreviousIndexing() {
        MAIN_THREADS.clear();
        FORK_JOIN_POOLS.clear();
        STOPPED_SITES.clear();
        SITE_ERROR.clear();
        lemmasToSave.clear();
        PageExtractorServiceImpl.links.clear();
        PageExtractorServiceImpl.pageList.clear();
    }

    private boolean isIndexingIsRunning() {
        return MAIN_THREADS.values().stream().anyMatch(Thread::isAlive);
    }

    private void updateIndexesAndLemmasWhenPageIsPresent(Map<String, Integer> lemmaCountMapFromNewPage, List<Index> oldPageIndexes) {
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
    }

    @SuppressWarnings("all")
    private void updateIndexesAndLemmasWhenPageIsNotPresent(String lemmaString, Integer count, List<Index> newIndexes, Long pageId, Site site) {
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
    }

    /*Deletes site from DB*/
    private void deleteSiteInfo(Site site, boolean deleteAll) {
        if (deleteAll) {
            indexRepository.deleteAllIndexes();
            lemmaRepository.deleteAllLemmas();
            pageRepository.deleteAll();
            return;
        }
        indexRepository.deleteBySiteId(site.getId());
        lemmaRepository.deleteAllBySiteId(site.getId());
        pageRepository.deleteBySiteId(site.getId());
    }

    /*Creates model.Site objects from simple config.Site objects*/
    private List<Site> createNewSites() {
        List<Site> createdSites = new ArrayList<>();
        for (searchengine.config.Site site : IndexingServiceImpl.sites) {
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
            if (stopIndexing || STOPPED_SITES.contains(currentSite)) {
                currentSite.setLastError("Indexing is stopped by user");
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

    private String getBaseUrl(String url) {
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
            return;
        }
        long siteId = site.getId();
        List<Page> savedPages = new ArrayList<>();
        batch.forEach(pageId -> savedPages.add(pageRepository.findById(pageId)));
        List<Index> indexList = new ArrayList<>();

        for (Page page : savedPages) {
            long pageId = page.getId();
            Map<String, Integer> lemma_Count = lemmaFinderServiceImpl.collectLemmas(page.getContent());

            lemma_Count.forEach((lemmaString, count) -> {
                Lemma lemma = new Lemma(siteId, lemmaString, 1);
                if (lemmasToSave.contains(lemma)) {
                    lemmasToSave.stream().filter(lemma1 -> lemma1.equals(lemma)).forEach(lemma1 -> {
                        lemma1.incrementFrequency();
                        lemma.setId(lemma1.getId());
                    });
                } else {
                    Lemma savedLemma = lemmaRepository.save(lemma);
                    lemma.setId(savedLemma.getId());
                    lemmasToSave.add(savedLemma);
                }
                Index index = new Index(pageId, lemma.getId(), count);
                indexList.add(index);
            });
        }
        lemmaRepository.saveAll(lemmasToSave);
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

    @SuppressWarnings("all")
    private void awaitThreadFinish() {
        while (MAIN_THREADS.values().stream().anyMatch(Thread::isAlive)) {
            System.out.println("Waiting main threads to finish");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        indexingIsRunning = false;
    }

    @SuppressWarnings("all")
    private void savePagesEvery200pages(PageExtractorServiceImpl task) {
        Thread currentThread = Thread.currentThread();
        Thread thread = new Thread(() -> {
            while (!currentThread.isInterrupted()) {
                if (!currentThread.isAlive()) {
                    break;
                }
                System.out.println("Page list size: " + PageExtractorServiceImpl.pageList.size() + " from thread: " + Thread.currentThread().getName());
                System.out.println("Link list size: " + PageExtractorServiceImpl.links.size() + " from thread: " + Thread.currentThread().getName());
                if (PageExtractorServiceImpl.pageList.size() >= 200) {
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
