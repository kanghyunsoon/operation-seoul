package com.operation.seoul.episode.repository;

import com.operation.seoul.episode.domain.*;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface EpisodeRepository {
    @Select("""
            select id, title, subtitle, region_id, era, genre, difficulty, estimated_time, estimated_distance,
                   fiction_synopsis, final_answer_type, final_answer, final_answer_aliases, final_question,
                   final_truth_summary, actual_history_summary, deduction_secret_facts,
                   deduction_forbidden_reveals, max_deduction_questions, status
            from episodes
            where status = 'PUBLISHED'
            order by id asc
            """)
    @Results(id = "EpisodeMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "title", column = "title"),
            @Result(property = "subtitle", column = "subtitle"), @Result(property = "regionId", column = "region_id"),
            @Result(property = "era", column = "era"), @Result(property = "genre", column = "genre"),
            @Result(property = "difficulty", column = "difficulty"), @Result(property = "estimatedTime", column = "estimated_time"),
            @Result(property = "estimatedDistance", column = "estimated_distance"), @Result(property = "fictionSynopsis", column = "fiction_synopsis"),
            @Result(property = "finalAnswerType", column = "final_answer_type"), @Result(property = "finalAnswer", column = "final_answer"),
            @Result(property = "finalAnswerAliases", column = "final_answer_aliases"), @Result(property = "finalQuestion", column = "final_question"),
            @Result(property = "finalTruthSummary", column = "final_truth_summary"), @Result(property = "actualHistorySummary", column = "actual_history_summary"),
            @Result(property = "deductionSecretFacts", column = "deduction_secret_facts"), @Result(property = "deductionForbiddenReveals", column = "deduction_forbidden_reveals"),
            @Result(property = "maxDeductionQuestions", column = "max_deduction_questions"), @Result(property = "status", column = "status")
    })
    List<Episode> findPublishedEpisodes();

    @Select("""
            select id, title, subtitle, region_id, era, genre, difficulty, estimated_time, estimated_distance,
                   fiction_synopsis, final_answer_type, final_answer, final_answer_aliases, final_question,
                   final_truth_summary, actual_history_summary, deduction_secret_facts,
                   deduction_forbidden_reveals, max_deduction_questions, status
            from episodes
            where id = #{id}
            limit 1
            """)
    @ResultMap("EpisodeMap")
    Episode findEpisodeById(Long id);

    @Select("""
            select id, episode_id, place_name, address, latitude, longitude, marker_type, clue_role,
                   public_marker_type, story_text, arrival_radius, is_final_place
            from mission_spots
            where episode_id = #{episodeId}
            order by id asc
            """)
    @Results(id = "SpotMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "placeName", column = "place_name"), @Result(property = "address", column = "address"),
            @Result(property = "latitude", column = "latitude"), @Result(property = "longitude", column = "longitude"),
            @Result(property = "markerType", column = "marker_type"), @Result(property = "clueRole", column = "clue_role"),
            @Result(property = "publicMarkerType", column = "public_marker_type"), @Result(property = "storyText", column = "story_text"),
            @Result(property = "arrivalRadius", column = "arrival_radius"), @Result(property = "finalPlace", column = "is_final_place")
    })
    List<MissionSpot> findSpotsByEpisodeId(Long episodeId);

    @Select("""
            select id, episode_id, place_name, address, latitude, longitude, marker_type, clue_role,
                   public_marker_type, story_text, arrival_radius, is_final_place
            from mission_spots
            where id = #{id}
            limit 1
            """)
    @ResultMap("SpotMap")
    MissionSpot findSpotById(Long id);

    @Select("""
            select id, mission_spot_id, puzzle_type, question_text, answer, answer_format, reward_clue, reward_payload, difficulty
            from puzzles
            where mission_spot_id = #{spotId}
            limit 1
            """)
    @Results(id = "PuzzleMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "missionSpotId", column = "mission_spot_id"),
            @Result(property = "puzzleType", column = "puzzle_type"), @Result(property = "questionText", column = "question_text"),
            @Result(property = "answer", column = "answer"), @Result(property = "answerFormat", column = "answer_format"),
            @Result(property = "rewardClue", column = "reward_clue"), @Result(property = "rewardPayload", column = "reward_payload"),
            @Result(property = "difficulty", column = "difficulty")
    })
    Puzzle findPuzzleBySpotId(Long spotId);

    @Select("""
            select id, mission_spot_id, puzzle_type, question_text, answer, answer_format, reward_clue, reward_payload, difficulty
            from puzzles
            where id = #{id}
            limit 1
            """)
    @ResultMap("PuzzleMap")
    Puzzle findPuzzleById(Long id);

    @Select("select id, puzzle_id, hint_level, hint_text from puzzle_hints where puzzle_id = #{puzzleId} order by hint_level asc")
    @Results(id = "HintMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "puzzleId", column = "puzzle_id"),
            @Result(property = "hintLevel", column = "hint_level"), @Result(property = "hintText", column = "hint_text")
    })
    List<PuzzleHint> findHintsByPuzzleId(Long puzzleId);

    @Select("""
            select id, user_id, episode_id, visited_spot_ids, completed_spot_ids, collected_answer_clues,
                   collected_destination_clues, collected_story_clues, final_arrived_spot_id, hint_used_count,
                   wrong_answer_count, deduction_question_count, final_guess_count, score, started_at, last_played_at,
                   cleared_at, status, unlocked_suspect_ids, cleared_suspect_ids, unlocked_evidence_ids
            from user_episode_progress
            where user_id = #{userId} and episode_id = #{episodeId}
            limit 1
            """)
    @Results(id = "ProgressMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "userId", column = "user_id"),
            @Result(property = "episodeId", column = "episode_id"), @Result(property = "visitedSpotIds", column = "visited_spot_ids"),
            @Result(property = "completedSpotIds", column = "completed_spot_ids"), @Result(property = "collectedAnswerClues", column = "collected_answer_clues"),
            @Result(property = "collectedDestinationClues", column = "collected_destination_clues"), @Result(property = "collectedStoryClues", column = "collected_story_clues"),
            @Result(property = "finalArrivedSpotId", column = "final_arrived_spot_id"), @Result(property = "hintUsedCount", column = "hint_used_count"),
            @Result(property = "wrongAnswerCount", column = "wrong_answer_count"), @Result(property = "deductionQuestionCount", column = "deduction_question_count"),
            @Result(property = "finalGuessCount", column = "final_guess_count"), @Result(property = "score", column = "score"),
            @Result(property = "startedAt", column = "started_at"), @Result(property = "lastPlayedAt", column = "last_played_at"),
            @Result(property = "clearedAt", column = "cleared_at"), @Result(property = "status", column = "status"),
            @Result(property = "unlockedSuspectIds", column = "unlocked_suspect_ids"), @Result(property = "clearedSuspectIds", column = "cleared_suspect_ids"),
            @Result(property = "unlockedEvidenceIds", column = "unlocked_evidence_ids")
    })
    UserEpisodeProgress findProgress(@Param("userId") Long userId, @Param("episodeId") Long episodeId);

    @Insert("""
            insert into user_episode_progress (user_id, episode_id, visited_spot_ids, completed_spot_ids,
            collected_answer_clues, collected_destination_clues, collected_story_clues, hint_used_count,
            wrong_answer_count, deduction_question_count, final_guess_count, score, started_at, last_played_at,
            unlocked_suspect_ids, cleared_suspect_ids, unlocked_evidence_ids, status)
            values (#{userId}, #{episodeId}, #{visitedSpotIds}, #{completedSpotIds}, #{collectedAnswerClues},
            #{collectedDestinationClues}, #{collectedStoryClues}, 0, 0, 0, 0, 0, current_timestamp, current_timestamp,
            #{unlockedSuspectIds}, #{clearedSuspectIds}, #{unlockedEvidenceIds}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertProgress(UserEpisodeProgress progress);

    @Update("""
            update user_episode_progress
            set visited_spot_ids = #{visitedSpotIds}, completed_spot_ids = #{completedSpotIds},
                collected_answer_clues = #{collectedAnswerClues}, collected_destination_clues = #{collectedDestinationClues},
                collected_story_clues = #{collectedStoryClues}, final_arrived_spot_id = #{finalArrivedSpotId},
                hint_used_count = #{hintUsedCount}, wrong_answer_count = #{wrongAnswerCount},
                deduction_question_count = #{deductionQuestionCount}, final_guess_count = #{finalGuessCount},
                score = #{score}, last_played_at = current_timestamp, cleared_at = #{clearedAt}, status = #{status},
                unlocked_suspect_ids = #{unlockedSuspectIds}, cleared_suspect_ids = #{clearedSuspectIds},
                unlocked_evidence_ids = #{unlockedEvidenceIds}
            where id = #{id}
            """)
    int updateProgress(UserEpisodeProgress progress);

    @Select("select * from final_deduction_sessions where id = #{id} limit 1")
    @Results(id = "DeductionSessionMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "userId", column = "user_id"),
            @Result(property = "episodeId", column = "episode_id"), @Result(property = "startedAt", column = "started_at"),
            @Result(property = "completedAt", column = "completed_at"), @Result(property = "questionCount", column = "question_count"),
            @Result(property = "finalGuessCount", column = "final_guess_count"), @Result(property = "status", column = "status")
    })
    FinalDeductionSession findDeductionSession(Long id);

    @Select("select * from final_deduction_sessions where user_id = #{userId} and episode_id = #{episodeId} and status = 'OPEN' limit 1")
    @ResultMap("DeductionSessionMap")
    FinalDeductionSession findOpenDeductionSession(@Param("userId") Long userId, @Param("episodeId") Long episodeId);

    @Insert("""
            insert into final_deduction_sessions (user_id, episode_id, started_at, question_count, final_guess_count, status)
            values (#{userId}, #{episodeId}, current_timestamp, 0, 0, 'OPEN')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDeductionSession(FinalDeductionSession session);

    @Update("""
            update final_deduction_sessions
            set completed_at = #{completedAt}, question_count = #{questionCount}, final_guess_count = #{finalGuessCount}, status = #{status}
            where id = #{id}
            """)
    int updateDeductionSession(FinalDeductionSession session);

    @Insert("""
            insert into final_deduction_questions (session_id, user_question, ai_answer_type, ai_answer_text)
            values (#{sessionId}, #{userQuestion}, #{aiAnswerType}, #{aiAnswerText})
            """)
    int insertDeductionQuestion(FinalDeductionQuestion question);

    @Select("select id, session_id, user_question, ai_answer_type, ai_answer_text, created_at from final_deduction_questions where session_id = #{sessionId} order by id asc")
    @Results(id = "DeductionQuestionMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "sessionId", column = "session_id"),
            @Result(property = "userQuestion", column = "user_question"), @Result(property = "aiAnswerType", column = "ai_answer_type"),
            @Result(property = "aiAnswerText", column = "ai_answer_text"), @Result(property = "createdAt", column = "created_at")
    })
    List<FinalDeductionQuestion> findDeductionQuestions(Long sessionId);
}
