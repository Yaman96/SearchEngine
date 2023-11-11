package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.Map;
import java.util.Set;

public interface LemmaFinderService {
    static String extractVisibleText(String html) {
        Document doc = Jsoup.parse(html);
        return doc.text();
    }
    Map<String, Integer> collectLemmas(String text);
    Set<String> getLemmaSet(String text);
    String[] arrayContainsEnglishAndRussianWords(String text);
    LuceneMorphology getLuceneMorphologyEn();
    LuceneMorphology getLuceneMorphologyRu();
}
