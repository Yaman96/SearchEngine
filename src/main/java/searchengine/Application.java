package searchengine;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.procedure.NoSuchParameterException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import searchengine.config.Site;
import searchengine.services.impl.IndexingServiceImpl;

import java.net.MalformedURLException;
import java.net.URL;

@SpringBootApplication
@Slf4j
public class Application {

    private final static Logger infoLogger = LogManager.getLogger("infoLogger");
    public static void main(String[] args) {
        if (args.length == 0) {
            throw new NoSuchParameterException("Enter site's main page url");
        } else {
            try {
                for (String url : args) {
                    String rootUrl = getRootUrl(url);
                    String siteName = getSiteName(url);
                    Site site = new Site(rootUrl,siteName);
                    IndexingServiceImpl.sites.add(site);
                    infoLogger.info("Site: " + rootUrl + " added.");
                }
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        SpringApplication.run(Application.class, args);
    }
    public static String getRootUrl(String pageUrl) throws MalformedURLException {
        URL url = new URL(pageUrl);

        String protocol = url.getProtocol();
        String host = url.getHost();

        return protocol + "://" + host;
    }

    public static String getSiteName(String pageUrl) throws MalformedURLException {
        URL url = new URL(pageUrl);

        return url.getHost();
    }
}
