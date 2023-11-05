package searchengine.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jetbrains.annotations.NotNull;

import jakarta.persistence.*;

@Entity
@Data
@NoArgsConstructor
public class Page implements Comparable<Page>{

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

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    @Transient
    private double relevance;

    public Page(String path, int code, String content, Site site) {
        this.path = path;
        this.code = code;
        this.content = content;
        this.site = site;
    }

    @Override
    public String toString() {
        return "Page{" +
                "site=" + site.getName() +
                ", path='" + path + '\'' +
                ", code=" + code +
                ", content length='" + content.length() + '\'' +
                ", relevance=" + relevance +
                '}';
    }

    @Override
    public int compareTo(@NotNull Page o) {
        if (relevance == o.relevance) {
            return path.compareTo(o.path);
        }
        return -Double.compare(relevance, o.relevance);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Page page = (Page) o;

        return new EqualsBuilder().append(code, page.code).append(path, page.path).append(content, page.content).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(path).append(code).append(content).toHashCode();
    }
}
