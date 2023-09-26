package searchengine.model;

import lombok.Data;

import javax.persistence.*;

@Entity
@org.hibernate.annotations.Table(appliesTo = "page", indexes = @org.hibernate.annotations.Index(name = "ind", columnNames = "path"))
@Data
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT")
    private long id;

    @ManyToOne
    @JoinColumn(name = "site_id", columnDefinition = "INT", nullable = false)
    private Site site;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String path;

    @Column(columnDefinition = "INT", nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}
