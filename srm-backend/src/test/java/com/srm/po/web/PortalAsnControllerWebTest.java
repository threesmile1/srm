package com.srm.po.web;

import com.srm.execution.service.AsnService;
import com.srm.web.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalAsnControllerWebTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AsnService asnService;

    @Test
    void getAsn_wrongSupplier_returns404() throws Exception {
        when(asnService.requireWithLinesForSupplier(1L, 5L)).thenThrow(new NotFoundException("ASN 不存在"));

        mockMvc.perform(get("/api/v1/portal/asn-notices/5").param("supplierId", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ASN 不存在"));
    }
}
