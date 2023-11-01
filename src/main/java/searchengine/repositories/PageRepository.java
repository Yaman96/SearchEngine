package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.ArrayList;

@Repository
public interface PageRepository extends CrudRepository<Page, Integer> {

    @Modifying
    @Transactional
    void deleteAllBySiteIs(Site site);

    @Modifying
    @Transactional
    @Query(value = "delete from Page p")
    void deleteAll();

    Page findByPath(String path);

    Page findById(long pageId);

    @Transactional
    @Query("SELECT p.id FROM Page p WHERE p.site.id = :siteId AND p.code >= 200 AND p.code < 300")
    ArrayList<Long> getPagesIdBySiteId(@Param("siteId") long siteId);

    int countAllBySiteId(long siteId);
}
