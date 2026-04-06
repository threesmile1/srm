package com.srm.perf.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalPerfControllerWebTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void listEvaluations_withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/portal/perf/evaluations"))
                .andExpect(status().isUnauthorized());
    }
}
