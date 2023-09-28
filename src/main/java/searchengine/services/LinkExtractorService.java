package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
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
@Scope("prototype")
public class LinkExtractorService extends RecursiveTask<String> {

    private String url;
    private static String startURL;
    public static CopyOnWriteArraySet<String> links = new CopyOnWriteArraySet<>();
    public static List<Page> pageList = new ArrayList<>();
    public static Site currentSite;
    private SiteRepository siteRepository;

    @Autowired
    public LinkExtractorService(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    public LinkExtractorService(String url) {
        this.url = url.trim();
    }

    public LinkExtractorService(String url, String startURL) {
        this.url = url.trim();
        LinkExtractorService.startURL = startURL.trim();
    }

    @Override
    protected String compute() {
        StringBuffer stringBuffer = new StringBuffer(url + "\n");
        Set<LinkExtractorService> tasks = new HashSet<>();

        getLinks(tasks);

        for (LinkExtractorService linkExtractor : tasks) {
            stringBuffer.append(linkExtractor.join());
        }
        return stringBuffer.toString();
    }

    private void getLinks(Set<LinkExtractorService> tasks) {
        Document document;
        Elements elements;
        try {
            Thread.sleep(500);
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT " +
                    "5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("https://www.google.com").execute();
            document = response.parse();
//            System.out.println("HTML " + document.html());
            int code = response.statusCode();
            elements = document.select("a");
            for (Element element : elements) {
                String attr = element.attr("abs:href");
                if(!attr.isEmpty() && attr.startsWith(startURL) && !attr.contains("#") && !LinkExtractorService.links.contains(attr)) {
                    LinkExtractorService linkExtractor = new LinkExtractorService(attr);
                    linkExtractor.fork();
                    tasks.add(linkExtractor);
                    System.out.println(attr);
                    LinkExtractorService.links.add(attr);
                }
            }
            pageList.add(new Page(url,code, document.html(),currentSite));
        }catch (IOException | InterruptedException e) {
            currentSite.setStatus(Status.FAILED.toString());
            currentSite.setLastError("Site indexing exception occurred. \n" + Arrays.toString(e.getStackTrace()));
            siteRepository.save(currentSite);
        }
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public static void setStartURL(String startURL) {
        LinkExtractorService.startURL = startURL;
    }
}
