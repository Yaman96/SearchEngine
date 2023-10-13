package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;

import java.util.List;

@Repository
public interface LemmaRepository extends CrudRepository<Lemma,Integer> {

    Lemma findByLemmaEquals(String lemma);
    default void saveOrUpdateAll(List<Lemma> lemmaList) {
        lemmaList.forEach(l -> {
            Lemma lemma = findByLemmaEquals(l.getLemma());
            if(lemma != null) {
                lemma.setFrequency(lemma.getFrequency() + l.getFrequency());
                save(lemma);
            } else {
                save(l);
            }
        });
    }
}
