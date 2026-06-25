-- MyBatis 전환 후 Hibernate DDL 자동 생성이 사라지므로 로컬/시연 DB 초기화를 SQL로 관리합니다.
-- 모든 문장은 IF NOT EXISTS 기반이라 기존 데이터가 있는 테이블을 삭제하거나 재생성하지 않습니다.

create table if not exists users (
    id bigint not null auto_increment,
    email varchar(255) not null,
    password varchar(255) not null,
    nickname varchar(255) not null,
    is_admin boolean not null default false,
    role varchar(32) not null default 'ROLE_USER',
    profile_image_url mediumtext null,
    status_message varchar(120) null,
    profile_public boolean not null default true,
    status varchar(32) not null default 'ACTIVE',
    created_at datetime not null default current_timestamp,
    updated_at datetime null,
    primary key (id),
    unique key uk_users_email (email)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists user_social_accounts (
    id bigint not null auto_increment,
    user_id bigint not null,
    provider varchar(32) not null,
    provider_user_id varchar(255) not null,
    created_at datetime not null default current_timestamp,
    primary key (id),
    unique key uk_social_provider_user (provider, provider_user_id),
    unique key uk_social_user_provider (user_id, provider),
    constraint fk_social_account_user
        foreign key (user_id) references users (id)
        on delete cascade
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
    is_notice boolean not null default false,
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

create table if not exists episodes (
    id bigint not null auto_increment,
    title varchar(255) not null,
    subtitle varchar(255) null,
    region_id bigint null,
    era varchar(100) null,
    genre varchar(100) null,
    difficulty varchar(32) null,
    estimated_time varchar(100) null,
    estimated_distance varchar(100) null,
    fiction_synopsis text null,
    mission_description text null,
    final_answer_type varchar(64) null,
    final_answer varchar(255) null,
    final_answer_aliases varchar(1000) null,
    final_question text null,
    final_truth_summary text null,
    actual_history_summary text null,
    deduction_secret_facts text null,
    deduction_forbidden_reveals text null,
    max_deduction_questions int not null default 20,
    status varchar(32) not null default 'DRAFT',
    created_at datetime not null default current_timestamp,
    updated_at datetime null,
    primary key (id),
    unique key uk_episodes_title (title),
    index idx_episodes_status (status)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists player_analysis (
    id bigint not null auto_increment,
    user_id bigint not null,
    mission_id bigint not null,
    player_type varchar(50) null,
    summary varchar(255) null,
    strength varchar(255) null,
    weakness varchar(255) null,
    recommendation varchar(255) null,
    created_at datetime not null default current_timestamp,
    primary key (id),
    index idx_player_analysis_user_created (user_id, created_at),
    index idx_player_analysis_mission (mission_id),
    constraint fk_player_analysis_user
        foreign key (user_id) references users (id)
        on delete cascade,
    constraint fk_player_analysis_mission
        foreign key (mission_id) references episodes (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists player_analysis_mbti (
    id bigint not null auto_increment,
    analysis_id bigint not null,
    dimension varchar(50) not null,
    left_label varchar(50) not null,
    right_label varchar(50) not null,
    left_percent int not null,
    right_percent int not null,
    primary key (id),
    index idx_player_analysis_mbti_analysis (analysis_id),
    constraint fk_player_analysis_mbti_analysis
        foreign key (analysis_id) references player_analysis (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists reasoning_answer (
    id bigint not null auto_increment,
    user_id bigint not null,
    mission_id bigint not null,
    question text not null,
    answer text not null,
    created_at datetime not null default current_timestamp,
    primary key (id),
    index idx_reasoning_answer_user_mission (user_id, mission_id),
    constraint fk_reasoning_answer_user
        foreign key (user_id) references users (id)
        on delete cascade,
    constraint fk_reasoning_answer_mission
        foreign key (mission_id) references episodes (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists episode_reviews (
    id bigint not null auto_increment,
    episode_id bigint not null,
    user_id bigint not null,
    rating int not null,
    difficulty_rating int not null,
    content text not null,
    spoiler boolean not null default false,
    status varchar(32) not null default 'VISIBLE',
    created_at datetime not null default current_timestamp,
    updated_at datetime null,
    primary key (id),
    unique key uk_episode_reviews_episode_user (episode_id, user_id),
    index idx_episode_reviews_episode_created (episode_id, created_at),
    index idx_episode_reviews_episode_rating (episode_id, rating),
    constraint fk_episode_reviews_episode
        foreign key (episode_id) references episodes (id)
        on delete cascade,
    constraint fk_episode_reviews_user
        foreign key (user_id) references users (id)
        on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists episode_review_comments (
    id bigint not null auto_increment,
    review_id bigint not null,
    user_id bigint not null,
    content text not null,
    spoiler boolean not null default false,
    status varchar(32) not null default 'VISIBLE',
    created_at datetime not null default current_timestamp,
    updated_at datetime null,
    primary key (id),
    index idx_episode_review_comments_review_created (review_id, created_at),
    index idx_episode_review_comments_user (user_id),
    constraint fk_episode_review_comments_review
        foreign key (review_id) references episode_reviews (id)
        on delete cascade,
    constraint fk_episode_review_comments_user
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
