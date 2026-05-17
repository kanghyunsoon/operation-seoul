package com.operation.seoul.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegionSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        addColumnIfMissing("period_code", "alter table region add column period_code varchar(32) not null default 'mixed'");
        addColumnIfMissing("theme_code", "alter table region add column theme_code varchar(32) not null default 'mystery'");
        addColumnIfMissing("created_at", "alter table region add column created_at datetime not null default current_timestamp");
        addIndexIfMissing("idx_region_period_theme", "create index idx_region_period_theme on region (period_code, theme_code)");
        addIndexIfMissing("idx_region_created_at", "create index idx_region_created_at on region (created_at)");
    }

    private void addColumnIfMissing(String columnName, String sql) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = database()
                  and table_name = 'region'
                  and column_name = ?
                """, Integer.class, columnName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(sql);
        }
    }

    private void addIndexIfMissing(String indexName, String sql) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.statistics
                where table_schema = database()
                  and table_name = 'region'
                  and index_name = ?
                """, Integer.class, indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(sql);
        }
    }
}
