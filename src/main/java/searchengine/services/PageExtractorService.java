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
import searchengine.repositories.PageRepository;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private String link;
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
        int responseCode;

        try {
            Thread.sleep(200);
            Connection.Response response = getResponse(link);
            responseCode = response.statusCode();
            document = response.parse();
        } catch (InterruptedException | IOException e) {
            Page errorPage = new Page(link,404,"404", site);
            pageList.add(errorPage);
            System.err.println("An exception occurred: " + e.getClass().getName());
            return;
        }

        Page currentPage = new Page(link,responseCode, document.html(), site);
        pageList.add(currentPage);
        elements = document.select("a[href]");

        for (Element element : elements) {
            String link;
            if (site.getUrl().endsWith("/")) {
                link = site.getUrl() + element.attr("href").replaceFirst("/", "");
            } else {
                link = site.getUrl() + element.attr("href");
            }

            if (isValidPageLink(link)) {
                PageExtractorService extractorService = new PageExtractorService(link, site);
                extractorService.fork();
                tasks.add(extractorService);
                links.add(link);
            }
        }
    }

    @NotNull
    public static Connection.Response getResponse(String path) throws InterruptedException, IOException {
        System.err.println("[DEBUG] getResponse(String path) got path: " + path);
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
        boolean isMatch = Pattern.compile("\\S+@\\S+\\.\\S+")
                .matcher(link)
                .find();

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
                !link.contains("tg:/") &&
                !isMatch &&
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

    public PageExtractorService(String pagePath, Site site) {
        this.link = pagePath;
        this.site = site;
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
