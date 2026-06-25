package com.operation.seoul.global.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class EpisodeSchemaMigration implements ApplicationRunner {
    private static final String SAMPLE_TITLE = "EP.01 The Lens That Lit the Silence";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${episode.migration.legacy-place-hints.enabled:false}")
    private boolean legacyPlaceHintMigrationEnabled;

    @Override
    public void run(ApplicationArguments args) {
        migrateUsers();
        createEpisodeTables();
        createFavoriteTables();
        createPlayerAnalysisTables();
        addColumns();
        createCaseFileTables();
        addCaseFileColumns();
        seedSampleEpisode();
        if (legacyPlaceHintMigrationEnabled) {
            migrateLegacyDestinationHints();
        } else {
            logLegacyDestinationHintSummary();
        }
    }

    private void logLegacyDestinationHintSummary() {
        long spotCount = countLegacyRows("""
                select count(*) from mission_spots
                where marker_type in ('DESTINATION_HINT', 'FINAL_CANDIDATE')
                   or clue_role in ('DESTINATION_HINT', 'STORY_CONTEXT')
                   or public_marker_type in ('DESTINATION_HINT', 'FINAL_CANDIDATE')
                """);
        long evidenceCount = countLegacyRows("""
                select count(*) from case_evidences
                where type = 'DESTINATION_CLUE' or related_clue_type = 'DESTINATION_CLUE'
                """);
        long progressCount = countLegacyRows("""
                select count(*) from user_episode_progress
                where collected_destination_clues is not null
                  and trim(collected_destination_clues) not in ('', '[]')
                """);
        if (spotCount + evidenceCount + progressCount > 0) {
            log.warn(
                    "legacy_place_hint_migration pending spots={} evidences={} progressRows={} enable with episode.migration.legacy-place-hints.enabled=true after backup",
                    spotCount,
                    evidenceCount,
                    progressCount
            );
        }
    }

    private long countLegacyRows(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private void migrateLegacyDestinationHints() {
        Set<Long> legacyEpisodeIds = new LinkedHashSet<>(jdbcTemplate.queryForList("""
                select distinct episode_id
                from mission_spots
                where marker_type in ('DESTINATION_HINT', 'FINAL_CANDIDATE')
                   or clue_role in ('DESTINATION_HINT', 'STORY_CONTEXT')
                   or public_marker_type in ('DESTINATION_HINT', 'FINAL_CANDIDATE')
                """, Long.class));
        legacyEpisodeIds.addAll(jdbcTemplate.queryForList("""
                select distinct episode_id
                from case_evidences
                where type = 'DESTINATION_CLUE' or related_clue_type = 'DESTINATION_CLUE'
                """, Long.class));

        backupLegacyDestinationHintRows(legacyEpisodeIds);

        jdbcTemplate.update("""
                update mission_spots
                set marker_type = case when is_final_place = true then 'FINAL' else 'ANSWER_HINT' end,
                    clue_role = case when is_final_place = true then 'FINAL_PLACE' else 'ANSWER_HINT' end,
                    public_marker_type = case when marker_type = 'START' then 'START' else 'ANSWER_HINT' end,
                    updated_at = current_timestamp
                where marker_type in ('DESTINATION_HINT', 'FINAL_CANDIDATE')
                   or clue_role in ('DESTINATION_HINT', 'STORY_CONTEXT')
                   or public_marker_type in ('DESTINATION_HINT', 'FINAL_CANDIDATE')
                """);
        jdbcTemplate.update("""
                update case_evidences
                set type = case when type = 'DESTINATION_CLUE' then 'EVIDENCE' else type end,
                    related_clue_type = case when related_clue_type = 'DESTINATION_CLUE' then 'ANSWER_CLUE' else related_clue_type end
                where type = 'DESTINATION_CLUE' or related_clue_type = 'DESTINATION_CLUE'
                """);
        jdbcTemplate.update("""
                update puzzles
                set reward_payload = replace(
                        replace(reward_payload, '"type":"DESTINATION_CLUE"', '"type":"ANSWER_CLUE"'),
                        '"slotId":"FINAL_DESTINATION"',
                        '"slotId":"ANSWER_CLUE"'
                    ),
                    updated_at = current_timestamp
                where reward_payload like '%DESTINATION_CLUE%'
                   or reward_payload like '%FINAL_DESTINATION%'
                """);

        migrateCollectedDestinationClues();
        if (!legacyEpisodeIds.isEmpty()) {
            String placeholders = legacyEpisodeIds.stream().map(id -> "?").collect(Collectors.joining(","));
            List<Object> args = new ArrayList<>(legacyEpisodeIds);
            jdbcTemplate.update(
                    "update episodes set status='DRAFT', updated_at=current_timestamp where id in (" + placeholders + ")",
                    args.toArray()
            );
        }
    }

    private void backupLegacyDestinationHintRows(Set<Long> legacyEpisodeIds) {
        jdbcTemplate.execute("create table if not exists backup_legacy_place_hint_episodes like episodes");
        jdbcTemplate.execute("create table if not exists backup_legacy_place_hint_spots like mission_spots");
        jdbcTemplate.execute("create table if not exists backup_legacy_place_hint_puzzles like puzzles");
        jdbcTemplate.execute("create table if not exists backup_legacy_place_hint_evidences like case_evidences");
        jdbcTemplate.execute("create table if not exists backup_legacy_place_hint_progress like user_episode_progress");

        if (!legacyEpisodeIds.isEmpty()) {
            String ids = legacyEpisodeIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            jdbcTemplate.update("insert ignore into backup_legacy_place_hint_episodes select * from episodes where id in (" + ids + ")");
            jdbcTemplate.update("insert ignore into backup_legacy_place_hint_spots select * from mission_spots where episode_id in (" + ids + ")");
            jdbcTemplate.update("""
                    insert ignore into backup_legacy_place_hint_puzzles
                    select p.* from puzzles p
                    join mission_spots s on s.id = p.mission_spot_id
                    where s.episode_id in (""" + ids + ")");
            jdbcTemplate.update("insert ignore into backup_legacy_place_hint_evidences select * from case_evidences where episode_id in (" + ids + ")");
            jdbcTemplate.update("insert ignore into backup_legacy_place_hint_progress select * from user_episode_progress where episode_id in (" + ids + ")");
        }
    }

    private void migrateCollectedDestinationClues() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select id, collected_answer_clues, collected_destination_clues
                from user_episode_progress
                where collected_destination_clues is not null
                  and trim(collected_destination_clues) not in ('', '[]')
                """);
        for (Map<String, Object> row : rows) {
            List<String> merged = new ArrayList<>(readStringList(row.get("collected_answer_clues")));
            for (String clue : readStringList(row.get("collected_destination_clues"))) {
                String normalized = clue == null ? "" : clue.trim();
                if (normalized.isBlank()) {
                    continue;
                }
                String migrated = normalized.contains("::") ? normalized : "ANSWER_CLUE::" + normalized;
                if (!merged.contains(migrated)) {
                    merged.add(migrated);
                }
            }
            jdbcTemplate.update(
                    "update user_episode_progress set collected_answer_clues=?, collected_destination_clues='[]' where id=?",
                    writeStringList(merged),
                    row.get("id")
            );
        }
    }

    private List<String> readStringList(Object value) {
        if (value == null || value.toString().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value.toString(), STRING_LIST);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeStringList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void migrateUsers() {
        addColumnIfMissing("users", "role", "alter table users add column role varchar(32) not null default 'ROLE_USER'");
        addColumnIfMissing("users", "profile_image_url", "alter table users add column profile_image_url varchar(1000) null");
        addColumnIfMissing("users", "status_message", "alter table users add column status_message varchar(120) null");
        addColumnIfMissing("users", "profile_public", "alter table users add column profile_public boolean not null default true");
        addColumnIfMissing("users", "status", "alter table users add column status varchar(32) not null default 'ACTIVE'");
        addColumnIfMissing("users", "created_at", "alter table users add column created_at datetime not null default current_timestamp");
        addColumnIfMissing("users", "updated_at", "alter table users add column updated_at datetime null");
        executeIgnoringFailure("alter table users modify column profile_image_url mediumtext null");
        jdbcTemplate.update("update users set role = 'ROLE_ADMIN' where is_admin = true and (role is null or role <> 'ROLE_ADMIN')");
        jdbcTemplate.update("update users set role = 'ROLE_USER' where is_admin = false and (role is null or role = '')");
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
                    field_verified boolean not null default false,
                    field_verification_note text null,
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
                    hypothesis_count int not null default 0,
                    final_guess_count int not null default 0,
                    active_elapsed_seconds int not null default 0,
                    clear_time_penalty_seconds int not null default 0,
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
                    hypothesis_count int not null default 0,
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
                create table if not exists puzzle_attempt_limits (
                    user_id bigint not null,
                    puzzle_id bigint not null,
                    wrong_count int not null default 0,
                    window_expires_at datetime not null,
                    updated_at datetime not null default current_timestamp on update current_timestamp,
                    primary key (user_id, puzzle_id),
                    index idx_puzzle_attempt_limits_expiry (window_expires_at),
                    constraint fk_puzzle_attempt_limits_user foreign key (user_id) references users (id) on delete cascade,
                    constraint fk_puzzle_attempt_limits_puzzle foreign key (puzzle_id) references puzzles (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                create table if not exists admin_episode_audit_logs (
                    id bigint not null auto_increment,
                    episode_id bigint null,
                    episode_title varchar(255) null,
                    actor_user_id bigint not null,
                    actor_email varchar(255) not null,
                    actor_nickname varchar(255) null,
                    action varchar(64) not null,
                    target_type varchar(64) not null,
                    target_id bigint null,
                    summary varchar(1000) not null,
                    request_id varchar(100) null,
                    created_at datetime not null default current_timestamp,
                    primary key (id),
                    index idx_admin_episode_audit_episode (episode_id, created_at),
                    index idx_admin_episode_audit_actor (actor_user_id, created_at),
                    index idx_admin_episode_audit_action (action, created_at)
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

    private void createPlayerAnalysisTables() {
        jdbcTemplate.execute("""
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
                    constraint fk_player_analysis_user foreign key (user_id) references users (id) on delete cascade,
                    constraint fk_player_analysis_mission foreign key (mission_id) references episodes (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
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
                    constraint fk_player_analysis_mbti_analysis foreign key (analysis_id) references player_analysis (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                create table if not exists reasoning_answer (
                    id bigint not null auto_increment,
                    user_id bigint not null,
                    mission_id bigint not null,
                    question text not null,
                    answer text not null,
                    created_at datetime not null default current_timestamp,
                    primary key (id),
                    index idx_reasoning_answer_user_mission (user_id, mission_id),
                    constraint fk_reasoning_answer_user foreign key (user_id) references users (id) on delete cascade,
                    constraint fk_reasoning_answer_mission foreign key (mission_id) references episodes (id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
    }

    private void addColumns() {
        addColumnIfMissing("episodes", "recommended_players", "alter table episodes add column recommended_players varchar(100) null");
        addColumnIfMissing("episodes", "team_role_guide", "alter table episodes add column team_role_guide text null");
        addColumnIfMissing("episodes", "notice_text", "alter table episodes add column notice_text text null");
        addColumnIfMissing("episodes", "mission_description", "alter table episodes add column mission_description text null");
        addColumnIfMissing("puzzles", "reward_payload", "alter table puzzles add column reward_payload text null");
        addColumnIfMissing("mission_spots", "field_verified", "alter table mission_spots add column field_verified boolean not null default false");
        addColumnIfMissing("mission_spots", "field_verification_note", "alter table mission_spots add column field_verification_note text null");
        addColumnIfMissing("user_episode_progress", "last_played_at", "alter table user_episode_progress add column last_played_at datetime null");
        addColumnIfMissing("user_episode_progress", "hypothesis_count", "alter table user_episode_progress add column hypothesis_count int not null default 0");
        addColumnIfMissing("user_episode_progress", "active_elapsed_seconds", "alter table user_episode_progress add column active_elapsed_seconds int not null default 0");
        addColumnIfMissing("user_episode_progress", "clear_time_penalty_seconds", "alter table user_episode_progress add column clear_time_penalty_seconds int not null default 0");
        addColumnIfMissing("user_episode_progress", "unlocked_suspect_ids", "alter table user_episode_progress add column unlocked_suspect_ids text null");
        addColumnIfMissing("user_episode_progress", "cleared_suspect_ids", "alter table user_episode_progress add column cleared_suspect_ids text null");
        addColumnIfMissing("user_episode_progress", "unlocked_evidence_ids", "alter table user_episode_progress add column unlocked_evidence_ids text null");
        addColumnIfMissing("final_deduction_sessions", "hypothesis_count", "alter table final_deduction_sessions add column hypothesis_count int not null default 0");
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
                    image_prompt text null,
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
                    image_prompt text null,
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

    private void addCaseFileColumns() {
        addColumnIfMissing("case_suspects", "image_prompt", "alter table case_suspects add column image_prompt text null");
        addColumnIfMissing("case_evidences", "image_prompt", "alter table case_evidences add column image_prompt text null");
    }

    private void seedSampleEpisode() {
        Long episodeId = findEpisodeIdByTitle(SAMPLE_TITLE);
        if (episodeId == null) {
            jdbcTemplate.update("""
                    insert into episodes (title, subtitle, era, genre, difficulty, estimated_time, estimated_distance,
                    fiction_synopsis, final_answer_type, final_answer, final_answer_aliases, final_question,
                    final_truth_summary, actual_history_summary, deduction_secret_facts, deduction_forbidden_reveals,
                    max_deduction_questions, recommended_players, team_role_guide, notice_text, status)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 20, ?, ?, ?, 'DRAFT')
                    """, SAMPLE_TITLE, "A field puzzle mystery around Jeong-dong", "Late Daehan Empire", "Outdoor mystery / observation / escape room", "NORMAL",
                    "About 3 hours", "About 2.4km", fictionSynopsis(), "OBJECT", "cracked lens", "lens,cracked lens,broken lens",
                    "What object made the final photograph look like a murder clue?", finalTruth(), historySummary(), secretFacts(), forbiddenReveals(),
                    "2-4 players", teamGuide(), noticeText());
            episodeId = findEpisodeIdByTitle(SAMPLE_TITLE);
        } else {
            jdbcTemplate.update("""
                    update episodes set subtitle=?, era=?, genre=?, difficulty=?, estimated_time=?, estimated_distance=?,
                    fiction_synopsis=?, final_answer_type=?, final_answer=?, final_answer_aliases=?, final_question=?,
                    final_truth_summary=?, actual_history_summary=?, deduction_secret_facts=?, deduction_forbidden_reveals=?,
                    max_deduction_questions=20, recommended_players=?, team_role_guide=?, notice_text=?, status='DRAFT',
                    updated_at=current_timestamp where id=?
                    """, "A field puzzle mystery around Jeong-dong", "Late Daehan Empire", "Outdoor mystery / observation / escape room", "NORMAL",
                    "About 3 hours", "About 2.4km", fictionSynopsis(), "OBJECT", "cracked lens", "lens,cracked lens,broken lens",
                    "What object made the final photograph look like a murder clue?", finalTruth(), historySummary(), secretFacts(), forbiddenReveals(),
                    "2-4 players", teamGuide(), noticeText(), episodeId);
        }
        seedSpotsAndPuzzlesIfMissing(episodeId);
        seedCaseDataIfMissing(episodeId);
        seedRewardPayloads(episodeId);
    }

    private void seedSpotsAndPuzzlesIfMissing(Long episodeId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from mission_spots where episode_id = ?", Integer.class, episodeId);
        if (count != null && count > 0) return;
        List<Object[]> spots = List.of(
                spot(episodeId, "Daehanmun Gate", "99 Sejong-daero, Jung-gu, Seoul", 37.565804, 126.975146, "START", "START", "START", "The investigation begins at the gate shown in the torn photograph.", false),
                spot(episodeId, "Jeongdong-gil Stone Wall", "Jeong-dong, Jung-gu, Seoul", 37.566258, 126.973766, "ANSWER_HINT", "ANSWER_HINT", "ANSWER_HINT", "A crack pattern on the wall matches the damaged lens mark.", false),
                spot(episodeId, "Jeongdong First Methodist Church", "46 Jeongdong-gil, Jung-gu, Seoul", 37.566637, 126.972559, "ANSWER_HINT", "ANSWER_HINT", "ANSWER_HINT", "The window grid hides an initial-sound clue.", false),
                spot(episodeId, "Pai Chai Hall Museum", "19 Seosomun-ro 11-gil, Jung-gu, Seoul", 37.564815, 126.972420, "ANSWER_HINT", "ANSWER_HINT", "ANSWER_HINT", "The archive years narrow down the code.", false),
                spot(episodeId, "SeMA Seosomun Main Building", "61 Deoksugung-gil, Jung-gu, Seoul", 37.564104, 126.973747, "ANSWER_HINT", "ANSWER_HINT", "ANSWER_HINT", "Light and shadow reveal the direction of the reflection.", false),
                spot(episodeId, "Jeongdong Theater", "43 Jeongdong-gil, Jung-gu, Seoul", 37.565840, 126.972007, "ANSWER_HINT", "ANSWER_HINT", "ANSWER_HINT", "A stage record adds a distinct fact to the case timeline.", false),
                spot(episodeId, "Ewha Hakdang Historic Marker", "26 Jeongdong-gil, Jung-gu, Seoul", 37.565055, 126.971380, "ANSWER_HINT", "ANSWER_HINT", "ANSWER_HINT", "A document fragment adds a distinct fact to the case timeline.", false),
                spot(episodeId, "SeMA Front Yard", "61 Deoksugung-gil, Jung-gu, Seoul", 37.564010, 126.973780, "ANSWER_HINT", "ANSWER_HINT", "ANSWER_HINT", "This location provides another independent case fact.", false),
                spot(episodeId, "Jungmyeongjeon Hall", "41-11 Jeongdong-gil, Jung-gu, Seoul", 37.566289, 126.971856, "FINAL", "FINAL_PLACE", "ANSWER_HINT", "The final deduction opens after all investigation locations are complete.", true)
        );
        spots.forEach(values -> jdbcTemplate.update("""
                insert into mission_spots (episode_id, place_name, address, latitude, longitude, marker_type, clue_role,
                public_marker_type, story_text, arrival_radius, is_final_place, field_verified, field_verification_note)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 80, ?, false, 'Seed data requires real field verification before publishing.')
                """, values));

        Map<String, Long> spotIds = spotIds(episodeId);
        insertPuzzle(spotIds.get("Daehanmun Gate"), "OBSERVATION", "Enter the name of the gate where the first photo was taken.", "Daehanmun", "TEXT", "first photo", "EASY");
        insertPuzzle(spotIds.get("Jeongdong-gil Stone Wall"), "OBSERVATION", "Which visible wall feature matches the broken photo mark?", "crack", "TEXT", "crack mark", "EASY");
        insertPuzzle(spotIds.get("Jeongdong First Methodist Church"), "INITIAL_SOUND", "Combine the initials from the window-grid clue to name the damaged object.", "lens", "TEXT", "lens", "NORMAL");
        insertPuzzle(spotIds.get("Pai Chai Hall Museum"), "NUMBER_LOCK", "From 1897 and 1907, enter the earlier archive year.", "1897", "NUMBER", "archive year", "EASY");
        insertPuzzle(spotIds.get("SeMA Seosomun Main Building"), "PATTERN", "The reflected light points to one word. Enter that word.", "reflection", "TEXT", "reflection", "NORMAL");
        insertPuzzle(spotIds.get("Jeongdong Theater"), "STORY_COMBINATION", "The stage record points to a color and material. Enter the destination phrase.", "red brick", "TEXT", "red brick record", "NORMAL");
        insertPuzzle(spotIds.get("Ewha Hakdang Historic Marker"), "STORY_COMBINATION", "The final route clue says the last thing to open is a ___.", "door", "TEXT", "last door", "EASY");
        insertPuzzle(spotIds.get("SeMA Front Yard"), "OBSERVATION", "This candidate location is tied to art records. Enter the matching word.", "art", "TEXT", "art record", "EASY");
        insertPuzzle(spotIds.get("Jungmyeongjeon Hall"), "STORY_COMBINATION", "Combine the destination clues: red brick + last door. What record phrase completes the route?", "red-brick record", "TEXT", "final place confirmation", "NORMAL");
    }

    private void seedCaseDataIfMissing(Long episodeId) {
        if (count("case_suspects", episodeId) == 0) {
            insertSuspect(episodeId, "Witness with a Red Umbrella", "Suspect A", "Seen near the red-brick path after the photo was taken.", "Claimed to protect the victim's last photo envelope.", "His statement changes whenever the broken lens is mentioned.", "Says he was waiting near Jeongdong Theater.", true, 1);
            insertSuspect(episodeId, "Vanished Photographer's Assistant", "Suspect B", "Handled the victim's camera case before disappearing.", "Managed the photo equipment.", "The camera mount shows signs of forced adjustment.", "Claims she was repairing a tripod, with no witness.", false, 2);
            insertSuspect(episodeId, "Black-Coat Archivist", "Suspect C", "Brokered old records around Jeong-dong.", "Connected to confidential archive trades.", "A trade memo mentions the imperial record route.", "Admits meeting the victim but denies seeing the final photo.", false, 3);
        }
        if (count("case_evidences", episodeId) == 0) {
            Map<String, Long> spotIds = spotIds(episodeId);
            Map<String, Long> suspectIds = suspectIds(episodeId);
            insertEvidence(episodeId, "Torn Field Photograph", "PHOTO", "The last photo shows a gate, a red-brick shadow, and a distorted corner.", spotIds.get("Daehanmun Gate"), null, "STORY_CLUE", true, 1);
            insertEvidence(episodeId, "Post-it with a Crack Mark", "POST_IT", "A note warns that the puzzle is not the dead person, but the disappearing object.", spotIds.get("Jeongdong-gil Stone Wall"), null, "ANSWER_CLUE", true, 2);
            insertEvidence(episodeId, "Broken Lens Memo", "MEMO", "A small memo describes a strong reflection from a cracked lens piece.", spotIds.get("Pai Chai Hall Museum"), suspectIds.get("Vanished Photographer's Assistant"), "ANSWER_CLUE", false, 3);
            insertEvidence(episodeId, "Camera Mount Repair Log", "DOCUMENT", "The repair log shows the mount was deliberately twisted before the incident.", spotIds.get("Jeongdong First Methodist Church"), suspectIds.get("Vanished Photographer's Assistant"), "ANSWER_CLUE", false, 4);
            insertEvidence(episodeId, "Witness Statement", "NOTE", "A witness saw the red-umbrella man carrying the final photo envelope.", spotIds.get("Jeongdong Theater"), suspectIds.get("Witness with a Red Umbrella"), "ANSWER_CLUE", false, 5);
            insertEvidence(episodeId, "Red Umbrella Record", "SUSPECT_CLUE", "The umbrella was a signal, not the murder weapon.", spotIds.get("Jungmyeongjeon Hall"), suspectIds.get("Witness with a Red Umbrella"), "STORY_CLUE", false, 6);
            insertEvidence(episodeId, "Reflection Direction Memo", "MEMO", "The reflection direction connects the camera trace to the left-side shadow.", spotIds.get("SeMA Seosomun Main Building"), null, "ANSWER_CLUE", false, 7);
            insertEvidence(episodeId, "Last Door Photo Card", "PHOTO", "The photo back records a route inconsistency that weakens one statement.", spotIds.get("Ewha Hakdang Historic Marker"), suspectIds.get("Black-Coat Archivist"), "ANSWER_CLUE", false, 8);
        }
        if (count("episode_partner_rewards", episodeId) == 0) {
            jdbcTemplate.update("""
                    insert into episode_partner_rewards (episode_id, title, description, reward_type, partner_name, location_name, status)
                    values (?, 'Local reward placeholder', 'Partner coupons are disabled until a real local partner contract exists.', 'COUPON', 'Operation Seoul', 'Jeong-dong area', 'PLANNED')
                    """, episodeId);
        }
    }

    private void seedRewardPayloads(Long episodeId) {
        Map<String, Long> evidence = evidenceIds(episodeId);
        Map<String, Long> suspect = suspectIds(episodeId);
        payload(episodeId, "Daehanmun Gate", "{\"rewards\":[{\"type\":\"STORY_CLUE\",\"value\":\"first photo\"}]}");
        payload(episodeId, "Jeongdong-gil Stone Wall", "{\"rewards\":[{\"type\":\"ANSWER_CLUE\",\"value\":\"crack\"}]}");
        payload(episodeId, "Jeongdong First Methodist Church", "{\"rewards\":[{\"type\":\"ANSWER_CLUE\",\"value\":\"lens\"},{\"type\":\"EVIDENCE_UNLOCK\",\"targetId\":" + evidence.get("Camera Mount Repair Log") + "},{\"type\":\"SUSPECT_UNLOCK\",\"targetId\":" + suspect.get("Vanished Photographer's Assistant") + "}]}");
        payload(episodeId, "Pai Chai Hall Museum", "{\"rewards\":[{\"type\":\"ANSWER_CLUE\",\"value\":\"archive year\"},{\"type\":\"EVIDENCE_UNLOCK\",\"targetId\":" + evidence.get("Broken Lens Memo") + "}]}");
        payload(episodeId, "SeMA Seosomun Main Building", "{\"rewards\":[{\"type\":\"ANSWER_CLUE\",\"value\":\"reflection\"},{\"type\":\"MEMO_UNLOCK\",\"value\":\"The reflection direction connects the camera trace to the left-side shadow.\",\"targetId\":" + evidence.get("Reflection Direction Memo") + "}]}");
        payload(episodeId, "Jeongdong Theater", "{\"rewards\":[{\"type\":\"ANSWER_CLUE\",\"slotId\":\"CULPRIT\",\"value\":\"the witness had access to the photo envelope\"},{\"type\":\"EVIDENCE_UNLOCK\",\"targetId\":" + evidence.get("Witness Statement") + "}]}");
        payload(episodeId, "Ewha Hakdang Historic Marker", "{\"rewards\":[{\"type\":\"ANSWER_CLUE\",\"slotId\":\"METHOD\",\"value\":\"the recorded route contradicts one statement\"},{\"type\":\"PHOTO_UNLOCK\",\"targetId\":" + evidence.get("Last Door Photo Card") + "},{\"type\":\"SUSPECT_UNLOCK\",\"targetId\":" + suspect.get("Black-Coat Archivist") + "}]}");
        payload(episodeId, "Jungmyeongjeon Hall", "{\"rewards\":[{\"type\":\"STORY_CLUE\",\"value\":\"The red-brick route ends at the final record hall.\"},{\"type\":\"EVIDENCE_UNLOCK\",\"targetId\":" + evidence.get("Red Umbrella Record") + "},{\"type\":\"SUSPECT_UPDATE\",\"targetId\":" + suspect.get("Witness with a Red Umbrella") + "}]}");
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
        jdbcTemplate.update("insert into puzzle_hints (puzzle_id, hint_level, hint_text) values (?, 1, 'Check the visible field element first.'), (?, 2, 'Focus on the noun requested by the question.'), (?, 3, 'Enter the answer in the requested format.')",
                puzzleId, puzzleId, puzzleId);
    }

    private void insertSuspect(Long episodeId, String name, String alias, String description, String relation, String suspicious, String alibi, boolean unlocked, int order) {
        jdbcTemplate.update("""
                insert into case_suspects (episode_id, display_name, alias, short_description, portrait_image_url, image_prompt, relation_to_victim, suspicious_point, alibi_summary, unlocked_by_default, display_order)
                values (?, ?, ?, ?, null, ?, ?, ?, ?, ?, ?)
                """, episodeId, name, alias, description, sampleSuspectPrompt(name, alias, description), relation, suspicious, alibi, unlocked, order);
    }

    private void insertEvidence(Long episodeId, String title, String type, String summary, Long sourceSpotId, Long suspectId, String clueType, boolean unlocked, int order) {
        jdbcTemplate.update("""
                insert into case_evidences (episode_id, title, type, image_url, image_prompt, text_summary, source_spot_id, related_suspect_id, related_clue_type, unlocked_by_default, display_order)
                values (?, ?, ?, null, ?, ?, ?, ?, ?, ?, ?)
                """, episodeId, title, type, sampleEvidencePrompt(title, type, summary), summary, sourceSpotId, suspectId, clueType, unlocked, order);
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

    private String sampleSuspectPrompt(String name, String alias, String description) {
        return "Cinematic detective suspect card portrait of " + name + " (" + alias + "), " + description
                + ". Mandatory casting: a fictional Korean person from Seoul, South Korea, with natural Korean styling appropriate to the character's age and era. "
                + "Do not cast a Western or European-looking model. Modern Seoul outdoor mystery mood, sharp facial expression, dramatic rim light, clean card composition, no text, no watermark, no extra fingers.";
    }

    private String sampleEvidencePrompt(String title, String type, String summary) {
        return "High-detail escape-room evidence card image, subject: " + title + ", evidence type: " + type + ", story detail: " + summary
                + ". If a person, hand, portrait, reflection, or silhouette appears, depict a fictional Korean person from Seoul and match the story era. "
                + "No Western or European-looking models. Realistic tabletop investigation photography, moody natural light, clear central object, no readable text, no watermark.";
    }

    private String fictionSynopsis() {
        return "A photographer dies after leaving a final field route through Jeong-dong. The team must separate a staged murder clue from the real object hidden in the photograph: a cracked lens.";
    }

    private String finalTruth() {
        return "The decisive clue is not a weapon or a person. The distorted final photograph was caused by a cracked lens deliberately placed on the camera mount.";
    }

    private String historySummary() {
        return "This is a fictional field mystery using public Jeong-dong landmarks, archive motifs, photographs, and documents as puzzle material.";
    }

    private String secretFacts() {
        return "Final answer: cracked lens. Answer clues are crack, lens, archive year, and reflection. Destination clues are red brick and last door, leading to Jungmyeongjeon Hall.";
    }

    private String forbiddenReveals() {
        return "Do not directly reveal cracked lens or Jungmyeongjeon Hall before the player has earned the relevant clues.";
    }

    private String teamGuide() {
        return "Recommended roles: navigator, field observer, puzzle solver, and note keeper. Teams of two can combine navigator and note keeper.";
    }

    private String noticeText() {
        return "Internet access is required. Estimated play time is about 3 hours. Recommended team size is 2-4 players. Field signs and access routes can change, so admins must verify all spots before publishing. Avoid blocking sidewalks or revealing answers to other players.";
    }

    private void addColumnIfMissing(String tableName, String columnName, String sql) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = database() and table_name = ? and column_name = ?
                """, Integer.class, tableName, columnName);
        if (count == null || count == 0) jdbcTemplate.execute(sql);
    }

    private void executeIgnoringFailure(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception exception) {
            log.debug("Optional schema migration skipped: {}", sql, exception);
        }
    }
}
