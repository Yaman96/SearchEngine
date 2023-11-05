package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;

    @Override
    public StatisticsResponse getStatistics() {

        Iterable<searchengine.model.Site> siteIterable = siteRepository.findAll();
        int siteCount = 0;
        List<searchengine.model.Site> siteList = new ArrayList<>();
        for (searchengine.model.Site site : siteIterable) {
            siteCount++;
            siteList.add(site);
        }

        TotalStatistics total = new TotalStatistics();
        total.setSites(siteCount);
        total.setIndexing(isSiteIndexing(siteList));

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        for (Site site : siteList) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            int pages = pageRepository.countAllBySiteId(site.getId());
            int lemmas = lemmaRepository.countAllBySiteId(site.getId());
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(site.getStatus());
            if (!site.getLastError().isEmpty()) {
                item.setError(site.getLastError());
            }
            item.setStatusTime(site.getStatusTime().toInstant(ZoneOffset.UTC).toEpochMilli());
            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    private boolean isSiteIndexing(List<searchengine.model.Site> siteList) {
        return siteList.stream().anyMatch(site -> site.getStatus().equals(Status.INDEXING.toString()));
    }
}
