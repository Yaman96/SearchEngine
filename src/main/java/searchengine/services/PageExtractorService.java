package searchengine.services;

import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
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
import searchengine.repositories.PageRepository;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;

@Service
@NoArgsConstructor
public class PageExtractorService extends RecursiveAction {

    public static CopyOnWriteArraySet<String> links = new CopyOnWriteArraySet<>();
    public static final CopyOnWriteArraySet<Page> pageList = new CopyOnWriteArraySet<>();
    private Site site;
    @Autowired
    private static PageRepository pageRepository;
    private Page page;
    private final String USER_AGENT = "Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6";
    private final String REFERER = "https://www.google.com";

    @Override
    protected void compute() {
        Set<PageExtractorService> tasks = new HashSet<>();
        getLinks(tasks);
        tasks.forEach(ForkJoinTask::join);
    }

    private void getLinks(Set<PageExtractorService> tasks) {
        int responseCode = 418;
        Connection.Response response;
        try {
            response = getResponse(page.getPath());
            responseCode = response.statusCode();
            Document document = response.parse();
            Elements elements = document.select("a[href]");

            for (Element element : elements) {
                String link = element.absUrl("href");
                Page currentPage = new Page(link,responseCode,document.html(),site);
                if(isValidPageLink(currentPage)) {
                    PageExtractorService extractorService = new PageExtractorService(currentPage,site);
                    extractorService.fork();
                    tasks.add(extractorService);
                    PageExtractorService.links.add(link);
                    pageList.add(new Page(link.trim(),responseCode,document.html(),site));
                }
            }
        } catch  (IOException | InterruptedException e) {
            pageRepository.save(new Page(page.getPath(), responseCode, String.valueOf(responseCode),site));
        }
    }

    @NotNull
    private Connection.Response getResponse(String path) throws InterruptedException, IOException {
        Connection.Response response;
        Thread.sleep(200);
        response = Jsoup.connect(path)
                        .userAgent(USER_AGENT)
                        .referrer(REFERER)
                        .execute();
        return response;
    }

    private boolean isValidPageLink(Page currentPage) {
        String link = currentPage.getPath();
        return !link.isEmpty() &&
                link.startsWith(page.getPath()) &&
                !link.endsWith(".jpg") &&
                !link.endsWith(".png") &&
                !link.endsWith(".pdf") &&
                !link.contains("#") &&
                !PageExtractorService.links.contains(link);
    }

    public PageExtractorService(Page page) {
        this.page = page;
    }

    public PageExtractorService(Page page, Site site) {
        this.site = site;
        this.page = page;
    }

    public PageExtractorService(String mainPagePath, Site site) {
        try {
            Thread.sleep(200);
            Connection.Response response = getResponse(mainPagePath);
            int responseCode = response.statusCode();
            Document document = response.parse();
            this.page = new Page(mainPagePath,responseCode,document.html(),site);
            this.site = site;
        } catch (IOException e) {
            site.setLastError(Arrays.toString(e.getStackTrace()));
            site.setStatus(Status.FAILED.toString());
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}