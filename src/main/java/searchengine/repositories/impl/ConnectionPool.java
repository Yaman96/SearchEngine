package searchengine.repositories.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class ConnectionPool {

    private final HikariConfig config = new HikariConfig();
    private final HikariDataSource hikariDataSource;

    {
        config.setJdbcUrl("jdbc:mysql://localhost:3306/search_engine?useSSL=false&requireSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8");
        config.setUsername("root");
        config.setPassword("10081996Ym");
        config.setMinimumIdle(10);
        config.setMaximumPoolSize(120);
        config.setConnectionTimeout(40000);
        config.setMaxLifetime(1800000);
        config.setSchema("search_engine");
        hikariDataSource = new HikariDataSource(config);
    }

    public DataSource getDataSource() {
        return hikariDataSource;
    }
}
