package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;

import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends CrudRepository<Lemma,Integer> {

    @Transactional
    Optional<Lemma> findByLemmaEquals(String lemma);

    @Transactional
    @Query(value = "SELECT l FROM Lemma l WHERE l.lemma = :lemma AND l.frequency < :frequency")
    Optional<List<Lemma>> findAllByLemmaWithFrequencyLessThan(String lemma, int frequency);
    @Transactional
    Optional<Lemma> findByLemmaAndSiteIdAndFrequencyLessThan(String lemma, long siteId, int frequency);

    Optional<Lemma> findById(long lemmaId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM Lemma l WHERE l.siteId = :siteId")
    void deleteAllBySiteId(long siteId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM Lemma l")
    void deleteAllLemmas();

    int countAllBySiteId(long siteId);

    @Query(value = "SELECT SUM(l.frequency) FROM Lemma l where l.siteId = :siteId")
    int getFrequencySum(long siteId);
    @Query(value = "SELECT SUM(l.frequency) FROM Lemma l")
    int getFrequencySum();
}
