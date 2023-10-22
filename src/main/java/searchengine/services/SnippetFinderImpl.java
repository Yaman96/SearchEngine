package searchengine.services;

import lombok.AllArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class SnippetFinderImpl implements SnippetFinder {

    private LemmaFinderService lemmaFinderService;

    @Override
    public ArrayList<String> findSnippet(Set<Lemma> queryLemmas, Page page) {
        System.out.println("findSnippet(Set<Lemma> queryLemmas " + queryLemmas);
        String htmlTextOnly = Jsoup.parse(page.getContent()).text();
        String[] sentences = htmlTextOnly.split("[.!?]");
        Set<String> queryLemmasStrings = queryLemmas.stream().map(Lemma::getLemma).collect(Collectors.toSet());

        List<String> queryFullMatchSnippet = new ArrayList<>();
        List<String> queryNotFullMatchSnippet = new ArrayList<>();

        for (int i = 0; i < sentences.length; i++) {
            Set<String> sentenceLemmaSet = lemmaFinderService.getLemmaSet(sentences[i]);
            StringBuilder snippetBuilder = new StringBuilder();
            if (sentenceLemmaSet.containsAll(queryLemmasStrings)) {
                prepareSnippet(sentences, i, snippetBuilder);
                queryFullMatchSnippet.add(snippetBuilder.toString());
            } else {
                prepareSnippet(sentences, i, snippetBuilder);
                queryNotFullMatchSnippet.add(snippetBuilder.toString());
            }
        }
        ArrayList<String> sortedSnippets = new ArrayList<>();
        sortedSnippets.addAll(queryFullMatchSnippet);
        sortedSnippets.addAll(queryNotFullMatchSnippet);
        return sortedSnippets;
    }

    private void prepareSnippet(String[] sentences, int i, StringBuilder snippetBuilder) {
        if (sentences.length >= 3 && i > 0 && i < sentences.length - 1) {
            snippetBuilder.append(sentences[i - 1]).append(sentences[i]).append(sentences[i + 1]);
        } else if (sentences.length >= 3 && i == 0) {
            snippetBuilder.append(sentences[i]).append(sentences[i + 1]).append(sentences[i + 2]);
        } else if (sentences.length >= 3 && i == sentences.length - 1) {
            snippetBuilder.append(sentences[i - 2]).append(sentences[i - 1]).append(sentences[i]);
        } else if (sentences.length == 2 && i == 0) {
            snippetBuilder.append(sentences[i]).append(sentences[i + 1]);
        } else if (sentences.length == 2) {
            snippetBuilder.append(sentences[i - 1]).append(sentences[i]);
        } else if (sentences.length == 1) {
            snippetBuilder.append(sentences[i]);
        }
    }
}
