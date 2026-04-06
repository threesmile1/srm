package com.srm.po.web;

import com.srm.master.domain.Supplier;
import com.srm.po.domain.PurchaseOrder;
import com.srm.foundation.web.AuthController;
import com.srm.po.service.PurchaseOrderService;
import com.srm.web.error.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalPurchaseOrderControllerWebTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PurchaseOrderService purchaseOrderService;

    private static MockHttpSession loggedInSession() {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(AuthController.SESSION_USER_ID, 1L);
        s.setAttribute(AuthController.SESSION_SUPPLIER_ID, 1L);
        return s;
    }

    @Test
    void portal_list_withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/portal/purchase-orders").param("supplierId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("未登录或会话已过期，请重新登录"));
    }

    @Test
    void getDetail_otherSupplier_returns404() throws Exception {
        PurchaseOrder po = org.mockito.Mockito.mock(PurchaseOrder.class);
        Supplier supplier = org.mockito.Mockito.mock(Supplier.class);
        when(supplier.getId()).thenReturn(2L);
        when(po.getSupplier()).thenReturn(supplier);
        when(purchaseOrderService.requireDetail(10L)).thenReturn(po);

        mockMvc.perform(get("/api/v1/portal/purchase-orders/10").session(loggedInSession()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("订单不存在"));
    }

    @Test
    void confirmLine_forbidden_returns403() throws Exception {
        when(purchaseOrderService.confirmLine(eq(1L), eq(99L), any(), any(), any()))
                .thenThrow(new ForbiddenException("无权确认该行"));

        mockMvc.perform(post("/api/v1/portal/purchase-order-lines/99/confirm")
                        .session(loggedInSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmedQty\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("无权确认该行"));
    }

    @Test
    void list_loggedInWithoutSupplier_noDevParam_returns403() throws Exception {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(AuthController.SESSION_USER_ID, 99L);
        mockMvc.perform(get("/api/v1/portal/purchase-orders").session(s))
                .andExpect(status().isForbidden());
    }
}
