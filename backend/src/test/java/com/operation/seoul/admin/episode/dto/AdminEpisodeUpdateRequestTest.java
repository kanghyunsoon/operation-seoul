package com.operation.seoul.admin.episode.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminEpisodeUpdateRequestTest {

    @Test
    void deserializesFinalAnswerKeywordItemsForUpdateRequest() throws Exception {
        String json = """
                {
                  "title": "case",
                  "finalAnswerKeywordItems": [
                    { "type": "CULPRIT", "displayType": "Culprit", "value": "A", "aliases": ["Alias-A"] },
                    { "type": "WEAPON", "displayType": "Weapon", "value": "B", "aliases": [] },
                    { "type": "MOTIVE", "displayType": "Motive", "value": "C", "aliases": null },
                    { "type": "METHOD", "displayType": "Method", "value": "D", "aliases": ["Alias-D"] }
                  ]
                }
                """;

        AdminEpisodeUpdateRequest request = new ObjectMapper().readValue(json, AdminEpisodeUpdateRequest.class);

        assertNotNull(request.getFinalAnswerKeywordItems());
        assertEquals(4, request.getFinalAnswerKeywordItems().size());
        assertEquals("CULPRIT", request.getFinalAnswerKeywordItems().get(0).getType());
        assertEquals("Culprit", request.getFinalAnswerKeywordItems().get(0).getDisplayType());
        assertEquals("A", request.getFinalAnswerKeywordItems().get(0).getValue());
        assertEquals("B", request.getFinalAnswerKeywordItems().get(1).getValue());
        assertEquals("MOTIVE", request.getFinalAnswerKeywordItems().get(2).getType());
        assertNull(request.getFinalAnswerKeywordItems().get(2).getAliases());
        assertTrue(request.getFinalAnswerKeywordItems().get(3).getAliases().contains("Alias-D"));
    }
}
