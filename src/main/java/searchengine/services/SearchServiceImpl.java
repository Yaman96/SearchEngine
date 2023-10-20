package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResult;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.*;

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
        Set<Lemma> sortedLemmaSetAfterExcluding = excludeHighFrequencyLemmas(lemmasFromQuery);
        List<Page> pages = new ArrayList<>(indexRepository.findAllByLemmaId(sortedLemmaSetAfterExcluding.iterator().next().getId())
                .stream().map(Index::getPageId).map(pageRepository::findById).toList());
        sortedLemmaSetAfterExcluding.stream().skip(1).forEach(lemma -> {
            for (Page page : pages) {
                ArrayList<Index> indexes = indexRepository.findAllByPageId(page.getId());
                if(indexes.size() > 0) {
                    if(indexes.stream().noneMatch(currentIndex -> currentIndex.getLemmaId() == lemma.getId())) {
                        pages.remove(page);
                    }
                }
            }
        });
        return null;
    }

    private Set<Lemma> excludeHighFrequencyLemmas(Map<String, Integer> lemmaMap) {
        Set<Lemma> lemmaSetAfterExcluding = new TreeSet<>();
        lemmaMap.forEach((lemmaFromQuery, count) -> {
            Optional<Lemma> lemmaOptional = lemmaRepository.findByLemmaEquals(lemmaFromQuery);
            Lemma savedLemma;
            if (lemmaOptional.isPresent()) {
                savedLemma = lemmaOptional.get();
                if (savedLemma.getFrequency() < 50) lemmaSetAfterExcluding.add(savedLemma);
            }
        });
        return lemmaSetAfterExcluding;
    }
}
