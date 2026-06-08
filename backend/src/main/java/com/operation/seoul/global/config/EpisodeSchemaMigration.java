package com.operation.seoul.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Order(2)
@RequiredArgsConstructor
public class EpisodeSchemaMigration implements ApplicationRunner {
    private static final String SAMPLE_TITLE = "EP.01 죽음을 비추는 렌즈";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        migrateUsers();
        createEpisodeTables();
        createFavoriteTables();
        addColumns();
        createCaseFileTables();
        seedSampleEpisode();
    }

    private void migrateUsers() {
        addColumnIfMissing("users", "role", "alter table users add column role varchar(32) not null default 'ROLE_USER'");
        addColumnIfMissing("users", "profile_image_url", "alter table users add column profile_image_url varchar(1000) null");
        addColumnIfMissing("users", "status", "alter table users add column status varchar(32) not null default 'ACTIVE'");
        addColumnIfMissing("users", "created_at", "alter table users add column created_at datetime not null default current_timestamp");
        addColumnIfMissing("users", "updated_at", "alter table users add column updated_at datetime null");
        jdbcTemplate.update("update users set role = case when is_admin = true then 'ROLE_ADMIN' else 'ROLE_USER' end where role is null or role = ''");
        jdbcTemplate.update("update users set status = 'ACTIVE' where status is null or status = ''");
    }

    private void createEpisodeTables() {
        jdbcTemplate.execute("""
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
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                create table if not exists mission_spots (
                    id bigint not null auto_increment,
                    episode_id bigint not null,
                    place_name varchar(255) not null,
                    address varchar(500) null,
                    latitude double not null,
                    longitude double not null,
                    marker_type varchar(40) not null,
                    clue_role varchar(40) not null,
                    public_marker_type varchar(40) not null,
                    story_text text null,
                    arrival_radius double not null default 50,
                    is_final_place boolean not null default false,
                    created_at datetime not null default current_timestamp,
                    updated_at datetime null,
                    primary key (id),
                    index idx_mission_spots_episode (episode_id),
                    constraint fk_mission_spots_episode foreign key (episode_id) references episodes (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                create table if not exists puzzles (
                    id bigint not null auto_increment,
                    mission_spot_id bigint not null,
                    puzzle_type varchar(40) not null,
                    question_text text not null,
                    answer varchar(255) not null,
                    answer_format varchar(40) null,
                    reward_clue varchar(500) not null,
                    difficulty varchar(32) null,
                    created_at datetime not null default current_timestamp,
                    updated_at datetime null,
                    primary key (id),
                    index idx_puzzles_spot (mission_spot_id),
                    constraint fk_puzzles_spot foreign key (mission_spot_id) references mission_spots (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                create table if not exists puzzle_hints (
                    id bigint not null auto_increment,
                    puzzle_id bigint not null,
                    hint_level int not null,
                    hint_text text not null,
                    primary key (id),
                    index idx_puzzle_hints_puzzle (puzzle_id),
                    constraint fk_puzzle_hints_puzzle foreign key (puzzle_id) references puzzles (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                create table if not exists user_episode_progress (
                    id bigint not null auto_increment,
                    user_id bigint not null,
                    episode_id bigint not null,
                    visited_spot_ids text null,
                    completed_spot_ids text null,
                    collected_answer_clues text null,
                    collected_destination_clues text null,
                    collected_story_clues text null,
                    final_arrived_spot_id bigint null,
                    hint_used_count int not null default 0,
                    wrong_answer_count int not null default 0,
                    deduction_question_count int not null default 0,
                    final_guess_count int not null default 0,
                    score int null,
                    started_at datetime null,
                    cleared_at datetime null,
                    status varchar(32) not null default 'NOT_STARTED',
                    primary key (id),
                    unique key uk_user_episode_progress (user_id, episode_id),
                    index idx_user_episode_progress_status (episode_id, status),
                    constraint fk_user_episode_progress_user foreign key (user_id) references users (id) on delete cascade,
                    constraint fk_user_episode_progress_episode foreign key (episode_id) references episodes (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                create table if not exists final_deduction_sessions (
                    id bigint not null auto_increment,
                    user_id bigint not null,
                    episode_id bigint not null,
                    started_at datetime not null default current_timestamp,
                    completed_at datetime null,
                    question_count int not null default 0,
                    final_guess_count int not null default 0,
                    status varchar(32) not null default 'OPEN',
                    primary key (id),
                    index idx_final_deduction_user_episode (user_id, episode_id),
                    constraint fk_final_deduction_user foreign key (user_id) references users (id) on delete cascade,
                    constraint fk_final_deduction_episode foreign key (episode_id) references episodes (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                create table if not exists final_deduction_questions (
                    id bigint not null auto_increment,
                    session_id bigint not null,
                    user_question text not null,
                    ai_answer_type varchar(40) not null,
                    ai_answer_text text not null,
                    created_at datetime not null default current_timestamp,
                    primary key (id),
                    index idx_final_deduction_questions_session (session_id),
                    constraint fk_final_deduction_questions_session foreign key (session_id) references final_deduction_sessions (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
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
                    unique key uk_episode_review_episode_user (episode_id, user_id),
                    index idx_episode_review_episode_status (episode_id, status),
                    constraint fk_episode_review_episode foreign key (episode_id) references episodes (id) on delete cascade,
                    constraint fk_episode_review_user foreign key (user_id) references users (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
    }

    private void createFavoriteTables() {
        jdbcTemplate.execute("""
                create table if not exists episode_favorites (
                    id bigint not null auto_increment,
                    user_id bigint not null,
                    episode_id bigint not null,
                    created_at datetime not null default current_timestamp,
                    primary key (id),
                    unique key uk_episode_favorite_user_episode (user_id, episode_id),
                    index idx_episode_favorites_user (user_id),
                    index idx_episode_favorites_episode (episode_id),
                    constraint fk_episode_favorites_user foreign key (user_id) references users (id) on delete cascade,
                    constraint fk_episode_favorites_episode foreign key (episode_id) references episodes (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
    }

    private void addColumns() {
        addColumnIfMissing("episodes", "recommended_players", "alter table episodes add column recommended_players varchar(100) null");
        addColumnIfMissing("episodes", "team_role_guide", "alter table episodes add column team_role_guide text null");
        addColumnIfMissing("episodes", "notice_text", "alter table episodes add column notice_text text null");
        addColumnIfMissing("puzzles", "reward_payload", "alter table puzzles add column reward_payload text null");
        addColumnIfMissing("user_episode_progress", "last_played_at", "alter table user_episode_progress add column last_played_at datetime null");
        addColumnIfMissing("user_episode_progress", "unlocked_suspect_ids", "alter table user_episode_progress add column unlocked_suspect_ids text null");
        addColumnIfMissing("user_episode_progress", "cleared_suspect_ids", "alter table user_episode_progress add column cleared_suspect_ids text null");
        addColumnIfMissing("user_episode_progress", "unlocked_evidence_ids", "alter table user_episode_progress add column unlocked_evidence_ids text null");
    }

    private void createCaseFileTables() {
        jdbcTemplate.execute("""
                create table if not exists case_suspects (
                    id bigint not null auto_increment,
                    episode_id bigint not null,
                    display_name varchar(255) not null,
                    alias varchar(100) null,
                    short_description text null,
                    portrait_image_url varchar(1000) null,
                    relation_to_victim varchar(500) null,
                    suspicious_point text null,
                    alibi_summary text null,
                    unlocked_by_default boolean not null default false,
                    display_order int not null default 0,
                    created_at datetime not null default current_timestamp,
                    primary key (id),
                    index idx_case_suspects_episode (episode_id),
                    constraint fk_case_suspects_episode foreign key (episode_id) references episodes (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                create table if not exists case_evidences (
                    id bigint not null auto_increment,
                    episode_id bigint not null,
                    title varchar(255) not null,
                    type varchar(60) not null,
                    image_url varchar(1000) null,
                    text_summary text null,
                    source_spot_id bigint null,
                    related_suspect_id bigint null,
                    related_clue_type varchar(60) null,
                    unlocked_by_default boolean not null default false,
                    display_order int not null default 0,
                    created_at datetime not null default current_timestamp,
                    primary key (id),
                    index idx_case_evidences_episode (episode_id),
                    constraint fk_case_evidences_episode foreign key (episode_id) references episodes (id) on delete cascade,
                    constraint fk_case_evidences_spot foreign key (source_spot_id) references mission_spots (id) on delete set null,
                    constraint fk_case_evidences_suspect foreign key (related_suspect_id) references case_suspects (id) on delete set null
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                create table if not exists episode_partner_rewards (
                    id bigint not null auto_increment,
                    episode_id bigint not null,
                    title varchar(255) not null,
                    description text null,
                    reward_type varchar(60) not null,
                    partner_name varchar(255) null,
                    location_name varchar(255) null,
                    latitude double null,
                    longitude double null,
                    status varchar(32) not null default 'DISABLED',
                    created_at datetime not null default current_timestamp,
                    primary key (id),
                    index idx_episode_partner_rewards_episode (episode_id),
                    constraint fk_episode_partner_rewards_episode foreign key (episode_id) references episodes (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
    }

    private void seedSampleEpisode() {
        Long episodeId = findEpisodeIdByTitle(SAMPLE_TITLE);
        if (episodeId == null) {
            jdbcTemplate.update("""
                    insert into episodes (title, subtitle, era, genre, difficulty, estimated_time, estimated_distance,
                    fiction_synopsis, final_answer_type, final_answer, final_answer_aliases, final_question,
                    final_truth_summary, actual_history_summary, deduction_secret_facts, deduction_forbidden_reveals,
                    max_deduction_questions, recommended_players, team_role_guide, notice_text, status)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 20, ?, ?, ?, 'PUBLISHED')
                    """, SAMPLE_TITLE, "마지막 필름에 남은 그림자", "대한제국 말기", "야외 방탈출 / 추리 / 현장 관찰", "NORMAL",
                    "약 3시간", "약 2.4km", fictionSynopsis(), "WEAPON", "깨진 렌즈", "깨진렌즈,부서진 렌즈,렌즈 조각",
                    "사진사를 죽음으로 이끈 진짜 흉기는 무엇인가?", finalTruth(), historySummary(), secretFacts(), forbiddenReveals(),
                    "2~4명", teamGuide(), noticeText());
            episodeId = findEpisodeIdByTitle(SAMPLE_TITLE);
        } else {
            jdbcTemplate.update("""
                    update episodes set subtitle=?, era=?, genre=?, difficulty=?, estimated_time=?, estimated_distance=?,
                    fiction_synopsis=?, final_answer_type=?, final_answer=?, final_answer_aliases=?, final_question=?,
                    final_truth_summary=?, actual_history_summary=?, deduction_secret_facts=?, deduction_forbidden_reveals=?,
                    max_deduction_questions=20, recommended_players=?, team_role_guide=?, notice_text=?, status='PUBLISHED',
                    updated_at=current_timestamp where id=?
                    """, "마지막 필름에 남은 그림자", "대한제국 말기", "야외 방탈출 / 추리 / 현장 관찰", "NORMAL",
                    "약 3시간", "약 2.4km", fictionSynopsis(), "WEAPON", "깨진 렌즈", "깨진렌즈,부서진 렌즈,렌즈 조각",
                    "사진사를 죽음으로 이끈 진짜 흉기는 무엇인가?", finalTruth(), historySummary(), secretFacts(), forbiddenReveals(),
                    "2~4명", teamGuide(), noticeText(), episodeId);
        }
        seedSpotsAndPuzzlesIfMissing(episodeId);
        seedCaseDataIfMissing(episodeId);
        seedRewardPayloads(episodeId);
    }

    private void seedSpotsAndPuzzlesIfMissing(Long episodeId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from mission_spots where episode_id = ?", Integer.class, episodeId);
        if (count != null && count > 0) return;
        List<Object[]> spots = List.of(
                spot(episodeId, "덕수궁 대한문", "서울 중구 세종대로 99", 37.565804, 126.975146, "START", "START", "START", "첫 사진은 굳게 닫힌 문 앞에서 시작된다.", false),
                spot(episodeId, "덕수궁 돌담길", "서울 중구 정동길", 37.566258, 126.973766, "ANSWER_HINT", "ANSWER_HINT", "ANSWER_HINT", "돌담 아래의 균열은 사진 속 깨진 선과 닮아 있다.", false),
                spot(episodeId, "정동제일교회", "서울 중구 정동길 46", 37.566637, 126.972559, "ANSWER_HINT", "ANSWER_HINT", "ANSWER_HINT", "붉은 벽돌과 창의 곡선 사이에 렌즈의 윤곽이 숨어 있다.", false),
                spot(episodeId, "배재학당 역사박물관", "서울 중구 서소문로11길 19", 37.564815, 126.972420, "ANSWER_HINT", "ANSWER_HINT", "ANSWER_HINT", "기록의 표면에 남은 반짝임은 흉기의 재질을 암시한다.", false),
                spot(episodeId, "서울시립미술관 서소문본관", "서울 중구 덕수궁길 61", 37.564104, 126.973747, "ANSWER_HINT", "ANSWER_HINT", "ANSWER_HINT", "빛이 꺾이는 방향이 사건의 형태를 바꾼다.", false),
                spot(episodeId, "정동극장", "서울 중구 정동길 43", 37.565840, 126.972007, "DESTINATION_HINT", "DESTINATION_HINT", "DESTINATION_HINT", "무대 뒤 기록은 붉은 벽이 있는 장소를 가리킨다.", false),
                spot(episodeId, "이화학당 사적비", "서울 중구 정동길 26", 37.565055, 126.971380, "DESTINATION_HINT", "DESTINATION_HINT", "DESTINATION_HINT", "침묵으로 남은 마지막 문을 찾아라.", false),
                spot(episodeId, "서울시립미술관 앞마당", "서울 중구 덕수궁길 61", 37.564010, 126.973780, "FINAL_CANDIDATE", "STORY_CONTEXT", "FINAL_CANDIDATE", "마지막 필름이 숨겨졌다는 후보지다.", false),
                spot(episodeId, "중명전", "서울 중구 정동길 41-11", 37.566289, 126.971856, "FINAL", "FINAL_PLACE", "FINAL_CANDIDATE", "붉은 벽 앞에서 마지막 질문이 열린다.", true)
        );
        spots.forEach(values -> jdbcTemplate.update("""
                insert into mission_spots (episode_id, place_name, address, latitude, longitude, marker_type, clue_role,
                public_marker_type, story_text, arrival_radius, is_final_place) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 80, ?)
                """, values));

        Map<String, Long> spotIds = spotIds(episodeId);
        insertPuzzle(spotIds.get("덕수궁 대한문"), "OBSERVATION", "시작 장소의 문 이름을 입력하라.", "대한문", "TEXT", "마지막 사진", "EASY");
        insertPuzzle(spotIds.get("덕수궁 돌담길"), "OBSERVATION", "사건파일의 깨진 선과 가장 닮은 현장 요소를 한 단어로 입력하라.", "균열", "TEXT", "깨", "EASY");
        insertPuzzle(spotIds.get("정동제일교회"), "INITIAL_SOUND", "초성 ㄹㅈ가 가리키는 사진기 부품을 입력하라.", "렌즈", "TEXT", "ㄹㅈ", "NORMAL");
        insertPuzzle(spotIds.get("배재학당 역사박물관"), "NUMBER_LOCK", "관리자 입력 메모의 연도 1897과 1907 중 더 오래된 연도를 입력하라.", "1897", "NUMBER", "유리", "EASY");
        insertPuzzle(spotIds.get("서울시립미술관 서소문본관"), "PATTERN", "빛이 표면에서 되돌아오는 현상을 두 글자로 입력하라.", "반사", "TEXT", "반사", "NORMAL");
        insertPuzzle(spotIds.get("정동극장"), "STORY_COMBINATION", "목적지 힌트: 외교 기록이 잠든 벽의 색을 입력하라.", "붉은 벽", "TEXT", "외교 기록이 잠든 붉은 벽", "NORMAL");
        insertPuzzle(spotIds.get("이화학당 사적비"), "STORY_COMBINATION", "목적지 힌트: 마지막으로 열려야 하는 것을 한 글자로 입력하라.", "문", "TEXT", "침묵으로 남은 마지막 문", "EASY");
        insertPuzzle(spotIds.get("서울시립미술관 앞마당"), "OBSERVATION", "최종 후보 확인: 이 장소가 예술 기록과 관련 있음을 나타내는 단어를 입력하라.", "미술", "TEXT", "예술 기록 후보", "EASY");
        insertPuzzle(spotIds.get("중명전"), "STORY_COMBINATION", "최종 후보 확인: 목적지 힌트 두 개를 조합해 '외교 기록'을 입력하라.", "외교 기록", "TEXT", "최종 장소 확인", "NORMAL");
    }

    private void seedCaseDataIfMissing(Long episodeId) {
        if (count("case_suspects", episodeId) == 0) {
            insertSuspect(episodeId, "붉은 장갑의 남자", "용의자 A", "사건 당일 붉은 벽 근처에서 목격된 인물.", "피해자의 사진 의뢰인", "마지막 필름을 회수하려 했다는 증언이 있다.", "정동극장 인근에 있었다고 주장한다.", true, 1);
            insertSuspect(episodeId, "사라진 조수", "용의자 B", "피해자의 암실을 가장 잘 아는 조수. 사건 직후 행적이 끊겼다.", "피해자의 조수", "사진기 부품 기록을 마지막으로 열람했다.", "촬영 장비를 정리하고 있었다고 주장하지만 확인자가 없다.", false, 2);
            insertSuspect(episodeId, "검은 외투의 기록상", "용의자 C", "오래된 기록을 사고팔던 인물. 피해자와 문서 거래를 했다.", "비공식 기록 중개인", "외교 기록을 둘러싼 거래 흔적이 남아 있다.", "사건 당일 비가 와 외투를 입었다고 진술했다.", false, 3);
        }
        if (count("case_evidences", episodeId) == 0) {
            Map<String, Long> spotIds = spotIds(episodeId);
            Map<String, Long> suspectIds = suspectIds(episodeId);
            insertEvidence(episodeId, "현장 사진", "PHOTO", "피해자가 남긴 마지막 사진. 얼굴 대신 문과 그림자만 찍혀 있다.", spotIds.get("덕수궁 대한문"), null, "STORY_CLUE", true, 1);
            insertEvidence(episodeId, "찢어진 포스트잇", "POST_IT", "렌즈는 죽은 자가 아니라 사라진 진실을 비춘다.", spotIds.get("덕수궁 돌담길"), null, "ANSWER_CLUE", true, 2);
            insertEvidence(episodeId, "깨진 유리 조각 메모", "MEMO", "작은 유리 조각에서 강한 빛 반사 흔적이 발견되었다.", spotIds.get("배재학당 역사박물관"), suspectIds.get("사라진 조수"), "ANSWER_CLUE", false, 3);
            insertEvidence(episodeId, "사진기 부품 기록", "DOCUMENT", "렌즈 고정 링이 강제로 비틀린 흔적이 기록되어 있다.", spotIds.get("정동제일교회"), suspectIds.get("사라진 조수"), "ANSWER_CLUE", false, 4);
            insertEvidence(episodeId, "목격자 진술 메모", "NOTE", "붉은 장갑을 낀 사람이 마지막 사진 봉투를 들고 있었다는 진술.", spotIds.get("정동극장"), suspectIds.get("붉은 장갑의 남자"), "DESTINATION_CLUE", false, 5);
            insertEvidence(episodeId, "붉은 장갑 관련 기록", "SUSPECT_CLUE", "장갑의 붉은 색은 피가 아니라 낡은 인주 자국에 가까웠다.", spotIds.get("중명전"), suspectIds.get("붉은 장갑의 남자"), "STORY_CLUE", false, 6);
            insertEvidence(episodeId, "렌즈 반사 메모", "MEMO", "반사 방향은 흉기가 카메라 안쪽에서 나왔음을 암시한다.", spotIds.get("서울시립미술관 서소문본관"), null, "ANSWER_CLUE", false, 7);
            insertEvidence(episodeId, "마지막 사진 카드", "PHOTO", "사진 뒷면에는 '붉은 벽, 마지막 문'이라는 짧은 메모가 남아 있다.", spotIds.get("이화학당 사적비"), suspectIds.get("검은 외투의 기록상"), "DESTINATION_CLUE", false, 8);
        }
        if (count("episode_partner_rewards", episodeId) == 0) {
            jdbcTemplate.update("""
                    insert into episode_partner_rewards (episode_id, title, description, reward_type, partner_name, location_name, status)
                    values (?, '지역 리워드 준비 중', '향후 지자체/지역 상권 연계 리워드가 제공될 수 있습니다. MVP에서는 실제 쿠폰 지급 기능이 비활성화되어 있습니다.', 'COUPON', 'Operation Korea 제휴 준비', '정동 일대', 'PLANNED')
                    """, episodeId);
        }
    }

    private void seedRewardPayloads(Long episodeId) {
        Map<String, Long> evidence = evidenceIds(episodeId);
        Map<String, Long> suspect = suspectIds(episodeId);
        payload(episodeId, "덕수궁 대한문", "{\"rewards\":[{\"type\":\"STORY_CLUE\",\"value\":\"마지막 사진\"}]}");
        payload(episodeId, "덕수궁 돌담길", "{\"rewards\":[{\"type\":\"ANSWER_CLUE\",\"value\":\"깨\"}]}");
        payload(episodeId, "정동제일교회", "{\"rewards\":[{\"type\":\"ANSWER_CLUE\",\"value\":\"ㄹㅈ\"},{\"type\":\"EVIDENCE_UNLOCK\",\"targetId\":" + evidence.get("사진기 부품 기록") + "},{\"type\":\"SUSPECT_UNLOCK\",\"targetId\":" + suspect.get("사라진 조수") + "}]}");
        payload(episodeId, "배재학당 역사박물관", "{\"rewards\":[{\"type\":\"ANSWER_CLUE\",\"value\":\"유리\"},{\"type\":\"EVIDENCE_UNLOCK\",\"targetId\":" + evidence.get("깨진 유리 조각 메모") + "}]}");
        payload(episodeId, "서울시립미술관 서소문본관", "{\"rewards\":[{\"type\":\"ANSWER_CLUE\",\"value\":\"반사\"},{\"type\":\"MEMO_UNLOCK\",\"value\":\"렌즈 반사 방향은 카메라 안쪽의 파손 흔적과 연결된다.\",\"targetId\":" + evidence.get("렌즈 반사 메모") + "}]}");
        payload(episodeId, "정동극장", "{\"rewards\":[{\"type\":\"DESTINATION_CLUE\",\"value\":\"외교 기록이 잠든 붉은 벽\"},{\"type\":\"EVIDENCE_UNLOCK\",\"targetId\":" + evidence.get("목격자 진술 메모") + "}]}");
        payload(episodeId, "이화학당 사적비", "{\"rewards\":[{\"type\":\"DESTINATION_CLUE\",\"value\":\"침묵으로 남은 마지막 문\"},{\"type\":\"PHOTO_UNLOCK\",\"targetId\":" + evidence.get("마지막 사진 카드") + "},{\"type\":\"SUSPECT_UNLOCK\",\"targetId\":" + suspect.get("검은 외투의 기록상") + "}]}");
        payload(episodeId, "중명전", "{\"rewards\":[{\"type\":\"STORY_CLUE\",\"value\":\"붉은 장갑의 색은 피가 아니라 인주 자국에 가깝다.\"},{\"type\":\"EVIDENCE_UNLOCK\",\"targetId\":" + evidence.get("붉은 장갑 관련 기록") + "},{\"type\":\"SUSPECT_UPDATE\",\"targetId\":" + suspect.get("붉은 장갑의 남자") + "}]}");
    }

    private void payload(Long episodeId, String placeName, String payload) {
        jdbcTemplate.update("""
                update puzzles p join mission_spots s on s.id = p.mission_spot_id
                set p.reward_payload = ?
                where s.episode_id = ? and s.place_name = ?
                """, payload, episodeId, placeName);
    }

    private int count(String table, Long episodeId) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where episode_id = ?", Integer.class, episodeId);
    }

    private Long findEpisodeIdByTitle(String title) {
        List<Long> ids = jdbcTemplate.query("select id from episodes where title = ? limit 1", (rs, rowNum) -> rs.getLong("id"), title);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Object[] spot(Long episodeId, String name, String address, double lat, double lng, String markerType,
                          String clueRole, String publicMarkerType, String storyText, boolean finalPlace) {
        return new Object[]{episodeId, name, address, lat, lng, markerType, clueRole, publicMarkerType, storyText, finalPlace};
    }

    private void insertPuzzle(Long spotId, String type, String question, String answer, String format, String rewardClue, String difficulty) {
        jdbcTemplate.update("""
                insert into puzzles (mission_spot_id, puzzle_type, question_text, answer, answer_format, reward_clue, difficulty)
                values (?, ?, ?, ?, ?, ?, ?)
                """, spotId, type, question, answer, format, rewardClue, difficulty);
        Long puzzleId = jdbcTemplate.queryForObject("select id from puzzles where mission_spot_id = ?", Long.class, spotId);
        jdbcTemplate.update("insert into puzzle_hints (puzzle_id, hint_level, hint_text) values (?, 1, '현장 요소를 먼저 확인하라.'), (?, 2, '문제의 핵심 단어를 좁혀라.'), (?, 3, '정답 형식에 맞춰 짧게 입력하라.')",
                puzzleId, puzzleId, puzzleId);
    }

    private void insertSuspect(Long episodeId, String name, String alias, String description, String relation, String suspicious, String alibi, boolean unlocked, int order) {
        jdbcTemplate.update("""
                insert into case_suspects (episode_id, display_name, alias, short_description, relation_to_victim, suspicious_point, alibi_summary, unlocked_by_default, display_order)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, episodeId, name, alias, description, relation, suspicious, alibi, unlocked, order);
    }

    private void insertEvidence(Long episodeId, String title, String type, String summary, Long sourceSpotId, Long suspectId, String clueType, boolean unlocked, int order) {
        jdbcTemplate.update("""
                insert into case_evidences (episode_id, title, type, text_summary, source_spot_id, related_suspect_id, related_clue_type, unlocked_by_default, display_order)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, episodeId, title, type, summary, sourceSpotId, suspectId, clueType, unlocked, order);
    }

    private Map<String, Long> spotIds(Long episodeId) {
        return jdbcTemplate.queryForList("select id, place_name from mission_spots where episode_id = ?", episodeId).stream()
                .collect(Collectors.toMap(row -> String.valueOf(row.get("place_name")), row -> ((Number) row.get("id")).longValue()));
    }

    private Map<String, Long> suspectIds(Long episodeId) {
        return jdbcTemplate.queryForList("select id, display_name from case_suspects where episode_id = ?", episodeId).stream()
                .collect(Collectors.toMap(row -> String.valueOf(row.get("display_name")), row -> ((Number) row.get("id")).longValue()));
    }

    private Map<String, Long> evidenceIds(Long episodeId) {
        return jdbcTemplate.queryForList("select id, title from case_evidences where episode_id = ?", episodeId).stream()
                .collect(Collectors.toMap(row -> String.valueOf(row.get("title")), row -> ((Number) row.get("id")).longValue()));
    }

    private String fictionSynopsis() {
        return "정동의 한 사진사가 의문의 죽음을 맞았다. 그는 죽기 전 마지막 사진 한 장을 남겼지만, 사진 속에는 피해자도 범인도 없었다. 남은 것은 깨진 렌즈 조각, 붉은 벽을 가리키는 메모, 그리고 여러 장소에 흩어진 암호뿐이다.";
    }

    private String finalTruth() {
        return "사진사를 죽음으로 이끈 것은 외부에서 가져온 흉기가 아니라, 강제로 파손된 카메라의 깨진 렌즈였다.";
    }

    private String historySummary() {
        return "이 에피소드는 정동 일대의 근대 기록, 외교 공간, 사진과 문서의 상징성을 바탕으로 만든 가상 사건입니다. 실제 역사 인물을 범인으로 단정하지 않습니다.";
    }

    private String secretFacts() {
        return "최종 정답은 깨진 렌즈다. 정답 힌트는 깨, ㄹㅈ, 유리, 반사다. 목적지 힌트는 붉은 벽과 마지막 문이며 실제 최종 장소는 중명전이다.";
    }

    private String forbiddenReveals() {
        return "깨진 렌즈, 중명전, 최종 장소를 직접 노출하지 않는다. 실제 역사 인물을 범인으로 지목하지 않는다.";
    }

    private String teamGuide() {
        return "2~4명이 함께 플레이할 경우 지도 담당, 사건파일 담당, 문제 풀이 담당, 기록 담당으로 역할을 나누면 편합니다.";
    }

    private String noticeText() {
        return "인터넷 연결이 필요합니다.\n예상 소요 시간은 약 3시간입니다.\n권장 인원은 2~4명입니다.\n현장 지형지물과 안내문은 변경되었을 수 있습니다.\n다른 플레이어에게 정답이 노출되지 않도록 주의해 주세요.\n시설 운영을 방해하지 말고 조용히 플레이해 주세요.\n당일 완료하지 못해도 이어하기가 가능합니다.";
    }

    private void addColumnIfMissing(String tableName, String columnName, String sql) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = database() and table_name = ? and column_name = ?
                """, Integer.class, tableName, columnName);
        if (count == null || count == 0) jdbcTemplate.execute(sql);
    }
}
