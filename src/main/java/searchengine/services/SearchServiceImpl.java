package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchData;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final LemmaFinderService lemmaFinderService;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;

    @Override
    public List<SearchResult> search(String query, String site, int offset, int limit) {
        Map<String, Integer> lemmasFromQuery = lemmaFinderService.collectLemmas(query);
        Set<Lemma> sortedLemmaSetAfterExcluding = excludeHighFrequencyLemmas(lemmasFromQuery, site);
        List<Page> pages = new ArrayList<>(indexRepository.findAllByLemmaId(sortedLemmaSetAfterExcluding.iterator().next().getId())
                .stream().map(Index::getPageId).map(pageRepository::findById).toList());
        List<Page> pagesAfterExcluding = new ArrayList<>();
        Map<Page,Double> pageRelevance = new LinkedHashMap<>();
        sortedLemmaSetAfterExcluding.stream().skip(1).forEach(lemma -> {
            for (Page page : pages) {
                ArrayList<Index> indexes = indexRepository.findAllByPageId(page.getId());
                if (indexes != null && !indexes.isEmpty()) {
                    double absoluteRelevance = 0;
                    for (Index index : indexes) {
                        if(index.getLemmaId() == lemma.getId()) {
                            pagesAfterExcluding.add(page);
                            absoluteRelevance += index.getRank();
                        }
                    }
                    pageRelevance.put(page, absoluteRelevance);
                }
            }
        });
        if(pagesAfterExcluding.isEmpty()) {
            return null;
        } else {
            LinkedHashMap<Page, Double> sortedByValues = pageRelevance.entrySet()
                    .stream()
                    .sorted(Map.Entry.<Page,Double>comparingByValue().reversed())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    ));
            List<SearchData> result = new ArrayList<>();
            sortedByValues.forEach((page,relevance) -> {
                Site currentSite = page.getSite();
                Document document = Jsoup.parse(page.getContent());
                String title = document.title();
                String snippet =
                result.add(new SearchData(currentSite.getUrl(), currentSite.getName(), page.getPath().replace(currentSite.getUrl(),""), title,));
            });
        }
        return null;
    }

    private Set<Lemma> excludeHighFrequencyLemmas(Map<String, Integer> lemmaMap, String site) {
        Site savedSite;
        if (site != null && !site.isEmpty() && !site.isBlank()) {
            savedSite = siteRepository.findByUrlStartingWith(site);
        } else savedSite = null;
        Set<Lemma> lemmaSetAfterExcluding = new TreeSet<>();
        lemmaMap.forEach((lemmaFromQuery, count) -> {
            Optional<Lemma> lemmaOptional;
            if(savedSite == null) {
                lemmaOptional = lemmaRepository.findByLemmaEquals(lemmaFromQuery);
            } else {
                lemmaOptional = lemmaRepository.findByLemmaAndSiteId(lemmaFromQuery,savedSite.getId());
            }
            Lemma savedLemma;
            if (lemmaOptional.isPresent()) {
                savedLemma = lemmaOptional.get();
                if (savedLemma.getFrequency() < 50) lemmaSetAfterExcluding.add(savedLemma);
            }
        });
        return lemmaSetAfterExcluding;
    }
}
