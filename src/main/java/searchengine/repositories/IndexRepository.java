package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
public interface IndexRepository extends CrudRepository<Index, Integer> {

    ArrayList<Index> findAllByPageId(long pageId);

    @Query(value = "SELECT l FROM Lemma l " +
            "INNER JOIN Index i ON i.lemmaId = l.id " +
            "WHERE i.pageId = :pageId")
    Set<Lemma> findAllLemmasByPageId(long pageId);

    @Modifying
    @Transactional
    void deleteByPageId(long pageId);

    @Modifying
    @Transactional
    @Query(value = "delete from Index i")
    void deleteAllIndexes();

    ArrayList<Index> findAllByLemmaId(long lemmaId);

    @Transactional
    Index findByPageIdAndLemmaId(long pageId, long lemmaId);

    @Query("SELECT p FROM Page p " +
            "INNER JOIN Index i ON i.pageId = p.id " +
            "WHERE i.lemmaId = :lemmaId AND p.site.id = :siteId")
    List<Page> findPagesByLemmaIdAndSiteId(@Param("lemmaId") long lemmaId, @Param("siteId") long siteId);

    @Query("SELECT p FROM Page p " +
            "INNER JOIN Index i ON i.pageId = p.id " +
            "WHERE i.lemmaId = :lemmaId")
    List<Page> findPagesByLemmaId(@Param("lemmaId") long lemmaId);

    @Query("SELECT i FROM Page p " +
            "INNER JOIN Index i ON i.pageId = p.id " +
            "WHERE i.lemmaId = :lemmaId AND p.site.id = :siteId")
    List<Index> findIndexesByLemmaIdAndSiteId(@Param("lemmaId") long lemmaId, @Param("siteId") long siteId);
}
