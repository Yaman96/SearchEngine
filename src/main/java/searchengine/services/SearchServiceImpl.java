package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResult;
import searchengine.dto.search.SearchSuccessResult;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final LemmaFinderService lemmaFinderService;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final SnippetFinderImpl snippetFinder;

    @Override
    public SearchResult search(String query, String site, int offset, int limit) {
        Set<String> stringLemmasFromQuery = lemmaFinderService.getLemmaSet(query);
        Site selectedSite = siteRepository.findByUrlStartingWith(site);
        Set<Lemma> lemmaSetAfterExcluding = excludeHighFrequencyLemmas(stringLemmasFromQuery, selectedSite);
        Lemma rarestLemma = lemmaSetAfterExcluding.iterator().next();
        System.err.println("[DEBUG] rarestLemma: " + rarestLemma);
        Set<Page> pagesWithTheRarestLemma = findPagesWithTheRarestLemma(rarestLemma,selectedSite);
        System.err.println("[DEBUG] pagesWithTheRarestLemma: " + pagesWithTheRarestLemma);
        Set<Lemma> lemmaSetWithoutTheRarestLemma = lemmaSetAfterExcluding.stream().skip(1).collect(Collectors.toSet());
        System.err.println("[DEBUG] lemmaSetWithoutTheRarestLemma: " + lemmaSetWithoutTheRarestLemma);
        Set<Page> filteredPages = filterPagesWithTheRarestLemma(pagesWithTheRarestLemma,lemmaSetWithoutTheRarestLemma);
        System.err.println("[DEBUG] filteredPages: " + filteredPages);
        if(filteredPages.isEmpty()) {
            return new SearchSuccessResult(true,0, new ArrayList<>());
        }
        Set<Page> pagesWithCalculatedRelevance = calculateRelevance(filteredPages,lemmaSetAfterExcluding);
        System.err.println("[DEBUG] pagesWithCalculatedRelevance: " + pagesWithCalculatedRelevance);
        SearchResult searchResult;

        return null;
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
            for(Page page : pagesWithTheRarestLemma) {
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

        for(Page page : pagesWithCalculatedRelevance) {
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

    private SearchResult prepareSearchResult(Set<Page> pagesWithCalculatedRelevance, Set<Lemma> lemmaSetAfterExcluding) {
        return null;
    }

}
