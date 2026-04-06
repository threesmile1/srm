package com.srm.foundation.web;

import com.srm.web.error.ForbiddenException;
import jakarta.servlet.http.HttpSession;

/**
 * 门户 API 供应商身份：优先 {@link AuthController#SESSION_SUPPLIER_ID}，避免 query/header 伪造越权；
 * 无会话供应商时仍允许 {@code X-Dev-Supplier-Id} / {@code supplierId} 供本地联调。
 */
public final class PortalSupplierSession {

    private PortalSupplierSession() {
    }

    public static long resolveSupplierId(HttpSession session,
                                         Long headerSupplierId,
                                         Long querySupplierId) {
        if (session != null) {
            Object sid = session.getAttribute(AuthController.SESSION_SUPPLIER_ID);
            if (sid instanceof Long id) {
                return id;
            }
        }
        if (headerSupplierId != null) {
            return headerSupplierId;
        }
        if (querySupplierId != null) {
            return querySupplierId;
        }
        throw new ForbiddenException("当前用户非供应商账号；本地联调可传 X-Dev-Supplier-Id 或 supplierId");
    }
}
