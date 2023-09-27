package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.RecursiveTask;

@Service
@Scope("prototype")
public class LinkExtractorService extends RecursiveTask<String> {

    private String url;
    private static String startURL;
    private static CopyOnWriteArraySet<String> links = new CopyOnWriteArraySet<>();

    @Autowired
    public LinkExtractorService() {}

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
            document = Jsoup.connect(url).get();
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
        }catch (IOException | InterruptedException e) {
            e.printStackTrace();
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
