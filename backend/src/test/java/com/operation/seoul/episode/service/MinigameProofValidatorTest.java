package com.operation.seoul.episode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinigameProofValidatorTest {
    private final MinigameProofValidator validator = new MinigameProofValidator(new ObjectMapper());

    @Test
    void acceptsValidProofForSupportedMinigameTypes() {
        List<ProofCase> cases = List.of(
                new ProofCase("NUMBER_LOCK", "{\"solutionDigits\":\"1897\"}", "", "1897"),
                new ProofCase("WORD_COMPOSE", "{}", "Cracked Lens", "cracked-lens"),
                new ProofCase("MEMORY_CARD", "{\"pairs\":4}", "", "MATCHED"),
                new ProofCase("PATTERN_LOCK", "{\"nodes\":[1,5,9,7]}", "", "1,5,9,7"),
                new ProofCase("RAPID_TAP", "{\"target\":9}", "", "9"),
                new ProofCase("DIRECTION_SEQUENCE", "{\"sequence\":[\"UP\",\"LEFT\",\"DOWN\"]}", "", "UP,LEFT,DOWN"),
                new ProofCase("UP_DOWN_TIMER", "{\"solution\":73}", "", "73"),
                new ProofCase("NUMBER_BASEBALL", "{\"solution\":\"427\"}", "", "427"),
                new ProofCase("NUMBER_SEQUENCE_TAP", "{\"sequence\":[1,2,3,4,5],\"skipNumber\":3,\"doubleNumber\":5}", "", "1,2,4,5,5")
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

    @Test
    void rejectsRapidTapCountsThatDoNotExactlyMatchTarget() {
        ProofCase rapidTap = new ProofCase("RAPID_TAP", "{\"target\":29}", "", "29");
        assertFalse(validator.validate(payload(rapidTap), "MG|RAPID_TAP|28"));
        assertFalse(validator.validate(payload(rapidTap), "MG|RAPID_TAP|30"));
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
