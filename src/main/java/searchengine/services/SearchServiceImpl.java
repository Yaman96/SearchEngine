package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchErrorResult;
import searchengine.dto.search.SearchResult;
import searchengine.dto.search.SearchSuccessResult;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.SiteRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final LemmaFinderService lemmaFinderService;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SnippetFinderImpl snippetFinder;

    @Override
    public SearchResult search(String query, String site, int offset, int limit) {
        if (query.isEmpty()) {
            return new SearchErrorResult(false, "search query is empty");
        }
        Set<String> stringLemmasFromQuery = lemmaFinderService.getLemmaSet(query);
        Site selectedSite = siteRepository.findByUrlStartingWith(site);
        Set<Lemma> lemmaSetAfterExcluding = excludeHighFrequencyLemmas(stringLemmasFromQuery, selectedSite);
        if (lemmaSetAfterExcluding.isEmpty()) {
            return new SearchErrorResult(false,"query not found on this site");
        }
        Lemma rarestLemma = lemmaSetAfterExcluding.iterator().next();
        System.err.println("[DEBUG] rarestLemma: " + rarestLemma);
        Set<Page> pagesWithTheRarestLemma = findPagesWithTheRarestLemma(rarestLemma, selectedSite);
        System.err.println("[DEBUG] pagesWithTheRarestLemma: " + pagesWithTheRarestLemma);
        Set<Lemma> lemmaSetWithoutTheRarestLemma = lemmaSetAfterExcluding.stream().skip(1).collect(Collectors.toSet());
        System.err.println("[DEBUG] lemmaSetWithoutTheRarestLemma: " + lemmaSetWithoutTheRarestLemma);
        Set<Page> filteredPages = filterPagesWithTheRarestLemma(pagesWithTheRarestLemma, lemmaSetWithoutTheRarestLemma);
        System.err.println("[DEBUG] filteredPages: " + filteredPages);
        if (filteredPages.isEmpty()) {
            return new SearchSuccessResult(true, 0, new HashSet<>());
        }
        Set<Page> pagesWithCalculatedRelevance = calculateRelevance(filteredPages, lemmaSetAfterExcluding);
        System.err.println("[DEBUG] pagesWithCalculatedRelevance: " + pagesWithCalculatedRelevance);
        Set<SearchData> searchDataSet = prepareSearchData(pagesWithCalculatedRelevance, lemmaSetAfterExcluding);

        return prepareSuccessSearchResult(searchDataSet, offset, limit);
    }

    private TreeSet<Lemma> excludeHighFrequencyLemmas(Set<String> stringLemmasFromQuery, Site site) {
        System.err.println("[DEBUG] Site is not selected");
        List<Lemma> savedLemmas = new ArrayList<>();
        for (String stringLemma : stringLemmasFromQuery) {
            if (site == null) {
                Optional<List<Lemma>> lemmaListOptional = lemmaRepository.findAllByLemma(stringLemma);
                lemmaListOptional.ifPresent(savedLemmas::addAll);
            } else {
                Optional<Lemma> lemmaOptional = lemmaRepository.findByLemmaAndSiteId(stringLemma, site.getId());
                lemmaOptional.ifPresent(savedLemmas::add);
            }
        }
        savedLemmas.forEach(lemma -> {
            if (lemma.getFrequency() > 200) savedLemmas.remove(lemma);
        });
        System.err.println("[DEBUG] savedLemmas after excluding high frequency lemmas: " + savedLemmas);
        TreeSet<Lemma> lemmaSetAfterExcluding = new TreeSet<>(savedLemmas);
        System.err.println("[DEBUG] " + lemmaSetAfterExcluding);
        return lemmaSetAfterExcluding;
    }

    private TreeSet<Page> findPagesWithTheRarestLemma(Lemma rarestLemma, Site site) {
        TreeSet<Page> pagesWithTheRarestLemma = new TreeSet<>();

        if (site == null) {
            pagesWithTheRarestLemma.addAll(indexRepository.findPagesByLemmaId(rarestLemma.getId()));
        } else {
            pagesWithTheRarestLemma.addAll(indexRepository.findPagesByLemmaIdAndSiteId(rarestLemma.getId(), site.getId()));
        }

        return pagesWithTheRarestLemma;
    }

    private TreeSet<Page> filterPagesWithTheRarestLemma(Set<Page> pagesWithTheRarestLemma, Set<Lemma> lemmaSetWithoutTheRarestLemma) {
        TreeSet<Page> filteredPages = new TreeSet<>(pagesWithTheRarestLemma);

        for (Lemma lemma : lemmaSetWithoutTheRarestLemma) {
            for (Page page : pagesWithTheRarestLemma) {
                System.err.println("[DEBUG] inside forEach in filterPagesWithTheRarestLemma. filteredPages size: " + filteredPages.size());
                Set<Lemma> lemmasFromCurrentPage = indexRepository.findAllLemmasByPageId(page.getId());
                if (!lemmasFromCurrentPage.contains(lemma)) {
                    filteredPages.remove(page);
                }
            }
        }
        return filteredPages;
    }

    private TreeSet<Page> calculateRelevance(Set<Page> filteredPages, Set<Lemma> lemmaSetAfterExcluding) {
        TreeSet<Page> pagesWithCalculatedRelevance = new TreeSet<>(filteredPages);

        for (Page page : pagesWithCalculatedRelevance) {
            double relevance = 0;
            Set<Lemma> lemmasFromCurrentPage = indexRepository.findAllLemmasByPageId(page.getId());
            for (Lemma lemma : lemmaSetAfterExcluding) {
                if (lemmasFromCurrentPage.contains(lemma)) relevance++;
            }
            page.setRelevance(relevance);
            System.err.println("[DEBUG] Page: " + page.getPath() + " with relevance: " + page.getRelevance());
        }
        return pagesWithCalculatedRelevance;
    }

    private TreeSet<SearchData> prepareSearchData(Set<Page> pagesWithCalculatedRelevance, Set<Lemma> lemmaSetAfterExcluding) {
        TreeSet<SearchData> searchDataSet = new TreeSet<>();
        for (Page page : pagesWithCalculatedRelevance) {
            Document document = Jsoup.parse(page.getContent());

            String site = page.getSite().getUrl();
            String siteName = page.getSite().getName();
            String uri = page.getPath().replace(page.getSite().getUrl(),"");
            System.err.println("[DEBUG] uri: " + uri);
            String title = document.title();
            String snippet = snippetFinder.findSnippet(lemmaSetAfterExcluding, page);
            double relevance = page.getRelevance();

            SearchData searchData = new SearchData(site, siteName, uri, title, snippet, relevance);
            searchDataSet.add(searchData);
            System.err.println("[DEBUG] searchData: " + searchData);
        }
        return searchDataSet;
    }

    private SearchResult prepareSuccessSearchResult(Set<SearchData> searchDataSet, int offset, int limit) {
        if (limit == 0) {
            limit = 20;
        }

        int count = searchDataSet.size();
        System.err.println("searchDataSet.size(): " + count);
        System.err.println("searchDataSet: " + searchDataSet);
        TreeSet<SearchData> data = new TreeSet<>(searchDataSet);

        int safeLimit = limit;

        if ((count - offset) < limit) {
            safeLimit = count;
        }

        TreeSet<SearchData> subData = new TreeSet<>(new ArrayList<>(data).subList(offset, offset + safeLimit));

        return new SearchSuccessResult(true, count, subData);
    }
}
