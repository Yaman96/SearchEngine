package searchengine.services;

import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.List;
import java.util.Set;

public interface SnippetFinder {
    List<String> findSnippet(Set<Lemma> queryLemmas, Page page);
}
