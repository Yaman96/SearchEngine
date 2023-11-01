package searchengine.services;

import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;

@Service
@NoArgsConstructor
public class PageExtractorService extends RecursiveAction {

    public static final CopyOnWriteArraySet<String> links = new CopyOnWriteArraySet<>();
    public static final CopyOnWriteArraySet<Page> pageList = new CopyOnWriteArraySet<>();
    private Site site;
    public static PageRepository pageRepository;
    private Page page;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6";
    private static final String REFERER = "https://www.google.com";

    public static final CopyOnWriteArraySet<String> invalidLinks = new CopyOnWriteArraySet<>();

    @Override
    protected void compute() {
        Set<PageExtractorService> tasks = new HashSet<>();
        getLinks(tasks);
        tasks.forEach(ForkJoinTask::join);
    }

    private void getLinks(Set<PageExtractorService> tasks) {

        Document document;
        Elements elements;
        document = Jsoup.parse(page.getContent());
        elements = document.select("a[href]");

        for (Element element : elements) {
            String link = site.getUrl() + element.attr("href").replaceFirst("/", "");

            if (isValidPageLink(link)) {
                PageExtractorService extractorService = new PageExtractorService(link, site);
                extractorService.fork();
                tasks.add(extractorService);
                links.add(link);
            } else {
                if (!isValidPageLink(link)) {
                    invalidLinks.add(link);
                }
            }
        }
    }

    @NotNull
    public static Connection.Response getResponse(String path) throws InterruptedException, IOException {
        Connection.Response response;
        Thread.sleep(200);
        response = Jsoup.connect(path)
                .userAgent(USER_AGENT)
                .referrer(REFERER)
                .execute();
        return response;
    }

    public static String getHTML(Connection.Response response) throws IOException {
        Document document = response.parse();
        return document.html();
    }

    public boolean isValidPageLink(String link) {
        Pattern pattern1 = Pattern.compile(".*https?:.*https?:.*");
        boolean isMatch1 = pattern1.matcher(link).matches();

        Pattern pattern2 = Pattern.compile("^https?:\\/\\/(?:www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b(?:[-a-zA-Z0-9()@:%_\\+.~#?&\\/=]*)$");
        boolean isMatch2 = pattern2.matcher(link).matches();

        return !link.isEmpty() &&
                link.startsWith(site.getUrl()) &&
                !link.endsWith(".jpg") &&
                !link.endsWith(".png") &&
                !link.endsWith(".pdf") &&
                !link.contains("#") &&
                !link.contains("tel:") &&
//                link.contains(".html") &&
                !isMatch1 &&
                isMatch2 &&
                !links.contains(link);
    }

    public void savePages() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        synchronized (pageList) {
            List<Page> pagesToSave = new ArrayList<>(pageList);
            pageRepository.saveAll(pagesToSave);
            pageList.removeAll(pagesToSave);
        }
    }

    public PageExtractorService(String mainPagePath, Site site) {
        try {
            Thread.sleep(200);
            Connection.Response response = getResponse(mainPagePath);
            int responseCode = response.statusCode();
            Document document = response.parse();
            this.page = new Page(mainPagePath, responseCode, document.html(), site);
            this.site = site;
            pageList.add(this.page);
        } catch (IOException e) {
//            site.setLastError(Arrays.toString(e.getStackTrace()));
//            site.setStatus(Status.FAILED.toString());
            this.page = new Page(mainPagePath, 404, "404 not found", site);
            this.site = site;
            pageList.add(this.page);
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        PageExtractorService pageExtractorService = new PageExtractorService();
        pageExtractorService.site = new Site();
        pageExtractorService.site.setUrl("https://playback.ru/");

        ArrayList<String> links = new ArrayList<>();
        links.add("https://playback.ru/product_info/1124457.html");
        links.add("https://playback.ru/product/1124428.html");
        links.add("https://playback.ru/product/1123748.html");

        for (String link : links) {
            System.out.println("is link: " + link + " is valid: " + pageExtractorService.isValidPageLink(link));
        }
    }
}
