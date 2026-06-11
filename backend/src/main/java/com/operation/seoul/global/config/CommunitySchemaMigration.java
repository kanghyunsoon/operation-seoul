package com.operation.seoul.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(3)
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
        createTableIfMissing("user_follow", """
                create table user_follow (
                    follower_id bigint not null,
                    following_id bigint not null,
                    created_at datetime not null default current_timestamp,
                    primary key (follower_id, following_id),
                    index idx_user_follow_following (following_id),
                    constraint fk_user_follow_follower foreign key (follower_id) references users (id) on delete cascade,
                    constraint fk_user_follow_following foreign key (following_id) references users (id) on delete cascade,
                    constraint chk_user_follow_not_self check (follower_id <> following_id)
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        createTableIfMissing("user_plans", """
                create table user_plans (
                    id bigint not null auto_increment,
                    user_id bigint not null,
                    episode_id bigint not null,
                    planned_at datetime not null,
                    memo varchar(500) null,
                    status varchar(32) not null default 'PLANNED',
                    created_at datetime not null default current_timestamp,
                    updated_at datetime null,
                    primary key (id),
                    unique key uk_user_plans_user_episode (user_id, episode_id),
                    index idx_user_plans_user_status_date (user_id, status, planned_at),
                    index idx_user_plans_episode (episode_id),
                    constraint fk_user_plans_user foreign key (user_id) references users (id) on delete cascade,
                    constraint fk_user_plans_episode foreign key (episode_id) references episodes (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        createTableIfMissing("user_groups", """
                create table user_groups (
                    id bigint not null auto_increment,
                    name varchar(80) not null,
                    description varchar(500) null,
                    owner_id bigint not null,
                    visibility varchar(32) not null default 'PUBLIC',
                    status varchar(32) not null default 'ACTIVE',
                    created_at datetime not null default current_timestamp,
                    updated_at datetime null,
                    primary key (id),
                    index idx_user_groups_owner (owner_id),
                    index idx_user_groups_status_created (status, created_at),
                    constraint fk_user_groups_owner foreign key (owner_id) references users (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        createTableIfMissing("user_group_members", """
                create table user_group_members (
                    group_id bigint not null,
                    user_id bigint not null,
                    role varchar(32) not null default 'MEMBER',
                    joined_at datetime not null default current_timestamp,
                    primary key (group_id, user_id),
                    index idx_user_group_members_user (user_id),
                    constraint fk_user_group_members_group foreign key (group_id) references user_groups (id) on delete cascade,
                    constraint fk_user_group_members_user foreign key (user_id) references users (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        createTableIfMissing("challenges", """
                create table challenges (
                    id bigint not null auto_increment,
                    title varchar(120) not null,
                    description varchar(700) null,
                    target_type varchar(40) not null default 'CLEAR_COUNT',
                    target_count int not null,
                    status varchar(32) not null default 'ACTIVE',
                    start_at datetime null,
                    end_at datetime null,
                    created_at datetime not null default current_timestamp,
                    updated_at datetime null,
                    primary key (id),
                    unique key uk_challenges_title (title),
                    index idx_challenges_status_period (status, start_at, end_at)
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        createTableIfMissing("user_challenge_entries", """
                create table user_challenge_entries (
                    challenge_id bigint not null,
                    user_id bigint not null,
                    status varchar(32) not null default 'JOINED',
                    joined_at datetime not null default current_timestamp,
                    completed_at datetime null,
                    primary key (challenge_id, user_id),
                    index idx_user_challenge_entries_user (user_id, status),
                    constraint fk_user_challenge_entries_challenge foreign key (challenge_id) references challenges (id) on delete cascade,
                    constraint fk_user_challenge_entries_user foreign key (user_id) references users (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        seedDefaultChallenges();
    }

    private void seedDefaultChallenges() {
        jdbcTemplate.update("""
                insert ignore into challenges (title, description, target_type, target_count, status)
                values
                ('첫 사건 클리어', '아무 미션 메모이나 1개 클리어하면 완료됩니다.', 'CLEAR_COUNT', 1, 'ACTIVE'),
                ('현장 요원 루키', '서로 다른 미션 메모 3개를 클리어하면 완료됩니다.', 'CLEAR_COUNT', 3, 'ACTIVE'),
                ('작전 베테랑', '미션 메모 5개 클리어를 목표로 하는 장기 챌린지입니다.', 'CLEAR_COUNT', 5, 'ACTIVE')
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
