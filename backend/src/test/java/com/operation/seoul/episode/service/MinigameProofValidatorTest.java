package com.operation.seoul.episode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinigameProofValidatorTest {
    private final MinigameProofValidator validator = new MinigameProofValidator(new ObjectMapper());

    @Test
    void acceptsValidProofForAllTenMinigameTypes() {
        List<ProofCase> cases = List.of(
                new ProofCase("NUMBER_LOCK", "{\"solutionDigits\":\"1897\"}", "", "1897"),
                new ProofCase("WORD_COMPOSE", "{}", "Cracked Lens", "cracked-lens"),
                new ProofCase("COLOR_CODE", "{\"solution\":[\"RED\",\"BLUE\",\"GOLD\"]}", "", "RED,BLUE,GOLD"),
                new ProofCase("MEMORY_CARD", "{\"pairs\":4}", "", "MATCHED"),
                new ProofCase("PATTERN_LOCK", "{\"nodes\":[1,5,9,7]}", "", "1,5,9,7"),
                new ProofCase("SWITCH_TOGGLE", "{\"targetStates\":[true,false,true]}", "", "1,0,1"),
                new ProofCase("RAPID_TAP", "{\"target\":9}", "", "9"),
                new ProofCase("DIRECTION_SEQUENCE", "{\"sequence\":[\"UP\",\"LEFT\",\"DOWN\"]}", "", "UP,LEFT,DOWN"),
                new ProofCase("SHADOW_FIND", "{\"targetIndex\":2}", "", "2"),
                new ProofCase("SLIDE_PUZZLE", "{\"tiles\":[\"A\",\"B\",\"C\",\"D\"]}", "", "ABCD")
        );

        for (ProofCase proofCase : cases) {
            assertTrue(
                    validator.validate(payload(proofCase), "MG|" + proofCase.type() + "|" + proofCase.proof()),
                    proofCase.type()
            );
        }
    }

    @Test
    void rejectsWrongTypeMalformedPayloadAndOversizedProof() {
        ProofCase numberLock = new ProofCase("NUMBER_LOCK", "{\"solutionDigits\":\"1897\"}", "", "1897");
        assertFalse(validator.validate(payload(numberLock), "MG|WORD_COMPOSE|1897"));
        assertFalse(validator.validate("{not-json", "MG|NUMBER_LOCK|1897"));
        assertFalse(validator.validate(payload(numberLock), "MG|NUMBER_LOCK|" + "1".repeat(501)));
    }

    private String payload(ProofCase proofCase) {
        return """
                {
                  "interaction": {
                    "type": "%s",
                    "localSolution": "%s",
                    "config": %s
                  }
                }
                """.formatted(proofCase.type(), proofCase.localSolution(), proofCase.config());
    }

    private record ProofCase(String type, String config, String localSolution, String proof) {
    }
}
