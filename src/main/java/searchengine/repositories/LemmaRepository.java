package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends CrudRepository<Lemma,Integer> {

    Optional<Lemma> findByLemmaEquals(String lemma);

    Optional<Lemma> findById(long lemmaId);
    default void saveOrUpdateAll(List<Lemma> lemmaList) {
        lemmaList.forEach(l -> {
            Optional<Lemma> lemmaOptional = findByLemmaEquals(l.getLemma());
            if(lemmaOptional.isPresent()) {
                Lemma lemma = lemmaOptional.get();
                lemma.setFrequency(lemma.getFrequency() + l.getFrequency());
                save(lemma);
            } else {
                save(l);
            }
        });
    }

    @Transactional
    ArrayList<Lemma> findBySiteId(long siteId);

    @Transactional
    void deleteAllBySiteId(long siteId);
}
