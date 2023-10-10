package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;

@Service
public class LinkExtractorService extends RecursiveAction {

    public static CopyOnWriteArraySet<String> links = new CopyOnWriteArraySet<>();
    public static final CopyOnWriteArraySet<Page> pageList = new CopyOnWriteArraySet<>();
    private Site site;
    @Autowired
    private static SiteRepository siteRepository;
    @Autowired
    private static PageRepository pageRepository;
    private Page page;
    private final String USER_AGENT = "Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6";
    private final String REFERER = "https://www.google.com";

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    protected void compute() {
        Set<LinkExtractorService> tasks = new HashSet<>();
        getLinks(tasks);
        tasks.forEach(ForkJoinTask::join);
    }

    private void getLinks(Set<LinkExtractorService> tasks) {
        int responseCode = 418;
        Connection.Response response;
        try {
            Thread.sleep(200);
            response = Jsoup.connect(page.getPath())
                                                .userAgent(USER_AGENT)
                                                .referrer(REFERER)
                                                .execute();
            responseCode = response.statusCode();
            Document document = response.parse();
            Elements elements = document.select("a[href]");

            for (Element element : elements) {
                String link = element.absUrl("href");
                Page currentPage = new Page(link,responseCode,document.html(),site);
                if(isValidPageLink(currentPage)) {
                    LinkExtractorService extractorService = new LinkExtractorService(currentPage,site);
                    extractorService.fork();
                    tasks.add(extractorService);
                    LinkExtractorService.links.add(link);
                    pageList.add(new Page(link.trim(),responseCode,document.html(),site));
                }
            }
        } catch  (IOException | InterruptedException e) {
            pageRepository.save(new Page(page.getPath(), responseCode, String.valueOf(responseCode),site));
        }
    }

    private boolean isValidPageLink(Page currentPage) {
        String link = currentPage.getPath();
        return !link.isEmpty() &&
                link.startsWith(page.getPath()) &&
                !link.endsWith(".jpg") &&
                !link.endsWith(".png") &&
                !link.endsWith(".pdf") &&
                !link.contains("#") &&
                !LinkExtractorService.links.contains(link);
    }

    @Autowired
    public LinkExtractorService(SiteRepository siteRepository) {
        LinkExtractorService.siteRepository = siteRepository;
    }

    public LinkExtractorService(Page page) {
        this.page = page;
    }

    public LinkExtractorService(Page page, Site site) {
        this.site = site;
        this.page = page;
    }

    public LinkExtractorService(String mainPagePath, Site site) {
        try {
            Connection.Response response = Jsoup.connect(mainPagePath)
                    .userAgent(USER_AGENT)
                    .referrer(REFERER)
                    .execute();
            int responseCode = response.statusCode();
            Document document = response.parse();
            this.page = new Page(mainPagePath,responseCode,document.html(),site);
            this.site = site;
        } catch (IOException e) {
            site.setLastError(Arrays.toString(e.getStackTrace()));
            site.setStatus(Status.FAILED.toString());
            throw new RuntimeException(e);
        }
    }
}
