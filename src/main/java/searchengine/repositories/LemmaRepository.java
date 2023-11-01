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
    @Query(value = "SELECT l FROM Lemma l WHERE l.lemma = :lemma")
    Optional<List<Lemma>> findAllByLemma(String lemma);
    @Transactional
    Optional<Lemma> findByLemmaAndSiteId(String lemma, long siteId);

    Optional<Lemma> findById(long lemmaId);

    @Transactional
    ArrayList<Lemma> findBySiteId(long siteId);

    @Modifying
    @Transactional
    void deleteAllBySiteId(long siteId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM Lemma l")
    void deleteAllLemmas();

    int countAllBySiteId(long siteId);
}
