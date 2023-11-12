package searchengine.services.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.Page;
import searchengine.repositories.PageRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class PageService {

    private PageRepository pageRepository;

    public void deleteBySiteId(long siteId) {
        pageRepository.deleteBySiteId(siteId);
    }

    public void deleteAll() {
        pageRepository.deleteAll();
    }

    public Page findByPath(String path) {
        return pageRepository.findByPath(path);
    }

    public Page findById(long pageId) {
        return pageRepository.findById(pageId);
    }

    public ArrayList<Long> getPagesIdBySiteId(long siteId) {
        return pageRepository.getPagesIdBySiteId(siteId);
    }

    public int countAllBySiteId(long siteId) {
        return pageRepository.countAllBySiteId(siteId);
    }

    public void saveAll(List<Page> pages) {
        pageRepository.saveAll(pages);
    }

    public void save(Page page) {
        pageRepository.save(page);
    }
}
