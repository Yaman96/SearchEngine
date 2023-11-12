package searchengine.services.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.Lemma;
import searchengine.repositories.LemmaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@AllArgsConstructor
public class LemmaService {

    private LemmaRepository lemmaRepository;

    public Optional<Lemma> findByLemmaEquals(String lemma) {
        return lemmaRepository.findByLemmaEquals(lemma);
    }

    public Optional<List<Lemma>> findAllByLemma(String lemma) {
        return lemmaRepository.findAllByLemma(lemma);
    }

    public Optional<Lemma> findByLemmaAndSiteId(String lemma, long siteId) {
        return lemmaRepository.findByLemmaAndSiteId(lemma,siteId);
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
        return lemmaRepository.save(lemma);
    }

    public void saveAll(Set<Lemma> lemmas) {
        lemmaRepository.saveAll(lemmas);
    }
}
