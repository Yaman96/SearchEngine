package searchengine.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Site;

@Repository
public interface SiteRepository extends CrudRepository<Site,Long> {

    @Transactional
    Site findByNameContainsIgnoreCase(String siteName);

    @Transactional
    Site findByUrlStartingWith(String url);

    @Query(value = "SELECT s FROM Site s WHERE s.status = 'INDEXED'")
    Page<Site> findAnyIndexedSite(Pageable pageable);
}
