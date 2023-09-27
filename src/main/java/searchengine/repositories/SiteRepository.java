package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Site;

@Repository
public interface SiteRepository extends CrudRepository<Site,Long> {

    Site findByNameContainsIgnoreCase(String siteName);
    boolean deleteByNameContainsIgnoreCase(String siteName);
    void deleteById(long siteId);
}
