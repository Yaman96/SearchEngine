package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

@Repository
public interface PageRepository extends CrudRepository<Page,Integer> {
    void deleteAllBySiteIs(Site site);
    Page findByPath(String path);
}
