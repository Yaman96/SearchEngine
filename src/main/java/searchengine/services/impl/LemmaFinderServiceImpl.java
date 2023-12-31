package searchengine.services.impl;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.services.LemmaFinderService;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class LemmaFinderServiceImpl implements LemmaFinderService {

    private final LuceneMorphology luceneMorphologyEn;
    private final LuceneMorphology luceneMorphologyRu;
    private static final String WORD_TYPE_REGEX = "\\W\\w&&[^a-zA-Zа-яА-Я\\s]";
    private static final String[] particlesNames = new String[]{"CONJ", "INT", "PN", "PREP", "МЕЖД", "ПРЕДЛ", "СОЮЗ"};
    public static final Pattern RU_WORDS_PATTERN = Pattern.compile("^[а-я]+$");
    public static final Pattern EN_WORDS_PATTERN = Pattern.compile("^[a-z]+$");

    LemmaFinderServiceImpl() throws IOException {
        luceneMorphologyEn = new EnglishLuceneMorphology();
        luceneMorphologyRu = new RussianLuceneMorphology();
    }

    LemmaFinderServiceImpl(LuceneMorphology luceneMorphologyEn, LuceneMorphology luceneMorphologyRu) {
        this.luceneMorphologyEn = luceneMorphologyEn;
        this.luceneMorphologyRu = luceneMorphologyRu;
    }

    /**
     * Метод разделяет текст на слова, находит все леммы и считает их количество.
     *
     * @param text текст из которого будут выбираться леммы
     * @return ключ является леммой, а значение количеством найденных лемм
     */
    @Override
    public Map<String, Integer> collectLemmas(String text) {
        String cleanedText = LemmaFinderService.extractVisibleText(text);
        String[] words = arrayContainsEnglishAndRussianWords(cleanedText);
        HashMap<String, Integer> lemmas = new HashMap<>();

        for (String word : words) {
            if (word.isEmpty() || word.isBlank()) continue;

            List<String> wordBaseForms = new ArrayList<>();
            List<String> normalForms = new ArrayList<>();

            getEnOrRuNormalForms(word, wordBaseForms, normalForms);

            if (normalForms.isEmpty()) continue;

            String normalWord = normalForms.get(0);

            if (lemmas.containsKey(normalWord)) {
                lemmas.put(normalWord, lemmas.get(normalWord) + 1);
            } else {
                lemmas.put(normalWord, 1);
            }
        }

        return lemmas;
    }

    /**
     * @param text текст из которого собираем все леммы
     * @return набор уникальных лемм найденных в тексте
     */
    @Override
    public Set<String> getLemmaSet(String text) {
        String[] textArray = arrayContainsEnglishAndRussianWords(text);
        Set<String> lemmaSet = new HashSet<>();
        for (String word : textArray) {
            if (word.isEmpty() || word.isBlank()) continue;

            if (RU_WORDS_PATTERN.matcher(word).matches()) {
                checkRuLemmaAndAddItToSet(word, lemmaSet);
            } else if (EN_WORDS_PATTERN.matcher(word).matches()) {
                checkEnLemmaAndAddItToSet(word, lemmaSet);
            }
        }
        return lemmaSet;
    }

    private void checkRuLemmaAndAddItToSet(String word, Set<String> lemmaSet) {
        if (!isCorrectWordFormRu(word)) return;

        List<String> wordBaseForms = luceneMorphologyRu.getMorphInfo(word);

        if (anyWordBaseBelongToParticle(wordBaseForms)) return;

        lemmaSet.add(luceneMorphologyRu.getNormalForms(word).get(0));
    }

    private void checkEnLemmaAndAddItToSet(String word, Set<String> lemmaSet) {
        if (!isCorrectWordFormEn(word)) return;

        List<String> wordBaseForms = luceneMorphologyEn.getMorphInfo(word);

        if (anyWordBaseBelongToParticle(wordBaseForms)) return;

        lemmaSet.add(luceneMorphologyEn.getNormalForms(word).get(0));
    }

    private void getEnOrRuNormalForms(String word, List<String> wordBaseForms, List<String> normalForms) {
        if (RU_WORDS_PATTERN.matcher(word).matches()) {
            wordBaseForms.addAll(luceneMorphologyRu.getMorphInfo(word));

            if (anyWordBaseBelongToParticle(wordBaseForms)) return;

            normalForms.addAll(luceneMorphologyRu.getNormalForms(word));
        } else if (EN_WORDS_PATTERN.matcher(word).matches()) {
            wordBaseForms.addAll(luceneMorphologyEn.getMorphInfo(word));
            if (anyWordBaseBelongToParticle(wordBaseForms)) {
                return;
            }

            normalForms.addAll(luceneMorphologyEn.getNormalForms(word));
        }
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasParticleProperty);
    }

    private boolean hasParticleProperty(String wordBase) {
        for (String property : particlesNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String[] arrayContainsEnglishAndRussianWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^a-zа-я\\s])", " ")
                .trim()
                .split("\\s+");
    }

    private boolean isCorrectWordFormRu(String word) {
        List<String> wordInfo = luceneMorphologyRu.getMorphInfo(word);
        for (String morphInfo : wordInfo) {
            if (morphInfo.matches(WORD_TYPE_REGEX)) {
                return false;
            }
        }
        return true;
    }

    private boolean isCorrectWordFormEn(String word) {
        List<String> wordInfo = luceneMorphologyEn.getMorphInfo(word);
        for (String morphInfo : wordInfo) {
            if (morphInfo.matches(WORD_TYPE_REGEX)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public LuceneMorphology getLuceneMorphologyEn() {
        return luceneMorphologyEn;
    }

    @Override
    public LuceneMorphology getLuceneMorphologyRu() {
        return luceneMorphologyRu;
    }
}
