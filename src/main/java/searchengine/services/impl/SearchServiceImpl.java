package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import searchengine.services.LemmaFinderService;
import searchengine.services.SearchService;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final LemmaFinderService lemmaFinderService;
    private final SiteService siteService;
    private final LemmaService lemmaService;
    private final IndexService indexService;
    private final SnippetFinderImpl snippetFinder;
    private final Logger debugLogger = LogManager.getLogger("debugSearchServiceLogger");
    private final int LEMMA_PERCENT = 1;

    @Override
    public SearchResult search(String query, String site, int offset, int limit) {
        if (query.isEmpty())
            return new SearchErrorResult(false, "search query is empty");

        Set<String> stringLemmasFromQuery = lemmaFinderService.getLemmaSet(query);
        Site selectedSite = siteService.findByUrlStartingWith(site);
        Set<Lemma> lemmaSetAfterExcluding = excludeHighFrequencyLemmas(stringLemmasFromQuery, selectedSite);
        debugLogger.debug("Query: {},\n Query lemmas are: {},\n Selected site: {},\n Query lemmas after excluding high frequency lemmas: {}",
                query, stringLemmasFromQuery, site, lemmaSetAfterExcluding);

        if (lemmaSetAfterExcluding.isEmpty())
            return new SearchErrorResult(false,"query not found on this site or the query is too frequent");

        Lemma rarestLemma = lemmaSetAfterExcluding.iterator().next();
        Set<Page> pagesWithTheRarestLemma = findPagesWithTheRarestLemma(rarestLemma, selectedSite);
        Set<Lemma> lemmaSetWithoutTheRarestLemma = lemmaSetAfterExcluding.size() == 1 ? lemmaSetAfterExcluding : lemmaSetAfterExcluding.stream().skip(1).collect(Collectors.toSet());
        Set<Page> filteredPages = filterPagesWithTheRarestLemma(pagesWithTheRarestLemma, lemmaSetWithoutTheRarestLemma, rarestLemma);
        debugLogger.debug("The rarest lemma is: {}, \nPages that contain the rarest lemma: {}, \nLemmas without the rarest lemma: {}, \nPages excluding those that do not contain the rest lemmas: {}.", rarestLemma, pagesWithTheRarestLemma, lemmaSetWithoutTheRarestLemma, filteredPages);

        if (filteredPages.isEmpty())
            return new SearchSuccessResult(true, 0, new HashSet<>());

        Set<SearchData> searchDataSet = prepareSearchData(filteredPages, lemmaSetAfterExcluding);

        return prepareSuccessSearchResult(searchDataSet, offset, limit);
    }

    private TreeSet<Lemma> excludeHighFrequencyLemmas(Set<String> stringLemmasFromQuery, Site site) {
        List<Lemma> savedLemmas = new ArrayList<>();
        int maxFrequency = site == null ? getMaxFrequency(null) : getMaxFrequency(site.getId());
        for (String stringLemma : stringLemmasFromQuery) {
            if (site == null) {
                Optional<List<Lemma>> lemmaListOptional = lemmaService.findAllByLemmaWithFrequencyLessThan(stringLemma, maxFrequency);
                lemmaListOptional.ifPresent(savedLemmas::addAll);
            } else {
                Optional<Lemma> lemmaOptional = lemmaService.findByLemmaAndSiteIdAndFrequencyLessThan(stringLemma, site.getId(), maxFrequency);
                lemmaOptional.ifPresent(savedLemmas::add);
            }
        }
        return new TreeSet<>(savedLemmas);
    }

    private TreeSet<Page> findPagesWithTheRarestLemma(Lemma rarestLemma, Site site) {
        TreeSet<Page> pagesWithTheRarestLemma = new TreeSet<>();

        if (site == null) {
            pagesWithTheRarestLemma.addAll(indexService.findPagesByLemmaId(rarestLemma.getId()));
        } else {
            pagesWithTheRarestLemma.addAll(indexService.findPagesByLemmaIdAndSiteId(rarestLemma.getId(), site.getId()));
        }

        return pagesWithTheRarestLemma;
    }

    private TreeSet<Page> filterPagesWithTheRarestLemma(Set<Page> pagesWithTheRarestLemma, Set<Lemma> lemmaSetWithoutTheRarestLemma, Lemma rarestLemma) {
        TreeSet<Page> filteredPages = new TreeSet<>(pagesWithTheRarestLemma);
        if (lemmaSetWithoutTheRarestLemma.size() == 1) {
            return filteredPages;
        }

        for (Lemma lemma : lemmaSetWithoutTheRarestLemma) {
            TreeSet<Page> pagesToRemove = new TreeSet<>();
            for (Page page : filteredPages) {
                double relevance = 0;
                Optional<Index> indexOptional = indexService.findByPageIdAndLemmaId(page.getId(), lemma.getId());
                if (indexOptional.isEmpty()) {
                    pagesToRemove.add(page);
                } else {
                    Index index = indexOptional.get();
                    relevance += index.getRank();
                }
                page.setRelevance(relevance);
            }
            filteredPages.removeAll(pagesToRemove);
        }
        for (Page page : filteredPages) {
            Optional<Index> indexOptional = indexService.findByPageIdAndLemmaId(page.getId(), rarestLemma.getId());
            if (indexOptional.isPresent()) {
                Index index = indexOptional.get();
                page.setRelevance(page.getRelevance()+index.getRank());
            }
        }
        return filteredPages;
    }

    private TreeSet<SearchData> prepareSearchData(Set<Page> pagesWithCalculatedRelevance, Set<Lemma> lemmaSetAfterExcluding) {
        TreeSet<SearchData> searchDataSet = new TreeSet<>();
        for (Page page : pagesWithCalculatedRelevance) {
            Document document = Jsoup.parse(page.getContent());

            String site = page.getSite().getUrl();
            String siteName = page.getSite().getName();
            String uri = page.getPath().replace(page.getSite().getUrl(),"");
            String title = document.title();
            String snippet = snippetFinder.findSnippet(lemmaSetAfterExcluding, page);
            double relevance = page.getRelevance();

            SearchData searchData = new SearchData(site, siteName, uri, title, snippet, relevance);
            searchDataSet.add(searchData);
        }
        return searchDataSet;
    }

    private SearchResult prepareSuccessSearchResult(Set<SearchData> searchDataSet, int offset, int limit) {

        int count = searchDataSet.size();
        TreeSet<SearchData> data = new TreeSet<>(searchDataSet);

        int safeLimit = limit;

        if ((count - offset) < limit) {
            safeLimit = count;
        }

        TreeSet<SearchData> subData = new TreeSet<>(new ArrayList<>(data).subList(offset, safeLimit));

        return new SearchSuccessResult(true, count, subData);
    }

    private int getMaxFrequency(Long siteId) {
        int frequencySum;
        if (siteId == null) {
            frequencySum = lemmaService.getFrequencySum();
        } else {
            frequencySum = lemmaService.getFrequencySum(siteId);
        }
        return frequencySum * LEMMA_PERCENT / 100;
    }
}
