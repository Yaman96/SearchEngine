package searchengine.services.impl;

import lombok.NoArgsConstructor;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.ContextBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.dto.pageExtractor.ResponseDto;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.services.PageExtractorService;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;

@Service
@NoArgsConstructor
@SuppressWarnings("ALL")
public class PageExtractorServiceImpl extends RecursiveAction implements PageExtractorService {

    private final static Logger errorLogger = LogManager.getLogger("errorLogger");
    private final static Logger debugLogger = LogManager.getLogger("debugLogger");
    public final static String REFERER = "https://google.com";
    public final static String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
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
            errorLogger.error("Exception occurred: " + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }

        ResponseDto response = getResponse(REFERER, USER_AGENT, link);

        if (response == null) {
            Page errorPage = new Page(link, 503, "503", site);
            pageList.add(errorPage);
            debugLogger.debug("A problem occurred or the connection was aborted or http protocol error.");
            return;
        }

        int responseCode = response.getResponseCode();
        String pageContent = response.getContent();

        if (pageContent == null) {
            Page errorPage = new Page(link, 503, "503", site);
            pageList.add(errorPage);
            debugLogger.debug("Error occurred while getting content InputStream or while reading InputStream.");
            return;
        }
        Document document = Jsoup.parse(pageContent);
        Elements elements = document.select("a[href]");

        Page currentPage = new Page(link, responseCode, document.html(), site);
        pageList.add(currentPage);
        links.add(link);

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

    public static ResponseDto getResponse(String referer, String userAgent, String uri) {

        try (final CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            final HttpGet httpget = new HttpGet(uri);
            httpget.setHeader("Referer", referer);
            httpget.setHeader("User-Agent", userAgent);

            final RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                    .build();
            httpget.setConfig(requestConfig);

            final HttpClientContext context = ContextBuilder.create().build();

            final ClassicHttpResponse[] httpResponse = new ClassicHttpResponse[1];
            final String[] content = new String[1];
            httpclient.execute(httpget, context, response -> {
                httpResponse[0] = response;
                content[0] = getPageContent(response.getEntity().getContent());
                EntityUtils.consume(response.getEntity());
                return null;
            });
            ResponseDto responseDto = new ResponseDto(httpResponse[0].getCode(), content[0]);
            return responseDto;
        }
        catch (ClientProtocolException e) {
            //Add logging (ClientProtocolException - in case of an http protocol error)
            System.err.println(e);
        }
        catch (IOException e) {
            //Add logging (IOException - in case of a problem or the connection was aborted)
            System.err.println(e);
        }
        return null;
    }

    private static String getPageContent(InputStream contentInputStream) {

        StringBuilder content = new StringBuilder();
        while (true) {
            try {
                if (!(contentInputStream.available() > 0)) break;
                content.append((char) contentInputStream.read());
            } catch (IOException e) {
                //Add logging (Error occurred while reading InputStream)
                e.printStackTrace();
                return null;
            }
        }
        return content.toString();
    }

    public static void main(String[] args) {
        ResponseDto response = getResponse(REFERER, USER_AGENT, "https://honestreporting.ca/who-we-are/");
        String html = response.getContent();
        System.out.println(html.length());
        System.out.println(response.getResponseCode());

        Document doc = Jsoup.parse(html);
        Elements links = doc.select("a[href]");

        String mainUrl = "https://honestreporting.ca/";
        String currentUrl = "https://honestreporting.ca/who-we-are/";

        List<String> urls = new ArrayList<>();
        // Вывести найденные ссылки
        for (org.jsoup.nodes.Element link : links) {
            String linkstr = link.attr("href");

            if(!isNotNullAndEmpty(linkstr)) {
                continue;
            }

            if(linkstr.startsWith(mainUrl)) {
//                System.out.println("Link: " + linkstr);
                urls.add(linkstr);
            } else if (linkstr.startsWith("http")) {
                continue;
            } else {
                if (linkstr.startsWith("/")) linkstr = linkstr.replaceFirst("/", "");
//                System.out.println("Link: " + currentUrl + linkstr);
                urls.add(currentUrl + linkstr);
                urls.add(mainUrl + linkstr);
            }
        }
        checkLinks(urls);
    }

    private static boolean isNotNullAndEmpty(String linkstr) {
        return linkstr != null && !linkstr.isEmpty() && !linkstr.isBlank();
    }

    private static void checkLinks(List<String> urls) {
        for (String url : urls) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            ResponseDto responseDto = getResponse(REFERER, USER_AGENT, url);
            System.out.println("Link: " + url + " response code: " + responseDto.getResponseCode());
        }
    }
}
