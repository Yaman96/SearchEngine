package searchengine.services;

import lombok.AllArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class SnippetFinderImpl implements SnippetFinder {

    private LemmaFinderService lemmaFinderService;

    @Override
    public String findSnippet(Set<Lemma> queryLemmas, Page page) {
        LuceneMorphology luceneMorphologyEn = lemmaFinderService.getLuceneMorphologyEn();
        LuceneMorphology luceneMorphologyRu = lemmaFinderService.getLuceneMorphologyRu();
        Set<String> queryLemmasStrings = queryLemmas.stream().map(Lemma::getLemma).collect(Collectors.toSet());
        String visibleText = LemmaFinderService.extractVisibleText(page.getContent()).trim().replaceAll("\\s{2,}", " ");
        String[] wordsEnRu = lemmaFinderService.arrayContainsEnglishAndRussianWords(visibleText);

        ArrayList<String> wordsList = new ArrayList<>(List.of(wordsEnRu));

        for (String word : wordsList) {
            String normalForm;
            if (!word.isEmpty() && LemmaFinderService.RU_WORDS_PATTERN.matcher(word).matches()) {
                normalForm = luceneMorphologyRu.getNormalForms(word.toLowerCase()).get(0);
                if (queryLemmasStrings.contains(normalForm)) {
                    int index = wordsList.indexOf(word);
                    wordsList.set(index,String.format("<b>%s</b>", word));
                }
            }
            else if (!word.isEmpty() && LemmaFinderService.EN_WORDS_PATTERN.matcher(word).matches()) {
                normalForm = luceneMorphologyEn.getNormalForms(word.toLowerCase()).get(0);
                if (queryLemmasStrings.contains(normalForm)) {
                    int index = wordsList.indexOf(word);
                    wordsList.set(index,String.format("<b>%s</b>", word));
                }
            }
        }

        Map<Integer,String> relevance_snippet = new TreeMap<>();
        List<List<String>> listsWith30wordsInside = splitList(wordsList, 30);
        int maxCountOfQueryWordsInsideBatch = 0;

        for(List<String> batch : listsWith30wordsInside) {
            int countOfQueryWordsInsideThisBatch = 0;
            for (String word : batch) {
                if(word.startsWith("<b>") && word.endsWith("</b>")) {
                    countOfQueryWordsInsideThisBatch++;
                }
            }
            if (countOfQueryWordsInsideThisBatch > 0) {
                String snippet = String.join(" ", batch);
                relevance_snippet.put(countOfQueryWordsInsideThisBatch,snippet);
                maxCountOfQueryWordsInsideBatch = Math.max(maxCountOfQueryWordsInsideBatch,countOfQueryWordsInsideThisBatch);
            }
        }
        return relevance_snippet.get(maxCountOfQueryWordsInsideBatch);
    }

    public List<List<String>> splitList(List<String> inputList, int batchSize) {
        List<List<String>> outputList = new ArrayList<>();

        for (int i = 0; i < inputList.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, inputList.size());
            List<String> batch = inputList.subList(i, endIndex);
            outputList.add(new ArrayList<>(batch));
        }
        return outputList;
    }
}
