package searchengine.services.impl;

import lombok.NoArgsConstructor;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.ContextBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.services.PageExtractorService;

import java.io.IOException;
import java.io.InputStream;
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
@SuppressWarnings("ALL")
public class PageExtractorServiceImpl extends RecursiveAction implements PageExtractorService {

    private final String REFERER = "https://google.com";
    private final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    public static final CopyOnWriteArraySet<String> links = new CopyOnWriteArraySet<>();
    public static final CopyOnWriteArraySet<Page> pageList = new CopyOnWriteArraySet<>();
    public static PageService pageService;
    private Site site;
    private String link;

    public PageExtractorServiceImpl(String pagePath, Site site) {
        this.link = pagePath;
        this.site = site;
    }

    @Override
    protected void compute() {
        Set<PageExtractorServiceImpl> tasks = new HashSet<>();
        getLinks(tasks);
        tasks.forEach(ForkJoinTask::join);
    }

    private void getLinks(Set<PageExtractorServiceImpl> tasks) {

        try {Thread.sleep(200);}
        catch (InterruptedException e) {
            //Add logging
            e.printStackTrace();
        }

        ClassicHttpResponse response = getResponse(REFERER, USER_AGENT, link);

        if (response == null) { // A problem occurred or the connection was aborted or http protocol error
            Page errorPage = new Page(link, 503, "503", site);
            pageList.add(errorPage);
            return;
        }

        int responseCode = response.getCode();
        String pageContent = getPageContent(response);

        //Error occurred while getting content InputStream or while reading InputStream
        if (pageContent == null) {
            Page errorPage = new Page(link, 503, "503", site);
            pageList.add(errorPage);
            return;
        }
        Document document = Jsoup.parse(pageContent);
        Elements elements = document.select("a[href]");

        Page currentPage = new Page(link, responseCode, document.html(), site);
        pageList.add(currentPage);

        for (Element element : elements) {
            String link;
            if (element.attr("href").contains("https://")) {
                link = element.attr("href");
            } else if (site.getUrl().endsWith("/")) {
                link = site.getUrl() + element.attr("href").replaceFirst("/", "");
            } else {
                link = site.getUrl() + element.attr("href");
            }

            if (isValidPageLink(link)) {
                PageExtractorServiceImpl extractorService = new PageExtractorServiceImpl(link, site);
                extractorService.fork();
                tasks.add(extractorService);
                links.add(link);
            }
        }
    }


    @SuppressWarnings("all")
    @Override
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
                !link.endsWith(".doc") &&
                !link.endsWith(".docx") &&
                !link.endsWith(".xls") &&
                !link.endsWith(".pdf") &&
                !link.contains("#") &&
                !link.contains("tel:") &&
                !link.contains("tg:/") &&
                !isMatch &&
                !isMatch1 &&
                isMatch2 &&
                !links.contains(link);
    }

    @Override
    public void savePages() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        synchronized (pageList) {
            List<Page> pagesToSave = new ArrayList<>(pageList);
            pageService.saveAll(pagesToSave);
            pageList.removeAll(pagesToSave);
        }
    }

    private ClassicHttpResponse getResponse(String referer, String userAgent, String uri) {

        try (final CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            final HttpGet httpget = new HttpGet(uri);
            httpget.setHeader("Referer", referer);
            httpget.setHeader("User-Agent", userAgent);

            final HttpClientContext context = ContextBuilder.create().build();

            final ClassicHttpResponse[] httpResponse = new ClassicHttpResponse[1];
            httpclient.execute(httpget, context, response -> {
                httpResponse[0] = response;
                EntityUtils.consume(response.getEntity());
                return null;
            });
            return httpResponse[0];

        }
        catch (ClientProtocolException e) {
            //Add logging (ClientProtocolException - in case of an http protocol error)
            e.printStackTrace();
        }
        catch (IOException e) {
            //Add logging (IOException - in case of a problem or the connection was aborted)
            e.printStackTrace();
        }
        return null;
    }

    private String getPageContent(ClassicHttpResponse response) {
        InputStream inputStream;
        try {
            inputStream = response.getEntity().getContent();
        } catch (IOException e) {
            //Add logging (Error occurred while getting content InputStream)
            e.printStackTrace();
            return null;
        }
        StringBuilder content = new StringBuilder();
        while (true) {
            try {
                if (!(inputStream.available() > 0)) break;
                content.append((char) inputStream.read());
            } catch (IOException e) {
                //Add logging (Error occurred while reading InputStream)
                e.printStackTrace();
                return null;
            }
        }
        return content.toString();
    }
}
