package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.RecursiveTask;

@Service
public class NewLinkExtractorService extends RecursiveTask<CopyOnWriteArraySet<Page>> {

    public static CopyOnWriteArraySet<String> links = new CopyOnWriteArraySet<>();
    public static CopyOnWriteArraySet<Page> pageList = new CopyOnWriteArraySet<>();
    private Site site;
    private SiteRepository siteRepository;
    private String url;
    private final String USER_AGENT = "Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6";
    private final String REFERER = "https://www.google.com";

    @Override
    protected CopyOnWriteArraySet<Page> compute() {
        CopyOnWriteArraySet<Page> pages = new CopyOnWriteArraySet<>();
        Set<NewLinkExtractorService> tasks = new HashSet<>();

        getLinks(tasks);

        for (NewLinkExtractorService task : tasks) {
            pages.addAll(task.join());
        }
        return pages;
    }

    private void getLinks(Set<NewLinkExtractorService> tasks) {
        try {
            Thread.sleep(200);
            Connection.Response response = Jsoup.connect(url)
                                                .userAgent(USER_AGENT)
                                                .referrer(REFERER)
                                                .execute();
            int responseCode = response.statusCode();
            Document document = response.parse();
            Elements elements = document.select("a[href]");

            for (Element element : elements) {
                String link = element.absUrl("href");
                if(isValidLink(link)) {
                    NewLinkExtractorService extractorService = new NewLinkExtractorService(link);
                    extractorService.fork();
                    tasks.add(extractorService);
                    System.out.println(link);
                    NewLinkExtractorService.links.add(link);
                }
            }
            pageList.add(new Page(url,responseCode,document.html(),site));
        } catch (IOException | InterruptedException e) {
            site.setStatus(Status.FAILED.toString());
            site.setLastError("Site indexing exception occurred. \n" + Arrays.toString(e.getStackTrace()));
            siteRepository.save(site);
        }
    }

    private boolean isValidLink(String link) {
        return !link.isEmpty() && link.startsWith(url) && !link.endsWith(".jpg") &&
                !link.endsWith(".png") && !link.endsWith(".pdf") &&
                !link.contains("#") && !NewLinkExtractorService.links.contains(link);
    }

    @Autowired
    public NewLinkExtractorService(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    public NewLinkExtractorService(String url) {
        this.url = url.trim();
    }

    public NewLinkExtractorService(String url, Site site) {
        this.site = site;
        this.url = url;
    }
}
