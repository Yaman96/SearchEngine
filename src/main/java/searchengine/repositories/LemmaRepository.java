package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends CrudRepository<Lemma,Integer> {

    @Transactional
    Optional<Lemma> findByLemmaEquals(String lemma);
    @Transactional
    Optional<Lemma> findByLemmaAndSiteId(String lemma, long siteId);

    Optional<Lemma> findById(long lemmaId);

    @Transactional
    default List<Long> saveOrUpdateAll(List<Lemma> lemmaList) {
        List<Long> savedLemmaIds = new ArrayList<>();
        lemmaList.forEach(l -> {
            savedLemmaIds.add(saveOrUpdate(l));
        });
        return savedLemmaIds;
    }

    @Transactional
    default Long saveOrUpdate(Lemma lemma) {
        Optional<Lemma> lemmaOptional = findByLemmaEquals(lemma.getLemma());
        Lemma savedLemma;
        if(lemmaOptional.isPresent()) {
            savedLemma = lemmaOptional.get();
            savedLemma.setFrequency(savedLemma.getFrequency() + lemma.getFrequency());
            savedLemma = save(savedLemma);
        } else {
            savedLemma = save(lemma);
        }
        return savedLemma.getId();
    }

    @Transactional
    ArrayList<Lemma> findBySiteId(long siteId);

    @Modifying
    @Transactional
    void deleteAllBySiteId(long siteId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM Lemma l")
    void deleteAllLemmas();
}
