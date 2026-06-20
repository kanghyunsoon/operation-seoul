package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalPlaceResearchServiceTest {

    @Test
    void parsesWikipediaPagesIntoResearchContext() throws Exception {
        ExternalPlaceResearchService service = new ExternalPlaceResearchService(new ObjectMapper(), new RestTemplate());

        ExternalPlaceResearchService.ResearchResult result = service.parseWikipediaResponse("""
                {
                  "query": {
                    "pages": {
                      "1": {
                        "title": "Historic Site",
                        "extract": "A public cultural site with documented historical background.",
                        "fullurl": "https://example.test/wiki/Historic_Site"
                      }
                    }
                  }
                }
                """);

        assertEquals(1, result.notes().size());
        assertTrue(result.notes().get(0).contains("Historic Site"));
        assertEquals(List.of("https://example.test/wiki/Historic_Site"), result.referenceUrls());
        assertEquals("Wikipedia reference pages parsed: 1", result.summary());
    }

    @Test
    void prefersPageTitleMatchingPlaceName() throws Exception {
        ExternalPlaceResearchService service = new ExternalPlaceResearchService(new ObjectMapper(), new RestTemplate());

        ExternalPlaceResearchService.ResearchResult result = service.parseWikipediaResponse("""
                {
                  "query": {
                    "pages": {
                      "1": {
                        "title": "서대문구",
                        "extract": "서울특별시의 자치구이다.",
                        "fullurl": "https://example.test/wiki/district"
                      },
                      "2": {
                        "title": "서대문형무소역사관",
                        "extract": "독립운동과 근현대사를 전시하는 역사관이다.",
                        "fullurl": "https://example.test/wiki/place"
                      }
                    }
                  }
                }
                """, "서대문형무소역사관");

        assertTrue(result.notes().get(0).contains("서대문형무소역사관"));
        assertEquals("https://example.test/wiki/place", result.referenceUrls().get(0));
    }

    @Test
    void dropsUnrelatedWikipediaPages() throws Exception {
        ExternalPlaceResearchService service = new ExternalPlaceResearchService(new ObjectMapper(), new RestTemplate());

        ExternalPlaceResearchService.ResearchResult result = service.parseWikipediaResponse("""
                {
                  "query": {
                    "pages": {
                      "1": {
                        "title": "서울특별시",
                        "extract": "대한민국의 수도이다.",
                        "fullurl": "https://example.test/wiki/seoul"
                      },
                      "2": {
                        "title": "서대문구",
                        "extract": "서울특별시의 자치구이다.",
                        "fullurl": "https://example.test/wiki/district"
                      }
                    }
                  }
                }
                """, "이화박물관");

        assertTrue(result.notes().isEmpty());
        assertTrue(result.referenceUrls().isEmpty());
    }
}
