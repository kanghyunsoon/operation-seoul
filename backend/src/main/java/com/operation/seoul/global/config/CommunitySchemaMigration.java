package com.operation.seoul.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommunitySchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        createTableIfMissing("region_review", """
                create table region_review (
                    id bigint not null auto_increment,
                    region_id bigint not null,
                    user_id bigint not null,
                    rating int not null,
                    content text not null,
                    created_at datetime not null default current_timestamp,
                    updated_at datetime null,
                    primary key (id),
                    unique key uk_region_review_region_user (region_id, user_id),
                    index idx_region_review_region_created (region_id, created_at),
                    index idx_region_review_region_rating (region_id, rating),
                    constraint fk_region_review_region foreign key (region_id) references region (id) on delete cascade,
                    constraint fk_region_review_user foreign key (user_id) references users (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        createTableIfMissing("region_question", """
                create table region_question (
                    id bigint not null auto_increment,
                    region_id bigint not null,
                    user_id bigint not null,
                    title varchar(255) not null,
                    content text not null,
                    created_at datetime not null default current_timestamp,
                    updated_at datetime null,
                    primary key (id),
                    index idx_region_question_region_created (region_id, created_at),
                    constraint fk_region_question_region foreign key (region_id) references region (id) on delete cascade,
                    constraint fk_region_question_user foreign key (user_id) references users (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        createTableIfMissing("region_answer", """
                create table region_answer (
                    id bigint not null auto_increment,
                    question_id bigint not null,
                    user_id bigint not null,
                    content text not null,
                    created_at datetime not null default current_timestamp,
                    updated_at datetime null,
                    primary key (id),
                    index idx_region_answer_question_created (question_id, created_at),
                    constraint fk_region_answer_question foreign key (question_id) references region_question (id) on delete cascade,
                    constraint fk_region_answer_user foreign key (user_id) references users (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
    }

    private void createTableIfMissing(String tableName, String sql) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = database()
                  and table_name = ?
                """, Integer.class, tableName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(sql);
        }
    }
}
