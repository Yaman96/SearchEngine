package searchengine.model;

import lombok.Data;

import javax.persistence.*;

@Entity
@Data
@Table(name = "index_1")
public class Index {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT")
    private long id;

    @Column(columnDefinition = "INT", nullable = false, name = "page_id")
    private long pageId;

    @Column(columnDefinition = "INT", nullable = false, name = "lemma_id")
    private long lemmaId;

    @Column(columnDefinition = "FLOAT", nullable = false, name = "site_id")
    private float rank;
}
