package searchengine.services.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.Lemma;
import searchengine.repositories.LemmaJdbcRepository;
import searchengine.repositories.LemmaRepository;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class LemmaService {

    private LemmaRepository lemmaRepository;

    private LemmaJdbcRepository lemmaJdbcRepository;

    public Optional<Lemma> findByLemmaEquals(String lemma) {
        return lemmaRepository.findByLemmaEquals(lemma);
    }

    public Optional<List<Lemma>> findAllByLemmaWithFrequencyLessThan(String lemma, int frequency) {
        return lemmaRepository.findAllByLemmaWithFrequencyLessThan(lemma, frequency);
    }

    public Optional<Lemma> findByLemmaAndSiteIdAndFrequencyLessThan(String lemma, long siteId, int frequency) {
        return lemmaRepository.findByLemmaAndSiteIdAndFrequencyLessThan(lemma, siteId, frequency);
    }

    public Optional<Lemma> findById(long lemmaId) {
        return lemmaRepository.findById(lemmaId);
    }

    public void deleteAllBySiteId(long siteId) {
        lemmaRepository.deleteAllBySiteId(siteId);
    }

    public void deleteAllLemmas() {
        lemmaRepository.deleteAllLemmas();
    }

    public int countAllBySiteId(long siteId) {
        return lemmaRepository.countAllBySiteId(siteId);
    }

    public Lemma save(Lemma lemma) {
        return lemmaJdbcRepository.insertOrUpdateLemma(lemma);
    }

    public void saveAll(List<Lemma> lemmas) {
        executeSql(createSqlInsertStatement(createValuesForSqlStatement(lemmas)));
    }

    public void executeSql(String sql) {
        lemmaJdbcRepository.executeSql(sql);
    }

    private String createSqlInsertStatement(String values) {
        return "INSERT INTO lemma (site_id, lemma, frequency) VALUES " + values +
                " ON DUPLICATE KEY UPDATE frequency = frequency + 1";
    }

    private String createValuesForSqlStatement(List<Lemma> lemmas) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lemmas.size(); i++) {
            Lemma currentLemma = lemmas.get(i);
            if (i == lemmas.size() - 1) {
                result.append("(").append(currentLemma.getSiteId()).append(", '")
                        .append(currentLemma.getLemma()).append("', ")
                        .append(currentLemma.getFrequency()).append(")");
                break;
            }
            result.append("(").append(currentLemma.getSiteId()).append(", '")
                    .append(currentLemma.getLemma()).append("', ")
                    .append(currentLemma.getFrequency()).append("), ");
        }
        return result.toString();
    }

    public int getFrequencySum(long siteId) {
        return lemmaRepository.getFrequencySum(siteId);
    }

    public int getFrequencySum() {
        return lemmaRepository.getFrequencySum();
    }
}
