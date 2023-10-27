package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchErrorResult;
import searchengine.dto.search.SearchResult;
import searchengine.dto.search.SearchSuccessResult;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final LemmaFinderService lemmaFinderService;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final SnippetFinderImpl snippetFinder;
    private final Map<Page, Double> pageRelevance = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Double> relevanceList = new CopyOnWriteArrayList<>();
    private final Set<Long> pagesListIds = new HashSet<>();
    private Thread preparePageRelevanceThread;
    private String lastQuery;

    @Override
//    @SuppressWarnings("all")
    public SearchResult search(String query, String site, int offset, int limit) {
        Site savedSite = siteRepository.findByUrlStartingWith(site);
        Map<String, Integer> lemmasFromQuery = lemmaFinderService.collectLemmas(query);
        Set<Lemma> sortedLemmaSetAfterExcluding = excludeHighFrequencyLemmas(lemmasFromQuery, savedSite);
        int safeLimit = offset + limit;
        if (query.equalsIgnoreCase(lastQuery)) {
            if (!pageRelevance.isEmpty() && safeLimit < pageRelevance.size()) {
                return getSearchResult(sortedLemmaSetAfterExcluding, offset, safeLimit);
            }
            if (!pageRelevance.isEmpty() && (offset + limit) > pageRelevance.size()) {
                return checkPageRelevance(offset, limit, sortedLemmaSetAfterExcluding);
            }
        }

        lastQuery = query;
        System.out.println("sortedLemmaSetAfterExcluding: " + sortedLemmaSetAfterExcluding.size());
        long rarestLemmaId;
        try {
            rarestLemmaId = sortedLemmaSetAfterExcluding.iterator().next().getId();
        } catch (NoSuchElementException e) {
            return new SearchErrorResult(false, "Lemmas from this query were not found on the website, or the site has not yet been indexed");
        }
        List<Page> pages;
        if (savedSite != null)
            pages = indexRepository.findPagesByLemmaIdAndSiteId(rarestLemmaId, savedSite.getId());
        else
            pages = indexRepository.findPagesByLemmaId(rarestLemmaId);

        System.out.println("pages size: " + pages.size());
//        Map<Page, Double> pageRelevance = new TreeMap<>();
        TreeSet<Lemma> lemmaSetWithoutFirstElement = new TreeSet<>();
        sortedLemmaSetAfterExcluding.stream().skip(1).forEach(lemmaSetWithoutFirstElement::add);
        System.out.println("lemmaSetWithoutFirstElement: " + lemmaSetWithoutFirstElement);

        preparePageRelevanceThread = new Thread(() -> {
            int count = 1;
            for (Page page : pages) {
                double absoluteRelevance = 0;
                System.out.println("lemmaSetWithoutFirstElement inside forEach: " + lemmaSetWithoutFirstElement.size());
                for (Lemma currentLemma : lemmaSetWithoutFirstElement) {
                    Index index = indexRepository.findByPageIdAndLemmaId(page.getId(), currentLemma.getId());
                    System.out.println("Is index null? " + (index == null));
                    if (index != null) {
                        absoluteRelevance += index.getRank();
                    }
                }
                if (absoluteRelevance > 0) {
                    System.out.println("if (absoluteRelevance > 0) " + absoluteRelevance);
                    System.out.println("count is: " + count++);
                    synchronized (pageRelevance) {
                        page.setRelevance(absoluteRelevance);
                        System.out.println(page.getId() + " relevance: " + page.getRelevance());
                        pageRelevance.put(page, absoluteRelevance);
                        System.err.println(pageRelevance.size());
                        pagesListIds.add(page.getId());
                    }
                }
            }
        });
        preparePageRelevanceThread.start();
        while (preparePageRelevanceThread.isAlive()) {

        }
//        for (Page page: pages) {
//            double absoluteRelevance = 0;
//            System.out.println("lemmaSetWithoutFirstElement inside forEach: " + lemmaSetWithoutFirstElement.size());
//            for (Lemma currentLemma : lemmaSetWithoutFirstElement) {
//                Index index = indexRepository.findByPageIdAndLemmaId(page.getId(), currentLemma.getId());
//                System.out.println("Is index null? " + (index == null));
//                if (index != null) {
//                    absoluteRelevance += index.getRank();
//                }
//            }
//            if (absoluteRelevance > 0) {
//                System.out.println("if (absoluteRelevance > 0) " + absoluteRelevance);
//                pageRelevance.put(absoluteRelevance, page);
//            }
//        }
//        System.out.println("pagesAfterExcluding : " + pagesAfterExcluding.size());
        SearchResult searchResult = checkPageRelevance(offset, limit, sortedLemmaSetAfterExcluding);
        System.out.println("pageRelevance.size(): " + pageRelevance.size());
//        pagesListIds.forEach(p -> System.out.println("Page id: " + p));
        return searchResult;
    }

    @NotNull
    private SearchResult checkPageRelevance(int offset, int limit, Set<Lemma> sortedLemmaSetAfterExcluding) {
        int safeLimit;
        while ((offset + limit) > pageRelevance.size() && preparePageRelevanceThread.isAlive()) {
            System.out.println("pages size: " + pageRelevance.size());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Waiting");
        }
        System.out.println("if ((offset + limit) > pageRelevance.size() && !preparePageRelevanceThread.isAlive())" + ((offset + limit) > pageRelevance.size() && !preparePageRelevanceThread.isAlive()));
        if (limit > (pageRelevance.size() - offset) && !preparePageRelevanceThread.isAlive()) {
            safeLimit = pageRelevance.size();
            return getSearchResult(sortedLemmaSetAfterExcluding, offset, safeLimit);
        } else if (limit < (pageRelevance.size() - offset) && !preparePageRelevanceThread.isAlive()) {
            return getSearchResult(sortedLemmaSetAfterExcluding, offset, limit);
        } else {
            return new SearchSuccessResult(true, 0, new ArrayList<>());
        }
    }

    @NotNull
    private SearchResult getSearchResult(Set<Lemma> sortedLemmaSetAfterExcluding, int offset, int safeLimit) {
        List<SearchData> result = new ArrayList<>();
        ArrayList<Double> sortedRelevanceList = new ArrayList<>(pageRelevance.values());
        ArrayList<Page> sortedPageList = new ArrayList<>(pageRelevance.keySet());
        for (int i = offset; i < safeLimit; i++) {
            Site currentSite = sortedPageList.get(i).getSite();
            Page currentPage = sortedPageList.get(i);
            double currentRelevance = sortedRelevanceList.get(i);
            Document document = Jsoup.parse(currentPage.getContent());
            String title = document.title();
            String snippet = snippetFinder.findSnippet(sortedLemmaSetAfterExcluding, currentPage).get(0);
            result.add(new SearchData(currentSite.getUrl(), currentSite.getName(), currentPage.getPath().replace(currentSite.getUrl(), ""), title, snippet, currentRelevance));

        }
        return new SearchSuccessResult(true, pageRelevance.size(), result);
    }

    private Set<Lemma> excludeHighFrequencyLemmas(Map<String, Integer> lemmaMap, Site site) {
        System.out.println("is Site is null: " + (site == null));
        Set<Lemma> lemmaSetAfterExcluding = new TreeSet<>();
        lemmaMap.forEach((lemmaFromQuery, count) -> {
            Optional<Lemma> lemmaOptional;
            if (site == null) {
                System.out.println("if (savedSite == null)");
                lemmaOptional = lemmaRepository.findByLemmaEquals(lemmaFromQuery);
            } else {
                System.out.println("else");
                lemmaOptional = lemmaRepository.findByLemmaAndSiteId(lemmaFromQuery, site.getId());
            }
            Lemma savedLemma;
            if (lemmaOptional.isPresent()) {
                System.out.println("if (lemmaOptional.isPresent())");
                savedLemma = lemmaOptional.get();
                if (savedLemma.getFrequency() < 200) {
                    System.out.println("lemmaSetAfterExcluding: " + lemmaSetAfterExcluding);
                    System.out.println("lemma that will be added: " + savedLemma);
                    lemmaSetAfterExcluding.add(savedLemma);
                    System.out.println("savedLemma added");
                    System.out.println("lemmaSetAfterExcluding after: " + lemmaSetAfterExcluding);
                }
                System.out.println("savedLemma.getFrequency(): " + savedLemma.getLemma() + " " + savedLemma.getFrequency());
            }
            System.out.println("Lezddfpsmdd;lg " + lemmaSetAfterExcluding.size());
        });
        System.out.println("lemmaSetAfterExcluding: " + lemmaSetAfterExcluding.size());
        return lemmaSetAfterExcluding;
    }

    public static void main(String[] args) {
        Set<Lemma> lemmaSetAfterExcluding = new TreeSet<>();
        Lemma lemma1 = new Lemma(123,"устройство",85);
        lemma1.setId(29215);
        Lemma lemma2 = new Lemma(123,"зарядный",85);
        lemma2.setId(29261);

        lemmaSetAfterExcluding.add(lemma1);
        lemmaSetAfterExcluding.add(lemma2);

        System.out.println(lemmaSetAfterExcluding);
    }
}
