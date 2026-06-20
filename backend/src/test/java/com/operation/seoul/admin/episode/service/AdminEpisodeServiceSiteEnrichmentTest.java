package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.repository.AdminEpisodeRepository;
import com.operation.seoul.game.service.TourApiService;
import com.operation.seoul.location.service.OperationAreaResolver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminEpisodeServiceSiteEnrichmentTest {

    @Test
    void enrichSiteDataMergesExternalResearchIntoPlacesAndFinalSpot() {
        ExternalPlaceResearchService externalResearchService = mock(ExternalPlaceResearchService.class);
        when(externalResearchService.research(any(AiEpisodeDraftRequest.PlaceInput.class)))
                .thenReturn(new ExternalPlaceResearchService.ResearchResult(
                        List.of("Reference: archive - cultural background"),
                        List.of("https://example.test/archive"),
                        "external reference summary"
                ));
        AdminEpisodeService service = new AdminEpisodeService(
                mock(AdminEpisodeRepository.class),
                new ObjectMapper(),
                mock(TourApiService.class),
                mock(OperationAreaResolver.class),
                mock(KakaoLocalCandidateService.class),
                externalResearchService
        );
        AiEpisodeDraftRequest request = new AiEpisodeDraftRequest();
        request.setPlaces(new ArrayList<>(List.of(place("Start"), place("Investigation"))));
        request.getPlaces().get(0).setExternalResearchNotes(List.of("existing note"));
        request.getPlaces().get(0).setReferenceUrls(List.of("https://example.test/existing"));
        request.setFinalSpot(place("Final"));

        AiEpisodeDraftRequest enriched = service.enrichSiteData(request);

        AiEpisodeDraftRequest.PlaceInput first = enriched.getPlaces().get(0);
        assertTrue(first.getExternalResearchNotes().contains("existing note"));
        assertTrue(first.getExternalResearchNotes().contains("Reference: archive - cultural background"));
        assertTrue(first.getReferenceUrls().contains("https://example.test/existing"));
        assertTrue(first.getReferenceUrls().contains("https://example.test/archive"));
        assertEquals("external reference summary", first.getResearchSourceSummary());
        assertTrue(enriched.getFinalSpot().getExternalResearchNotes().contains("Reference: archive - cultural background"));
        assertTrue(enriched.getFinalSpot().getReferenceUrls().contains("https://example.test/archive"));
    }

    @Test
    void enrichSiteDataPreservesDraftContractAndReusesMatchingFinalSpot() {
        ExternalPlaceResearchService externalResearchService = mock(ExternalPlaceResearchService.class);
        when(externalResearchService.research(any(AiEpisodeDraftRequest.PlaceInput.class)))
                .thenReturn(new ExternalPlaceResearchService.ResearchResult(
                        List.of("Reference: archive - cultural background"),
                        List.of("https://example.test/archive"),
                        "external reference summary"
                ));
        AdminEpisodeService service = new AdminEpisodeService(
                mock(AdminEpisodeRepository.class),
                new ObjectMapper(),
                mock(TourApiService.class),
                mock(OperationAreaResolver.class),
                mock(KakaoLocalCandidateService.class),
                externalResearchService
        );
        AiEpisodeDraftRequest request = new AiEpisodeDraftRequest();
        request.setSelectedGenreId("CRIME_MYSTERY");
        request.setSelectedGenreName("Crime Mystery");
        request.setFinalAnswerKeywords(List.of("culprit", "weapon", "motive", "method"));
        AiEpisodeDraftRequest.FinalAnswersInput finalAnswers = new AiEpisodeDraftRequest.FinalAnswersInput();
        finalAnswers.setCulprit("culprit");
        request.setFinalAnswers(finalAnswers);
        request.setPlaces(new ArrayList<>(List.of(place("Start"), place("Final"))));
        request.setFinalSpot(place("Final"));

        AiEpisodeDraftRequest enriched = service.enrichSiteData(request);

        assertEquals("CRIME_MYSTERY", enriched.getSelectedGenreId());
        assertEquals("Crime Mystery", enriched.getSelectedGenreName());
        assertEquals(List.of("culprit", "weapon", "motive", "method"), enriched.getFinalAnswerKeywords());
        assertEquals("culprit", enriched.getFinalAnswers().getCulprit());
        assertEquals(enriched.getPlaces().get(1), enriched.getFinalSpot());
        verify(externalResearchService, times(2)).research(any(AiEpisodeDraftRequest.PlaceInput.class));
    }

    @Test
    void enrichSiteDataAddsSelectedPlaceContextWhenExternalResearchIsEmpty() {
        ExternalPlaceResearchService externalResearchService = mock(ExternalPlaceResearchService.class);
        when(externalResearchService.research(any(AiEpisodeDraftRequest.PlaceInput.class)))
                .thenReturn(ExternalPlaceResearchService.ResearchResult.empty());
        AdminEpisodeService service = new AdminEpisodeService(
                mock(AdminEpisodeRepository.class),
                new ObjectMapper(),
                mock(TourApiService.class),
                mock(OperationAreaResolver.class),
                mock(KakaoLocalCandidateService.class),
                externalResearchService
        );
        AiEpisodeDraftRequest request = new AiEpisodeDraftRequest();
        request.setPlaces(new ArrayList<>(List.of(place("Ewha Museum"))));

        AiEpisodeDraftRequest enriched = service.enrichSiteData(request);

        AiEpisodeDraftRequest.PlaceInput place = enriched.getPlaces().get(0);
        assertEquals(1, place.getExternalResearchNotes().size());
        assertTrue(place.getExternalResearchNotes().get(0).contains("Selected place context"));
        assertTrue(place.getExternalResearchNotes().get(0).contains("Ewha Museum"));
        assertEquals("Selected place context used because no direct external reference page matched.", place.getResearchSourceSummary());
    }

    private AiEpisodeDraftRequest.PlaceInput place(String name) {
        AiEpisodeDraftRequest.PlaceInput place = new AiEpisodeDraftRequest.PlaceInput();
        place.setName(name);
        place.setAddress("Seoul Jung-gu");
        place.setDescription("Selected route place");
        place.setKeywords(List.of("route"));
        return place;
    }
}
