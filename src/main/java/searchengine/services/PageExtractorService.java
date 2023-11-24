package searchengine.services;

public interface PageExtractorService {
    boolean isValidPageLink(String link);
    void savePages();
}
