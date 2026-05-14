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
    primary key (id),
    index idx_region_area_code (area_code)
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
