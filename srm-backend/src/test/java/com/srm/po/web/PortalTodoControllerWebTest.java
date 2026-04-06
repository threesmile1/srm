package com.srm.po.web;

import com.srm.foundation.web.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalTodoControllerWebTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void todoSummary_withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/portal/todo-summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void todoSummary_loggedInWithoutSupplier_returns403() throws Exception {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(AuthController.SESSION_USER_ID, 1L);
        mockMvc.perform(get("/api/v1/portal/todo-summary").session(s))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").isString());
    }
}
