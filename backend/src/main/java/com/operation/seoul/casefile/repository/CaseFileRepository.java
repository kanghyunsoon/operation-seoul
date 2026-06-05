package com.operation.seoul.casefile.repository;

import com.operation.seoul.casefile.domain.CaseEvidence;
import com.operation.seoul.casefile.domain.CaseSuspect;
import com.operation.seoul.casefile.domain.EpisodePartnerReward;
import com.operation.seoul.episode.domain.Episode;
import com.operation.seoul.episode.domain.UserEpisodeProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CaseFileRepository {
    @Select("""
            select id, title, subtitle, region_id, era, genre, difficulty, estimated_time, estimated_distance,
                   fiction_synopsis, final_answer_type, final_answer, final_answer_aliases, final_question,
                   final_truth_summary, actual_history_summary, deduction_secret_facts, deduction_forbidden_reveals,
                   max_deduction_questions, status, recommended_players, team_role_guide, notice_text
            from episodes
            where id = #{episodeId}
            limit 1
            """)
    @Results(id = "CaseEpisodeMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "title", column = "title"),
            @Result(property = "subtitle", column = "subtitle"), @Result(property = "regionId", column = "region_id"),
            @Result(property = "era", column = "era"), @Result(property = "genre", column = "genre"),
            @Result(property = "difficulty", column = "difficulty"), @Result(property = "estimatedTime", column = "estimated_time"),
            @Result(property = "estimatedDistance", column = "estimated_distance"), @Result(property = "fictionSynopsis", column = "fiction_synopsis"),
            @Result(property = "finalAnswerType", column = "final_answer_type"), @Result(property = "finalAnswer", column = "final_answer"),
            @Result(property = "finalAnswerAliases", column = "final_answer_aliases"), @Result(property = "finalQuestion", column = "final_question"),
            @Result(property = "finalTruthSummary", column = "final_truth_summary"), @Result(property = "actualHistorySummary", column = "actual_history_summary"),
            @Result(property = "deductionSecretFacts", column = "deduction_secret_facts"), @Result(property = "deductionForbiddenReveals", column = "deduction_forbidden_reveals"),
            @Result(property = "maxDeductionQuestions", column = "max_deduction_questions"), @Result(property = "status", column = "status"),
            @Result(property = "recommendedPlayers", column = "recommended_players"), @Result(property = "teamRoleGuide", column = "team_role_guide"),
            @Result(property = "noticeText", column = "notice_text")
    })
    Episode findEpisode(Long episodeId);

    @Select("""
            select id, episode_id, display_name, alias, short_description, portrait_image_url, relation_to_victim,
                   suspicious_point, alibi_summary, unlocked_by_default, display_order, created_at
            from case_suspects
            where episode_id = #{episodeId}
            order by display_order asc, id asc
            """)
    @Results(id = "CaseSuspectMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "displayName", column = "display_name"), @Result(property = "alias", column = "alias"),
            @Result(property = "shortDescription", column = "short_description"), @Result(property = "portraitImageUrl", column = "portrait_image_url"),
            @Result(property = "relationToVictim", column = "relation_to_victim"), @Result(property = "suspiciousPoint", column = "suspicious_point"),
            @Result(property = "alibiSummary", column = "alibi_summary"), @Result(property = "unlockedByDefault", column = "unlocked_by_default"),
            @Result(property = "displayOrder", column = "display_order"), @Result(property = "createdAt", column = "created_at")
    })
    List<CaseSuspect> findSuspects(Long episodeId);

    @Select("""
            select id, episode_id, title, type, image_url, text_summary, source_spot_id, related_suspect_id,
                   related_clue_type, unlocked_by_default, display_order, created_at
            from case_evidences
            where episode_id = #{episodeId}
            order by display_order asc, id asc
            """)
    @Results(id = "CaseEvidenceMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "title", column = "title"), @Result(property = "type", column = "type"),
            @Result(property = "imageUrl", column = "image_url"), @Result(property = "textSummary", column = "text_summary"),
            @Result(property = "sourceSpotId", column = "source_spot_id"), @Result(property = "relatedSuspectId", column = "related_suspect_id"),
            @Result(property = "relatedClueType", column = "related_clue_type"), @Result(property = "unlockedByDefault", column = "unlocked_by_default"),
            @Result(property = "displayOrder", column = "display_order"), @Result(property = "createdAt", column = "created_at")
    })
    List<CaseEvidence> findEvidences(Long episodeId);

    @Select("""
            select id, episode_id, title, description, reward_type, partner_name, location_name, latitude, longitude, status, created_at
            from episode_partner_rewards
            where episode_id = #{episodeId}
            order by id asc
            """)
    @Results(id = "PartnerRewardMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "title", column = "title"), @Result(property = "description", column = "description"),
            @Result(property = "rewardType", column = "reward_type"), @Result(property = "partnerName", column = "partner_name"),
            @Result(property = "locationName", column = "location_name"), @Result(property = "latitude", column = "latitude"),
            @Result(property = "longitude", column = "longitude"), @Result(property = "status", column = "status"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<EpisodePartnerReward> findPartnerRewards(Long episodeId);

    @Select("""
            select id, user_id, episode_id, visited_spot_ids, completed_spot_ids, collected_answer_clues,
                   collected_destination_clues, collected_story_clues, final_arrived_spot_id, hint_used_count,
                   wrong_answer_count, deduction_question_count, final_guess_count, score, started_at, last_played_at,
                   cleared_at, status, unlocked_suspect_ids, cleared_suspect_ids, unlocked_evidence_ids
            from user_episode_progress
            where user_id = #{userId} and episode_id = #{episodeId}
            limit 1
            """)
    @Results(id = "CaseProgressMap", value = {
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
    UserEpisodeProgress findProgress(Long userId, Long episodeId);

    @Select("select count(*) from mission_spots where episode_id = #{episodeId}")
    int countSpots(Long episodeId);
}