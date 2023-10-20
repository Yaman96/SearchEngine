package searchengine.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import javax.persistence.*;

@Entity
@Data
@NoArgsConstructor
public class Lemma implements Comparable<Lemma>{

    public Lemma(long siteId, String lemma, int frequency) {
        this.siteId = siteId;
        this.lemma = lemma;
        this.frequency = frequency;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT")
    private long id;

    @Column(columnDefinition = "INT", nullable = false, name = "site_id")
    private long siteId;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String lemma;

    @Column(columnDefinition = "INT", nullable = false)
    private int frequency;

    public void incrementFrequency() {
        frequency++;
    }

    @Override
    public int compareTo(@NotNull Lemma o) {
        return -Integer.compare(frequency,o.frequency);
    }
}
