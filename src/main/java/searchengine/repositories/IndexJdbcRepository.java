package searchengine.repositories;

public interface IndexJdbcRepository {

    void executeSql(String sql);
}
