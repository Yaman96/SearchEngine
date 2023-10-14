package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;

import java.util.ArrayList;
import java.util.List;

@Repository
public interface IndexRepository extends CrudRepository<Index,Integer> {

    ArrayList<Index> findAllByPageId(long pageId);

    @Transactional
    void deleteByPageId(long pageId);
}
