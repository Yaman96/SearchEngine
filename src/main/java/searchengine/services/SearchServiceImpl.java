package searchengine.services;

import lombok.RequiredArgsConstructor;
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
            return new SearchErrorResult(false,"query not found on this site or the query is too frequent");
        }
        Lemma rarestLemma = lemmaSetAfterExcluding.iterator().next();
        Set<Page> pagesWithTheRarestLemma = findPagesWithTheRarestLemma(rarestLemma, selectedSite);
        Set<Lemma> lemmaSetWithoutTheRarestLemma = lemmaSetAfterExcluding.size() == 1 ? lemmaSetAfterExcluding : lemmaSetAfterExcluding.stream().skip(1).collect(Collectors.toSet());
        Set<Page> filteredPages = filterPagesWithTheRarestLemma(pagesWithTheRarestLemma, lemmaSetWithoutTheRarestLemma, rarestLemma);
        if (filteredPages.isEmpty()) {
            return new SearchSuccessResult(true, 0, new HashSet<>());
        }
        Set<SearchData> searchDataSet = prepareSearchData(filteredPages, lemmaSetAfterExcluding);

        return prepareSuccessSearchResult(searchDataSet, offset, limit);
    }

    private TreeSet<Lemma> excludeHighFrequencyLemmas(Set<String> stringLemmasFromQuery, Site site) {
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
        savedLemmas.removeIf(lemma -> lemma.getFrequency() > 500);
        return new TreeSet<>(savedLemmas);
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

    private TreeSet<Page> filterPagesWithTheRarestLemma(Set<Page> pagesWithTheRarestLemma, Set<Lemma> lemmaSetWithoutTheRarestLemma, Lemma rarestLemma) {
        TreeSet<Page> filteredPages = new TreeSet<>(pagesWithTheRarestLemma);

        for (Lemma lemma : lemmaSetWithoutTheRarestLemma) {
            TreeSet<Page> pagesToRemove = new TreeSet<>();
            for (Page page : filteredPages) {
                double relevance = 0;
                Optional<Index> indexOptional = indexRepository.findByPageIdAndLemmaId(page.getId(), lemma.getId());
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
            Optional<Index> indexOptional = indexRepository.findByPageIdAndLemmaId(page.getId(), rarestLemma.getId());
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
        if (limit == 0) {
            limit = 20;
        }

        int count = searchDataSet.size();
        TreeSet<SearchData> data = new TreeSet<>(searchDataSet);

        int safeLimit = limit;

        if ((count - offset) < limit) {
            safeLimit = count;
        }

        TreeSet<SearchData> subData = new TreeSet<>(new ArrayList<>(data).subList(offset, offset + safeLimit));

        return new SearchSuccessResult(true, count, subData);
    }
}
