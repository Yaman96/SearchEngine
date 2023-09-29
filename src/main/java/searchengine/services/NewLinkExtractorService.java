package searchengine.services;

import lombok.NoArgsConstructor;
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
@NoArgsConstructor
public class NewLinkExtractorService extends RecursiveTask<CopyOnWriteArraySet<Page>> {

    public static CopyOnWriteArraySet<String> links = new CopyOnWriteArraySet<>();
    public static CopyOnWriteArraySet<Page> pageList = new CopyOnWriteArraySet<>();
    private Site site;

    @Autowired
    private SiteRepository siteRepository;
    private Page page;
    private final String USER_AGENT = "Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6";
    private final String REFERER = "https://www.google.com";

    @Override
    protected CopyOnWriteArraySet<Page> compute() {
        CopyOnWriteArraySet<Page> pages = new CopyOnWriteArraySet<>();
        pages.add(page);
        Set<NewLinkExtractorService> tasks = new HashSet<>();

        getLinks(tasks);

        for (NewLinkExtractorService task : tasks) {
            pages.addAll(task.join());
            System.out.println("in method compute page size: " + pages.size());
        }
        return pages;
    }

    private void getLinks(Set<NewLinkExtractorService> tasks) {
        try {
            Thread.sleep(200);
            Connection.Response response = Jsoup.connect(page.getPath())
                                                .userAgent(USER_AGENT)
                                                .referrer(REFERER)
                                                .execute();
            int responseCode = response.statusCode();
            Document document = response.parse();
            Elements elements = document.select("a[href]");

            for (Element element : elements) {
                String link = element.absUrl("href");
                System.out.println("Site link: " + site.getUrl() + "current link: " + link);
                Page currentPage = new Page(link,responseCode,document.html(),site);
                if(isValidPageLink(currentPage)) {
                    System.out.println("before forking: " + site);
                    NewLinkExtractorService extractorService = new NewLinkExtractorService(currentPage,site);
                    extractorService.fork();
                    tasks.add(extractorService);
                    System.out.println(link);
                    NewLinkExtractorService.links.add(link);
                    pageList.add(new Page(link.trim(),responseCode,document.html(),site));
                    System.out.println(pageList.size());
                }
            }
        } catch (IOException | InterruptedException e) {
            site.setStatus(Status.FAILED.toString());
            site.setLastError("Site indexing exception occurred. \n" + Arrays.toString(e.getStackTrace()));
            siteRepository.save(site);
        }
    }

    private boolean isValidPageLink(Page currentPage) {
        String link = currentPage.getPath();
        return !link.isEmpty() && link.startsWith(page.getPath()) && !link.endsWith(".jpg") &&
                !link.endsWith(".png") && !link.endsWith(".pdf") &&
                !link.contains("#") && !NewLinkExtractorService.links.contains(link);
    }

//    @Autowired
    public NewLinkExtractorService(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    public NewLinkExtractorService(Page page) {
        this.page = page;
    }

    public NewLinkExtractorService(Page page, Site site) {
        this.site = site;
        this.page = page;
    }

    public NewLinkExtractorService(String mainPagePath, Site site) {
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
            siteRepository.save(site);
//            throw new RuntimeException(e);
        }
    }
}
