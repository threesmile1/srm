package com.srm.po.web;

import com.srm.master.domain.Supplier;
import com.srm.po.domain.PurchaseOrder;
import com.srm.po.service.PurchaseOrderService;
import com.srm.web.error.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
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

    @Test
    void getDetail_otherSupplier_returns404() throws Exception {
        PurchaseOrder po = org.mockito.Mockito.mock(PurchaseOrder.class);
        Supplier supplier = org.mockito.Mockito.mock(Supplier.class);
        when(supplier.getId()).thenReturn(2L);
        when(po.getSupplier()).thenReturn(supplier);
        when(purchaseOrderService.requireDetail(10L)).thenReturn(po);

        mockMvc.perform(get("/api/v1/portal/purchase-orders/10").param("supplierId", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("订单不存在"));
    }

    @Test
    void confirmLine_forbidden_returns403() throws Exception {
        when(purchaseOrderService.confirmLine(eq(1L), eq(99L), any(), any(), any()))
                .thenThrow(new ForbiddenException("无权确认该行"));

        mockMvc.perform(post("/api/v1/portal/purchase-order-lines/99/confirm")
                        .param("supplierId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmedQty\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("无权确认该行"));
    }
}
