package searchengine.repositories.impl;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.repositories.LemmaJdbcRepository;

import java.sql.*;

@Repository
@AllArgsConstructor
public class LemmaJdbcRepositoryImpl implements LemmaJdbcRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ConnectionPool connectionPool;

    @Override
    public void executeSql(String sql) {
        jdbcTemplate.execute(sql);
    }

    @Override
    public Lemma insertOrUpdateLemma(Lemma lemma) {
        long siteId = lemma.getSiteId();
        String lemmaString = lemma.getLemma();
        int frequency = lemma.getFrequency();

        String sql = "INSERT INTO lemma (site_id, lemma, frequency) " +
                "VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE frequency = frequency + 1";

        try (Connection connection = connectionPool.getDataSource().getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            preparedStatement.setLong(1, siteId);
            preparedStatement.setString(2, lemmaString);
            preparedStatement.setInt(3, frequency);

            int affectedRows = preparedStatement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("This query didn't affect any rows.");
            }

            try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long id = generatedKeys.getLong(1);

                    lemma.setId(id);
                    return lemma;
                } else {
                    throw new SQLException("Failed to get generated key.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lemma;
    }
}
