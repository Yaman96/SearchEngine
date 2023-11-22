package searchengine.utils;

import lombok.Getter;
import org.springframework.stereotype.Component;
import searchengine.model.Lemma;

import java.util.ArrayList;
import java.util.List;

@Getter
@Component
public class CustomLemmaList {

    private final List<Lemma> list = new ArrayList<>();


    public void add(Lemma lemma) {
        if(list.contains(lemma)) {
            int lemmaIndex = list.indexOf(lemma);
            Lemma lemmaFromList = list.get(lemmaIndex);
            lemmaFromList.incrementFrequency();
        } else {
            list.add(lemma);
        }
    }

    public void clear() {
        list.clear();
    }
}
