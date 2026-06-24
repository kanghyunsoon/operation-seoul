package com.operation.seoul.admin.episode.repository;

import com.operation.seoul.admin.episode.domain.AdminEpisodeProgressStats;
import com.operation.seoul.casefile.domain.CaseEvidence;
import com.operation.seoul.casefile.domain.CaseSuspect;
import com.operation.seoul.casefile.domain.EpisodePartnerReward;
import com.operation.seoul.episode.domain.Episode;
import com.operation.seoul.episode.domain.MissionSpot;
import com.operation.seoul.episode.domain.Puzzle;
import com.operation.seoul.episode.domain.PuzzleHint;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AdminEpisodeRepository {
    @Select("""
            select id, title, subtitle, region_id, era, genre, difficulty, estimated_time, estimated_distance,
                   fiction_synopsis, mission_description, final_answer_type, final_answer, final_answer_aliases, final_question,
                   final_truth_summary, actual_history_summary, deduction_secret_facts, deduction_forbidden_reveals,
                   max_deduction_questions, recommended_players, team_role_guide, notice_text, status
            from episodes
            order by id desc
            """)
    @Results(id = "AdminEpisodeMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "title", column = "title"),
            @Result(property = "subtitle", column = "subtitle"), @Result(property = "regionId", column = "region_id"),
            @Result(property = "era", column = "era"), @Result(property = "genre", column = "genre"),
            @Result(property = "difficulty", column = "difficulty"), @Result(property = "estimatedTime", column = "estimated_time"),
            @Result(property = "estimatedDistance", column = "estimated_distance"), @Result(property = "fictionSynopsis", column = "fiction_synopsis"),
            @Result(property = "missionDescription", column = "mission_description"),
            @Result(property = "finalAnswerType", column = "final_answer_type"), @Result(property = "finalAnswer", column = "final_answer"),
            @Result(property = "finalAnswerAliases", column = "final_answer_aliases"), @Result(property = "finalQuestion", column = "final_question"),
            @Result(property = "finalTruthSummary", column = "final_truth_summary"), @Result(property = "actualHistorySummary", column = "actual_history_summary"),
            @Result(property = "deductionSecretFacts", column = "deduction_secret_facts"), @Result(property = "deductionForbiddenReveals", column = "deduction_forbidden_reveals"),
            @Result(property = "maxDeductionQuestions", column = "max_deduction_questions"), @Result(property = "recommendedPlayers", column = "recommended_players"),
            @Result(property = "teamRoleGuide", column = "team_role_guide"), @Result(property = "noticeText", column = "notice_text"),
            @Result(property = "status", column = "status")
    })
    List<Episode> findAllEpisodes();

    @Select("""
            select id, title, subtitle, region_id, era, genre, difficulty, estimated_time, estimated_distance,
                   fiction_synopsis, mission_description, final_answer_type, final_answer, final_answer_aliases, final_question,
                   final_truth_summary, actual_history_summary, deduction_secret_facts, deduction_forbidden_reveals,
                   max_deduction_questions, recommended_players, team_role_guide, notice_text, status
            from episodes
            where (
                #{keyword} is null
                or lower(title) like concat('%', #{keyword}, '%')
                or lower(subtitle) like concat('%', #{keyword}, '%')
                or lower(genre) like concat('%', #{keyword}, '%')
                or lower(era) like concat('%', #{keyword}, '%')
                or lower(status) like concat('%', #{keyword}, '%')
            )
            order by id desc
            limit #{limit} offset #{offset}
            """)
    @ResultMap("AdminEpisodeMap")
    List<Episode> findEpisodePage(@Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            select count(*)
            from episodes
            where (
                #{keyword} is null
                or lower(title) like concat('%', #{keyword}, '%')
                or lower(subtitle) like concat('%', #{keyword}, '%')
                or lower(genre) like concat('%', #{keyword}, '%')
                or lower(era) like concat('%', #{keyword}, '%')
                or lower(status) like concat('%', #{keyword}, '%')
            )
            """)
    int countEpisodePage(@Param("keyword") String keyword);

    @Select("""
            select id, title, subtitle, region_id, era, genre, difficulty, estimated_time, estimated_distance,
                   fiction_synopsis, mission_description, final_answer_type, final_answer, final_answer_aliases, final_question,
                   final_truth_summary, actual_history_summary, deduction_secret_facts, deduction_forbidden_reveals,
                   max_deduction_questions, recommended_players, team_role_guide, notice_text, status
            from episodes
            where id = #{episodeId}
            limit 1
            """)
    @ResultMap("AdminEpisodeMap")
    Episode findEpisode(Long episodeId);

    @Select("select count(*) from mission_spots where episode_id = #{episodeId}")
    int countSpots(Long episodeId);

    @Select("""
            select count(*)
            from puzzles p
            join mission_spots s on s.id = p.mission_spot_id
            where s.episode_id = #{episodeId}
            """)
    int countPuzzles(Long episodeId);

    @Select("select count(*) from case_suspects where episode_id = #{episodeId}")
    int countSuspects(Long episodeId);

    @Select("select count(*) from case_evidences where episode_id = #{episodeId}")
    int countEvidences(Long episodeId);

    @Select("select count(*) from episode_partner_rewards where episode_id = #{episodeId}")
    int countPartnerRewards(Long episodeId);

    @Select("""
            select
              count(*) as total_players,
              sum(case when status in ('IN_PROGRESS', 'FINAL_READY') then 1 else 0 end) as in_progress_players,
              sum(case when status = 'CLEARED' and cleared_at is not null then 1 else 0 end) as cleared_players
            from user_episode_progress
            where episode_id = #{episodeId}
            """)
    @Results(id = "AdminProgressStatsMap", value = {
            @Result(property = "totalPlayers", column = "total_players"),
            @Result(property = "inProgressPlayers", column = "in_progress_players"),
            @Result(property = "clearedPlayers", column = "cleared_players")
    })
    AdminEpisodeProgressStats findProgressStats(Long episodeId);

    @Insert("""
            insert into episodes (title, subtitle, era, genre, difficulty, estimated_time, estimated_distance,
            fiction_synopsis, mission_description, final_answer_type, final_answer, final_answer_aliases, final_question,
            final_truth_summary, actual_history_summary, deduction_secret_facts, deduction_forbidden_reveals,
            max_deduction_questions, recommended_players, team_role_guide, notice_text, status)
            values (#{title}, #{subtitle}, #{era}, #{genre}, #{difficulty}, #{estimatedTime}, #{estimatedDistance},
            #{fictionSynopsis}, #{missionDescription}, #{finalAnswerType}, #{finalAnswer}, #{finalAnswerAliases}, #{finalQuestion},
            #{finalTruthSummary}, #{actualHistorySummary}, #{deductionSecretFacts}, #{deductionForbiddenReveals},
            #{maxDeductionQuestions}, #{recommendedPlayers}, #{teamRoleGuide}, #{noticeText}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertEpisode(Episode episode);

    @Delete("delete from episodes where id = #{episodeId}")
    int deleteEpisode(Long episodeId);

    @Update("""
            update episodes
            set title = #{title}, subtitle = #{subtitle}, era = #{era}, genre = #{genre}, difficulty = #{difficulty},
                estimated_time = #{estimatedTime}, estimated_distance = #{estimatedDistance}, fiction_synopsis = #{fictionSynopsis},
                mission_description = #{missionDescription},
                final_answer_type = #{finalAnswerType}, final_answer = #{finalAnswer}, final_answer_aliases = #{finalAnswerAliases},
                final_question = #{finalQuestion}, final_truth_summary = #{finalTruthSummary}, actual_history_summary = #{actualHistorySummary},
                deduction_secret_facts = #{deductionSecretFacts}, deduction_forbidden_reveals = #{deductionForbiddenReveals},
                max_deduction_questions = #{maxDeductionQuestions}, recommended_players = #{recommendedPlayers},
                team_role_guide = #{teamRoleGuide}, notice_text = #{noticeText}, status = #{status},
                updated_at = current_timestamp
            where id = #{id}
            """)
    int updateEpisode(Episode episode);

    @Select("""
            select id, episode_id, place_name, address, latitude, longitude, marker_type, clue_role,
                   public_marker_type, story_text, arrival_radius, is_final_place, field_verified, field_verification_note
            from mission_spots
            where episode_id = #{episodeId}
            order by id asc
            """)
    @Results(id = "AdminSpotMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "placeName", column = "place_name"), @Result(property = "address", column = "address"),
            @Result(property = "latitude", column = "latitude"), @Result(property = "longitude", column = "longitude"),
            @Result(property = "markerType", column = "marker_type"), @Result(property = "clueRole", column = "clue_role"),
            @Result(property = "publicMarkerType", column = "public_marker_type"), @Result(property = "storyText", column = "story_text"),
            @Result(property = "arrivalRadius", column = "arrival_radius"), @Result(property = "finalPlace", column = "is_final_place"),
            @Result(property = "fieldVerified", column = "field_verified"), @Result(property = "fieldVerificationNote", column = "field_verification_note")
    })
    List<MissionSpot> findSpots(Long episodeId);

    @Insert("""
            insert into mission_spots (episode_id, place_name, address, latitude, longitude, marker_type, clue_role,
            public_marker_type, story_text, arrival_radius, is_final_place, field_verified, field_verification_note)
            values (#{episodeId}, #{placeName}, #{address}, #{latitude}, #{longitude}, #{markerType}, #{clueRole},
            #{publicMarkerType}, #{storyText}, #{arrivalRadius}, #{finalPlace}, #{fieldVerified}, #{fieldVerificationNote})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSpot(MissionSpot spot);

    @Update("""
            update mission_spots
            set place_name = #{placeName}, address = #{address}, latitude = #{latitude}, longitude = #{longitude},
                marker_type = #{markerType}, clue_role = #{clueRole}, public_marker_type = #{publicMarkerType},
                story_text = #{storyText}, arrival_radius = #{arrivalRadius}, is_final_place = #{finalPlace},
                field_verified = #{fieldVerified}, field_verification_note = #{fieldVerificationNote},
                updated_at = current_timestamp
            where id = #{id}
            """)
    int updateSpot(MissionSpot spot);

    @Delete("delete from puzzle_hints where puzzle_id in (select id from puzzles where mission_spot_id = #{spotId})")
    int deleteHintsBySpotId(Long spotId);

    @Delete("delete from puzzles where mission_spot_id = #{spotId}")
    int deletePuzzlesBySpotId(Long spotId);

    @Update("update case_evidences set source_spot_id = null where source_spot_id = #{spotId}")
    int detachEvidencesBySpotId(Long spotId);

    @Delete("delete from mission_spots where id = #{spotId}")
    int deleteSpot(Long spotId);

    @Select("""
            select id, mission_spot_id, puzzle_type, question_text, answer, answer_format, reward_clue, reward_payload, difficulty
            from puzzles
            where mission_spot_id = #{spotId}
            limit 1
            """)
    @Results(id = "AdminPuzzleMap", value = {
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
            where id = #{puzzleId}
            limit 1
            """)
    @ResultMap("AdminPuzzleMap")
    Puzzle findPuzzle(Long puzzleId);

    @Insert("""
            insert into puzzles (mission_spot_id, puzzle_type, question_text, answer, answer_format, reward_clue, reward_payload, difficulty)
            values (#{missionSpotId}, #{puzzleType}, #{questionText}, #{answer}, #{answerFormat}, #{rewardClue}, #{rewardPayload}, #{difficulty})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPuzzle(Puzzle puzzle);

    @Update("""
            update puzzles
            set puzzle_type = #{puzzleType}, question_text = #{questionText}, answer = #{answer},
                answer_format = #{answerFormat}, reward_clue = #{rewardClue}, reward_payload = #{rewardPayload},
                difficulty = #{difficulty}, updated_at = current_timestamp
            where id = #{id}
            """)
    int updatePuzzle(Puzzle puzzle);

    @Select("select id, puzzle_id, hint_level, hint_text from puzzle_hints where puzzle_id = #{puzzleId} order by hint_level asc")
    @Results(id = "AdminHintMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "puzzleId", column = "puzzle_id"),
            @Result(property = "hintLevel", column = "hint_level"), @Result(property = "hintText", column = "hint_text")
    })
    List<PuzzleHint> findHints(Long puzzleId);

    @Delete("delete from puzzle_hints where puzzle_id = #{puzzleId}")
    int deleteHints(Long puzzleId);

    @Insert("""
            insert into puzzle_hints (puzzle_id, hint_level, hint_text)
            values (#{puzzleId}, #{hintLevel}, #{hintText})
            """)
    int insertHint(@Param("puzzleId") Long puzzleId, @Param("hintLevel") Integer hintLevel, @Param("hintText") String hintText);

    @Select("""
            select id, episode_id, display_name, alias, short_description, portrait_image_url, image_prompt, relation_to_victim,
                   suspicious_point, alibi_summary, unlocked_by_default, display_order, created_at
            from case_suspects
            where episode_id = #{episodeId}
            order by display_order asc, id asc
            """)
    @Results(id = "AdminCaseSuspectMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "displayName", column = "display_name"), @Result(property = "alias", column = "alias"),
            @Result(property = "shortDescription", column = "short_description"), @Result(property = "portraitImageUrl", column = "portrait_image_url"),
            @Result(property = "imagePrompt", column = "image_prompt"),
            @Result(property = "relationToVictim", column = "relation_to_victim"), @Result(property = "suspiciousPoint", column = "suspicious_point"),
            @Result(property = "alibiSummary", column = "alibi_summary"), @Result(property = "unlockedByDefault", column = "unlocked_by_default"),
            @Result(property = "displayOrder", column = "display_order"), @Result(property = "createdAt", column = "created_at")
    })
    List<CaseSuspect> findSuspects(Long episodeId);

    @Insert("""
            insert into case_suspects (episode_id, display_name, alias, short_description, portrait_image_url, image_prompt,
            relation_to_victim, suspicious_point, alibi_summary, unlocked_by_default, display_order)
            values (#{episodeId}, #{displayName}, #{alias}, #{shortDescription}, #{portraitImageUrl}, #{imagePrompt},
            #{relationToVictim}, #{suspiciousPoint}, #{alibiSummary}, #{unlockedByDefault}, #{displayOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSuspect(CaseSuspect suspect);

    @Update("""
            update case_suspects
            set display_name = #{displayName}, alias = #{alias}, short_description = #{shortDescription},
                portrait_image_url = #{portraitImageUrl}, image_prompt = #{imagePrompt}, relation_to_victim = #{relationToVictim},
                suspicious_point = #{suspiciousPoint}, alibi_summary = #{alibiSummary},
                unlocked_by_default = #{unlockedByDefault}, display_order = #{displayOrder}
            where id = #{id}
            """)
    int updateSuspect(CaseSuspect suspect);

    @Update("update case_evidences set related_suspect_id = null where related_suspect_id = #{suspectId}")
    int detachEvidencesBySuspectId(Long suspectId);

    @Delete("delete from case_suspects where id = #{suspectId}")
    int deleteSuspect(Long suspectId);

    @Select("""
            select id, episode_id, title, type, image_url, image_prompt, text_summary, source_spot_id, related_suspect_id,
                   related_clue_type, unlocked_by_default, display_order, created_at
            from case_evidences
            where episode_id = #{episodeId}
            order by display_order asc, id asc
            """)
    @Results(id = "AdminCaseEvidenceMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "title", column = "title"), @Result(property = "type", column = "type"),
            @Result(property = "imageUrl", column = "image_url"), @Result(property = "imagePrompt", column = "image_prompt"),
            @Result(property = "textSummary", column = "text_summary"),
            @Result(property = "sourceSpotId", column = "source_spot_id"), @Result(property = "relatedSuspectId", column = "related_suspect_id"),
            @Result(property = "relatedClueType", column = "related_clue_type"), @Result(property = "unlockedByDefault", column = "unlocked_by_default"),
            @Result(property = "displayOrder", column = "display_order"), @Result(property = "createdAt", column = "created_at")
    })
    List<CaseEvidence> findEvidences(Long episodeId);

    @Insert("""
            insert into case_evidences (episode_id, title, type, image_url, image_prompt, text_summary, source_spot_id,
            related_suspect_id, related_clue_type, unlocked_by_default, display_order)
            values (#{episodeId}, #{title}, #{type}, #{imageUrl}, #{imagePrompt}, #{textSummary}, #{sourceSpotId},
            #{relatedSuspectId}, #{relatedClueType}, #{unlockedByDefault}, #{displayOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertEvidence(CaseEvidence evidence);

    @Update("""
            update case_evidences
            set title = #{title}, type = #{type}, image_url = #{imageUrl}, image_prompt = #{imagePrompt}, text_summary = #{textSummary},
                source_spot_id = #{sourceSpotId}, related_suspect_id = #{relatedSuspectId},
                related_clue_type = #{relatedClueType}, unlocked_by_default = #{unlockedByDefault},
                display_order = #{displayOrder}
            where id = #{id}
            """)
    int updateEvidence(CaseEvidence evidence);

    @Delete("delete from case_evidences where id = #{evidenceId}")
    int deleteEvidence(Long evidenceId);

    @Select("""
            select id, episode_id, title, description, reward_type, partner_name, location_name, latitude, longitude, status, created_at
            from episode_partner_rewards
            where episode_id = #{episodeId}
            order by id asc
            """)
    @Results(id = "AdminPartnerRewardMap", value = {
            @Result(property = "id", column = "id", id = true), @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "title", column = "title"), @Result(property = "description", column = "description"),
            @Result(property = "rewardType", column = "reward_type"), @Result(property = "partnerName", column = "partner_name"),
            @Result(property = "locationName", column = "location_name"), @Result(property = "latitude", column = "latitude"),
            @Result(property = "longitude", column = "longitude"), @Result(property = "status", column = "status"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<EpisodePartnerReward> findPartnerRewards(Long episodeId);

    @Insert("""
            insert into episode_partner_rewards (episode_id, title, description, reward_type, partner_name, location_name, latitude, longitude, status)
            values (#{episodeId}, #{title}, #{description}, #{rewardType}, #{partnerName}, #{locationName}, #{latitude}, #{longitude}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPartnerReward(EpisodePartnerReward reward);

    @Update("""
            update episode_partner_rewards
            set title = #{title}, description = #{description}, reward_type = #{rewardType},
                partner_name = #{partnerName}, location_name = #{locationName}, latitude = #{latitude},
                longitude = #{longitude}, status = #{status}
            where id = #{id}
            """)
    int updatePartnerReward(EpisodePartnerReward reward);
}
