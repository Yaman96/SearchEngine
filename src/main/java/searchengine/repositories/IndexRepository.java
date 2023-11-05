package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public interface IndexRepository extends CrudRepository<Index, Integer> {

    ArrayList<Index> findAllByPageId(long pageId);
    @Query(value = "SELECT i FROM Index i WHERE i.pageId = :pageId AND i.lemmaId = :lemmaId")
    Optional<Index> findByPageIdAndLemmaId(long pageId, long lemmaId);
    @Modifying
    @Transactional
    @Query(value = "DELETE i FROM index_1 i JOIN page p ON i.page_id = p.id WHERE p.site_id = :siteId", nativeQuery = true)
    void deleteBySiteId(long siteId);
    @Modifying
    @Transactional
    @Query(value = "delete from Index i")
    void deleteAllIndexes();
    @Query("SELECT p FROM Page p " +
            "INNER JOIN Index i ON i.pageId = p.id " +
            "WHERE i.lemmaId = :lemmaId AND p.site.id = :siteId")
    List<Page> findPagesByLemmaIdAndSiteId(@Param("lemmaId") long lemmaId, @Param("siteId") long siteId);
    @Query("SELECT p FROM Page p " +
            "INNER JOIN Index i ON i.pageId = p.id " +
            "WHERE i.lemmaId = :lemmaId")
    List<Page> findPagesByLemmaId(@Param("lemmaId") long lemmaId);
}
