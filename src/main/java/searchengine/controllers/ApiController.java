package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.indexing.IndexingErrorResponse;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingResponseServiceImpl;
import searchengine.services.IndexingService;
import searchengine.services.IndexingServiceImpl;
import searchengine.services.StatisticsService;

import java.util.ArrayList;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;

    private final IndexingService indexingService;

    private ArrayList<IndexingResponse> results = new ArrayList<>();

    public ApiController(StatisticsService statisticsService, IndexingService indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing() {
        return ResponseEntity.ok(indexingService.startIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexPage(@RequestParam String url) {
        return ResponseEntity.ok(indexingService.indexPage(url));
    }
}
