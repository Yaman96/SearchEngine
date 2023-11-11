package searchengine.services;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;


public interface PageExtractorService {
    String USER_AGENT = "Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6";
    String REFERER = "https://www.google.com";

    @NotNull
    static Connection.Response getResponse(String path) throws InterruptedException, IOException {
        System.err.println("[DEBUG] getResponse(String path) got path: " + path);
        Connection.Response response;
        Thread.sleep(200);
        response = Jsoup.connect(path)
                .userAgent(USER_AGENT)
                .referrer(REFERER)
                .execute();
        return response;
    }

    static String getHTML(Connection.Response response) throws IOException {
        Document document = response.parse();
        return document.html();
    }

    boolean isValidPageLink(String link);

    void savePages();
}
