-- MyBatis 전환 후 Hibernate DDL 자동 생성이 사라지므로 로컬/시연 DB 초기화를 SQL로 관리합니다.
-- 모든 문장은 IF NOT EXISTS 기반이라 기존 데이터가 있는 테이블을 삭제하거나 재생성하지 않습니다.

create table if not exists users (
    id bigint not null auto_increment,
    email varchar(255) not null,
    password varchar(255) not null,
    nickname varchar(255) not null,
    is_admin boolean not null default false,
    primary key (id),
    unique key uk_users_email (email)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists region (
    id bigint not null auto_increment,
    name varchar(255),
    area_code varchar(32) default 'seoul',
    description text,
    period_code varchar(32) not null default 'mixed',
    theme_code varchar(32) not null default 'mystery',
    created_at datetime not null default current_timestamp,
    primary key (id),
    index idx_region_area_code (area_code),
    index idx_region_period_theme (period_code, theme_code),
    index idx_region_created_at (created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists mission (
    id bigint not null auto_increment,
    region_id bigint,
    title varchar(255),
    description text,
    target_lat double,
    target_lng double,
    radius_in_meters double,
    vision_keyword varchar(255),
    clue text,
    answer_keyword varchar(255),
    chapter_id bigint,
    is_final boolean not null default false,
    real_story varchar(2000),
    primary key (id),
    index idx_mission_region_id (region_id),
    index idx_mission_region_final (region_id, is_final),
    constraint fk_mission_region
        foreign key (region_id) references region (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists game_session (
    id bigint not null auto_increment,
    user_id bigint not null,
    mission_id bigint not null,
    status varchar(50) not null,
    extracted_log text,
    started_at datetime,
    cleared_at datetime,
    elapsed_seconds bigint,
    route_distance_meters double,
    score int,
    primary key (id),
    unique key uk_game_session_user_mission (user_id, mission_id),
    index idx_game_session_mission_id (mission_id),
    constraint fk_game_session_user
        foreign key (user_id) references users (id)
        on delete cascade,
    constraint fk_game_session_mission
        foreign key (mission_id) references mission (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists clear_report (
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
    constraint fk_clear_report_user
        foreign key (user_id) references users (id)
        on delete cascade,
    constraint fk_clear_report_mission
        foreign key (mission_id) references mission (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists region_review (
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
    constraint fk_region_review_region
        foreign key (region_id) references region (id)
        on delete cascade,
    constraint fk_region_review_user
        foreign key (user_id) references users (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists region_question (
    id bigint not null auto_increment,
    region_id bigint not null,
    user_id bigint not null,
    title varchar(255) not null,
    content text not null,
    created_at datetime not null default current_timestamp,
    updated_at datetime null,
    primary key (id),
    index idx_region_question_region_created (region_id, created_at),
    constraint fk_region_question_region
        foreign key (region_id) references region (id)
        on delete cascade,
    constraint fk_region_question_user
        foreign key (user_id) references users (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists region_answer (
    id bigint not null auto_increment,
    question_id bigint not null,
    user_id bigint not null,
    content text not null,
    created_at datetime not null default current_timestamp,
    updated_at datetime null,
    primary key (id),
    index idx_region_answer_question_created (question_id, created_at),
    constraint fk_region_answer_question
        foreign key (question_id) references region_question (id)
        on delete cascade,
    constraint fk_region_answer_user
        foreign key (user_id) references users (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists region_question_like (
    question_id bigint not null,
    user_id bigint not null,
    created_at datetime not null default current_timestamp,
    primary key (question_id, user_id),
    index idx_region_question_like_user (user_id),
    constraint fk_region_question_like_question
        foreign key (question_id) references region_question (id)
        on delete cascade,
    constraint fk_region_question_like_user
        foreign key (user_id) references users (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists region_review_like (
    review_id bigint not null,
    user_id bigint not null,
    created_at datetime not null default current_timestamp,
    primary key (review_id, user_id),
    index idx_region_review_like_user (user_id),
    constraint fk_region_review_like_review
        foreign key (review_id) references region_review (id)
        on delete cascade,
    constraint fk_region_review_like_user
        foreign key (user_id) references users (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists region_like (
    region_id bigint not null,
    user_id bigint not null,
    created_at datetime not null default current_timestamp,
    primary key (region_id, user_id),
    index idx_region_like_user (user_id),
    constraint fk_region_like_region
        foreign key (region_id) references region (id)
        on delete cascade,
    constraint fk_region_like_user
        foreign key (user_id) references users (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists region_favorite (
    region_id bigint not null,
    user_id bigint not null,
    created_at datetime not null default current_timestamp,
    primary key (region_id, user_id),
    index idx_region_favorite_user (user_id),
    constraint fk_region_favorite_region
        foreign key (region_id) references region (id)
        on delete cascade,
    constraint fk_region_favorite_user
        foreign key (user_id) references users (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists user_follow (
    follower_id bigint not null,
    following_id bigint not null,
    created_at datetime not null default current_timestamp,
    primary key (follower_id, following_id),
    index idx_user_follow_following (following_id),
    constraint fk_user_follow_follower
        foreign key (follower_id) references users (id)
        on delete cascade,
    constraint fk_user_follow_following
        foreign key (following_id) references users (id)
        on delete cascade,
    constraint chk_user_follow_not_self
        check (follower_id <> following_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists user_plans (
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
    constraint fk_user_plans_user
        foreign key (user_id) references users (id)
        on delete cascade,
    constraint fk_user_plans_episode
        foreign key (episode_id) references episodes (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists user_groups (
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
    constraint fk_user_groups_owner
        foreign key (owner_id) references users (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists user_group_members (
    group_id bigint not null,
    user_id bigint not null,
    role varchar(32) not null default 'MEMBER',
    joined_at datetime not null default current_timestamp,
    primary key (group_id, user_id),
    index idx_user_group_members_user (user_id),
    constraint fk_user_group_members_group
        foreign key (group_id) references user_groups (id)
        on delete cascade,
    constraint fk_user_group_members_user
        foreign key (user_id) references users (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists challenges (
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
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists user_challenge_entries (
    challenge_id bigint not null,
    user_id bigint not null,
    status varchar(32) not null default 'JOINED',
    joined_at datetime not null default current_timestamp,
    completed_at datetime null,
    primary key (challenge_id, user_id),
    index idx_user_challenge_entries_user (user_id, status),
    constraint fk_user_challenge_entries_challenge
        foreign key (challenge_id) references challenges (id)
        on delete cascade,
    constraint fk_user_challenge_entries_user
        foreign key (user_id) references users (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
