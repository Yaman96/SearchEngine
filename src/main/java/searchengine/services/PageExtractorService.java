package searchengine.services;

import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.TemporalUnit;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;

@Service
@NoArgsConstructor
public class PageExtractorService extends RecursiveAction {

    public static final CopyOnWriteArraySet<String> links = new CopyOnWriteArraySet<>();
    public static final CopyOnWriteArraySet<Page> pageList = new CopyOnWriteArraySet<>();
    private Site site;
    private static PageRepository pageRepository = null;
    private Page page;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6";
    private static final String REFERER = "https://www.google.com";

    private static final ChromeOptions options = new ChromeOptions();
    private static final WebDriver driver;

    private static final CopyOnWriteArraySet<String> windows = new CopyOnWriteArraySet<>();

    static {
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.1234.56 Safari/537.36");
        System.setProperty("webdriver.chrome.driver", "C:\\Users\\yaman\\Downloads\\chromedriver.exe");
        driver = new ChromeDriver(options);
    }

    @Override
    protected void compute() {
        Set<PageExtractorService> tasks = new HashSet<>();
        getLinks(tasks);
        tasks.forEach(ForkJoinTask::join);
    }

    private void getLinks(Set<PageExtractorService> tasks) {

        int responseCode = 418;
        Connection.Response response;
        Document document;
        Elements elements;
        try {
            synchronized (driver) {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5)); // Максимальное ожидание в секундах
                wait.withTimeout(Duration.ofSeconds(5));

                driver.get(page.getPath());
                Thread.sleep(3000);
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("window.scrollBy(0, 250);");
                Thread.sleep(1000);
                String pageSourceWithJS = (String) js.executeScript("return document.documentElement.outerHTML;");
                document = Jsoup.parse(pageSourceWithJS);
                windows.add(driver.getWindowHandle());
                if(windows.size() > 1) {
                    driver.close();
                }
            }

            System.out.println("Title is : " + document.title());
            response = getResponse(page.getPath());
            responseCode = response.statusCode();
            elements = document.select("a[href]");
            Page currentPage = new Page(page.getPath(), responseCode, document.html(), site);

            for (Element element : elements) {
                String link = site.getUrl() + element.attr("href").replaceFirst("/","");
                System.out.println(element);
                System.out.println("link is: " + link);
                System.err.println(document.html());
                Thread.sleep(5000);

                System.out.println(isValidPageLink(currentPage));
                if (isValidPageLink(currentPage)) {
                    PageExtractorService extractorService = new PageExtractorService(currentPage, site);
                    extractorService.fork();
                    tasks.add(extractorService);
                    links.add(link);
                    pageList.add(currentPage);
                }
            }
        } catch (IOException | InterruptedException e) {
            pageRepository.save(new Page(page.getPath(), responseCode, String.valueOf(responseCode), site));
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

    private boolean isValidPageLink(Page currentPage) {
        String link = currentPage.getPath();
        return !link.isEmpty() &&
                link.startsWith(page.getPath()) &&
                !link.endsWith(".jpg") &&
                !link.endsWith(".png") &&
                !link.endsWith(".pdf") &&
                !link.contains("#") &&
                !links.contains(link);
    }

    public void savePages() {
        if (Thread.currentThread().isInterrupted()) {
            System.out.println("inside savePages() Current thread is interrupted");
            return;
        }
        synchronized (pageList) {
            List<Page> pagesToSave = new ArrayList<>(pageList);
            pageRepository.saveAll(pagesToSave);
            pageList.removeAll(pagesToSave);
        }
    }

    public PageExtractorService(Page page, Site site) {
        this.site = site;
        this.page = page;
    }

    public PageExtractorService(String mainPagePath, Site site, PageRepository pageRepository) {
        PageExtractorService.pageRepository = pageRepository;
        try {
            Thread.sleep(200);
            Connection.Response response = getResponse(mainPagePath);
            int responseCode = response.statusCode();
            Document document = response.parse();
            this.page = new Page(mainPagePath, responseCode, document.html(), site);
            this.site = site;
        } catch (IOException e) {
            site.setLastError(Arrays.toString(e.getStackTrace()));
            site.setStatus(Status.FAILED.toString());
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.1234.56 Safari/537.36");

        System.setProperty("webdriver.chrome.driver", "C:\\Users\\yaman\\Downloads\\chromedriver.exe");
        WebDriver driver = new ChromeDriver(options);
        driver.get("https://playback.ru/payment.html");


        String source = driver.getPageSource();
        driver.quit();
        System.out.println(source);
    }
}
