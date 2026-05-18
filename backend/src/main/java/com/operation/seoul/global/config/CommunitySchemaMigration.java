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
        createTableIfMissing("clear_report", """
                create table clear_report (
                    id bigint not null auto_increment,
                    user_id bigint not null,
                    mission_id bigint not null,
                    report text not null,
                    clue_explanations_json mediumtext,
                    created_at datetime not null default current_timestamp,
                    updated_at datetime null,
                    primary key (id),
                    unique key uk_clear_report_user_mission (user_id, mission_id),
                    index idx_clear_report_mission_id (mission_id),
                    constraint fk_clear_report_user foreign key (user_id) references users (id) on delete cascade,
                    constraint fk_clear_report_mission foreign key (mission_id) references mission (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        createTableIfMissing("region_question_like", """
                create table region_question_like (
                    question_id bigint not null,
                    user_id bigint not null,
                    created_at datetime not null default current_timestamp,
                    primary key (question_id, user_id),
                    index idx_region_question_like_user (user_id),
                    constraint fk_region_question_like_question foreign key (question_id) references region_question (id) on delete cascade,
                    constraint fk_region_question_like_user foreign key (user_id) references users (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        createTableIfMissing("region_review_like", """
                create table region_review_like (
                    review_id bigint not null,
                    user_id bigint not null,
                    created_at datetime not null default current_timestamp,
                    primary key (review_id, user_id),
                    index idx_region_review_like_user (user_id),
                    constraint fk_region_review_like_review foreign key (review_id) references region_review (id) on delete cascade,
                    constraint fk_region_review_like_user foreign key (user_id) references users (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        createTableIfMissing("region_like", """
                create table region_like (
                    region_id bigint not null,
                    user_id bigint not null,
                    created_at datetime not null default current_timestamp,
                    primary key (region_id, user_id),
                    index idx_region_like_user (user_id),
                    constraint fk_region_like_region foreign key (region_id) references region (id) on delete cascade,
                    constraint fk_region_like_user foreign key (user_id) references users (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        createTableIfMissing("region_favorite", """
                create table region_favorite (
                    region_id bigint not null,
                    user_id bigint not null,
                    created_at datetime not null default current_timestamp,
                    primary key (region_id, user_id),
                    index idx_region_favorite_user (user_id),
                    constraint fk_region_favorite_region foreign key (region_id) references region (id) on delete cascade,
                    constraint fk_region_favorite_user foreign key (user_id) references users (id) on delete cascade
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
