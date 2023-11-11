package searchengine.repositories.impl;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import searchengine.repositories.IndexJdbcRepository;

@Repository
@AllArgsConstructor
public class IndexJdbcRepositoryImpl implements IndexJdbcRepository {

    private final JdbcTemplate jdbcTemplate;
    @Override
    public void executeSql(String sql) {
        jdbcTemplate.execute(sql);
    }
}
