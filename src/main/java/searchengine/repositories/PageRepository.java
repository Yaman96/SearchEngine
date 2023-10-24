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
import java.util.List;

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

    @Transactional
    int deleteById(long pageId);

    Page findById(long pageId);

    @Transactional
    @Query("SELECT p.id FROM Page p WHERE p.site.id = :siteId")
    ArrayList<Long> getPagesIdBySiteId(@Param("siteId") long siteId);

    @Transactional
    default ArrayList<Long> getPagesIdBySiteIdWithThreadCheck(@Param("siteId") long siteId) {
        if(!Thread.currentThread().isInterrupted()) {
            return getPagesIdBySiteId(siteId);
        }
        System.out.println("inside getPagesIdBySiteIdWithThreadCheck() Current thread is interrupted");
        return null;
    }
}
