package searchengine.repositories;

import searchengine.model.Lemma;

public interface LemmaJdbcRepository {

    void executeSql(String sql);
    Lemma insertOrUpdateLemma(Lemma lemma);
}
