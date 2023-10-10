package searchengine.model;

import lombok.Data;

import javax.persistence.*;

@Entity
@Data
public class Lemma {

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
}
