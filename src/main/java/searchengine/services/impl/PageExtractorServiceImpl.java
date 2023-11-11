package searchengine.services.impl;

import lombok.NoArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.PageRepository;
import searchengine.services.PageExtractorService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;

import static searchengine.services.PageExtractorService.getResponse;

@Service
@NoArgsConstructor
public class PageExtractorServiceImpl extends RecursiveAction implements PageExtractorService {

    public static final CopyOnWriteArraySet<String> links = new CopyOnWriteArraySet<>();
    public static final CopyOnWriteArraySet<Page> pageList = new CopyOnWriteArraySet<>();
    private Site site;
    public static PageRepository pageRepository;
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
            System.err.println("An exception occurred: " + e.getClass().getName() + " status: ");
            return;
        }

        Page currentPage = new Page(link,responseCode, document.html(), site);
        pageList.add(currentPage);
        elements = document.select("a[href]");

        for (Element element : elements) {
            String link;
            if (element.attr("href").contains("https://")) {
                link = element.attr("href");
            }
            else if (site.getUrl().endsWith("/")) {
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
            pageRepository.saveAll(pagesToSave);
            pageList.removeAll(pagesToSave);
        }
    }
}
