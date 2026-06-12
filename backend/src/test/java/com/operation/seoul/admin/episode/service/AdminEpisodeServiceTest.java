package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminEpisodeServiceTest {

    private final AdminEpisodeService service = new AdminEpisodeService(
            null,
            new ObjectMapper(),
            null,
            null,
            null
    );

    @Test
    @SuppressWarnings("unchecked")
    void shadowLabelsAreMutableAndSafeForAnyTargetIndex() throws Exception {
        Method method = AdminEpisodeService.class.getDeclaredMethod(
                "shadowLabels",
                String.class,
                String.class,
                int.class
        );
        method.setAccessible(true);

        List<String> labels = (List<String>) method.invoke(service, "answer", "basis", 9);

        assertEquals(4, labels.size());
        assertEquals("basis", labels.get(1));
    }
}
