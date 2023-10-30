package searchengine.services;

import lombok.AllArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class SnippetFinderImpl implements SnippetFinder {

    private LemmaFinderService lemmaFinderService;

    @Override
    public String findSnippet(Set<Lemma> queryLemmas, Page page) {
        LuceneMorphology luceneMorphology = lemmaFinderService.getLuceneMorphology();
        Set<String> queryLemmasStrings = queryLemmas.stream().map(Lemma::getLemma).collect(Collectors.toSet());
        String visibleText = LemmaFinderService.extractVisibleText(page.getContent()).trim().replaceAll("\\s{2,}", " ");
        String[] words = lemmaFinderService.arrayContainsRussianWords(visibleText);
        ArrayList<String> wordsList = new ArrayList<>(List.of(words));

        for (String word : wordsList) {
            String normalForm = luceneMorphology.getNormalForms(word.toLowerCase()).get(0);
            if (queryLemmasStrings.contains(normalForm)) {
                int index = wordsList.indexOf(word);
                wordsList.set(index,String.format("<b>%s</b>", word));
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

    public static void main(String[] args) throws IOException {
        LemmaFinderService lemmaFinderService1 = new LemmaFinderService();
        String input = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Пример HTML-страницы с большим текстом</title>
                </head>
                <body>
                    <h1>Добро пожаловать на наш веб-сайт</h1>
                   \s
                    <h2>О нас</h2>
                    <p>Мы являемся ведущей компанией в области веб-разработки и предоставляем разнообразные услуги для наших клиентов.</p>
                   \s
                    <h2>Наши услуги</h2>
                    <ul>
                        <li>Веб-дизайн</li>
                        <li>Разработка веб-приложений</li>
                        <li>Оптимизация поисковой выдачи (SEO)</li>
                        <li>Интернет-маркетинг</li>
                    </ul>
                   \s
                    <h2>Наши проекты</h2>
                    <p>Мы гордимся своими успешными проектами, включая разработку крупных веб-приложений и рекламные кампании для различных компаний.</p>
                   \s
                    <h2>Контакты</h2>
                    <p>Вы можете связаться с нами по следующим контактам:</p>
                    <address>
                        Электронная почта: <a href="mailto:info@example.com">info@example.com</a><br>
                        Телефон: +7 (123) 456-7890<br>
                        Адрес: г. Примерово, ул. Примерная, 12345
                    </address>
                   \s
                    <p>Спасибо, что посетили наш веб-сайт!</p>
                </body>
                </html>
                """;
        String[] words = LemmaFinderService.extractVisibleText(input).trim().replaceAll("\\s{2,}|-+", " ").split("\\s");

        for (String word : words) {
            System.out.print(word + " ");
        }
    }
}
