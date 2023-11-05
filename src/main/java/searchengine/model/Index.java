package searchengine.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Entity
@NoArgsConstructor
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

    @Column(columnDefinition = "FLOAT", nullable = false, name = "rank_1")
    private float rank;

    public Index(long pageId, long lemmaId, float rank) {
        this.pageId = pageId;
        this.lemmaId = lemmaId;
        this.rank = rank;
    }
}
