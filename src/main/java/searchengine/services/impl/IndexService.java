package searchengine.services.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.Index;
import searchengine.model.Page;
import searchengine.repositories.IndexJdbcRepository;
import searchengine.repositories.IndexRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class IndexService {

    private IndexRepository indexRepository;
    private IndexJdbcRepository indexJdbcRepository;

    public ArrayList<Index> findAllByPageId(long pageId) {
        return indexRepository.findAllByPageId(pageId);
    }

    public Optional<Index> findByPageIdAndLemmaId(long pageId, long lemmaId) {
        return indexRepository.findByPageIdAndLemmaId(pageId, lemmaId);
    }

    public void deleteBySiteId(long siteId) {
        indexRepository.deleteBySiteId(siteId);
    }

    public void deleteAllIndexes() {
        indexRepository.deleteAllIndexes();
    }

    public List<Page> findPagesByLemmaIdAndSiteId(long lemmaId, long siteId) {
        return indexRepository.findPagesByLemmaIdAndSiteId(lemmaId, siteId);
    }

    public List<Page> findPagesByLemmaId(long lemmaId) {
        return indexRepository.findPagesByLemmaId(lemmaId);
    }

    public void save(Index index) {
        indexRepository.save(index);
    }

    public void saveAll(List<Index> indexes) {
        indexRepository.saveAll(indexes);
    }

    public void executeSql(String sql) {
        indexJdbcRepository.executeSql(sql);
    }
}
