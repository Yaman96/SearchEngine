package searchengine.services.impl;

import org.apache.lucene.morphology.LuceneMorphology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.services.LemmaFinderService;
import searchengine.services.SnippetFinder;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SnippetFinderImpl implements SnippetFinder {

    private final LemmaFinderService lemmaFinderServiceImpl;
    private int maxCountOfQueryWordsInsideBatch = 0;

    @Autowired
    public SnippetFinderImpl(LemmaFinderService lemmaFinderServiceImpl) {
        this.lemmaFinderServiceImpl = lemmaFinderServiceImpl;
    }

    @Override
    public String findSnippet(Set<Lemma> queryLemmas, Page page) {
        LuceneMorphology luceneMorphologyEn = lemmaFinderServiceImpl.getLuceneMorphologyEn();
        LuceneMorphology luceneMorphologyRu = lemmaFinderServiceImpl.getLuceneMorphologyRu();
        Set<String> queryLemmasStrings = queryLemmas.stream().map(Lemma::getLemma).collect(Collectors.toSet());
        String visibleText = LemmaFinderService.extractVisibleText(page.getContent()).trim().replaceAll("\\s{2,}", " ");
        String[] wordsEnRu = lemmaFinderServiceImpl.arrayContainsEnglishAndRussianWords(visibleText);

        ArrayList<String> wordsList = new ArrayList<>(List.of(wordsEnRu));

        makeQueryWordsBold(wordsList,luceneMorphologyEn,luceneMorphologyRu,queryLemmasStrings);

        Map<Integer,String> relevance_snippet = new TreeMap<>();
        List<List<String>> listsWith30wordsInside = splitList(wordsList, 30);

        createSnippetsAndReturnMaxCountOfQueryWordsInASnippet(listsWith30wordsInside,relevance_snippet);
        return relevance_snippet.get(maxCountOfQueryWordsInsideBatch);
    }

    private void makeQueryWordsBold(ArrayList<String> wordsList, LuceneMorphology luceneMorphologyEn, LuceneMorphology luceneMorphologyRu, Set<String> queryLemmasStrings) {
        for (String word : wordsList) {
            String normalForm;
            if (!word.isEmpty() && LemmaFinderServiceImpl.RU_WORDS_PATTERN.matcher(word).matches()) {
                normalForm = luceneMorphologyRu.getNormalForms(word.toLowerCase()).get(0);
                if (queryLemmasStrings.contains(normalForm)) {
                    int index = wordsList.indexOf(word);
                    wordsList.set(index,String.format("<b>%s</b>", word));
                }
            }
            else if (!word.isEmpty() && LemmaFinderServiceImpl.EN_WORDS_PATTERN.matcher(word).matches()) {
                normalForm = luceneMorphologyEn.getNormalForms(word.toLowerCase()).get(0);
                if (queryLemmasStrings.contains(normalForm)) {
                    int index = wordsList.indexOf(word);
                    wordsList.set(index,String.format("<b>%s</b>", word));
                }
            }
        }
    }

    private void createSnippetsAndReturnMaxCountOfQueryWordsInASnippet(List<List<String>> listsWith30wordsInside, Map<Integer,String> relevance_snippet) {
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
