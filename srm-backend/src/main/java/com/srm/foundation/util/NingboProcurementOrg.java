package com.srm.foundation.util;

import com.srm.foundation.domain.OrgUnit;

/**
 * 与 U9 文档/前端约定一致：宁波公司采购组织。
 */
public final class NingboProcurementOrg {

    private static final String U9_CODE = "1001711275375071";

    private NingboProcurementOrg() {}

    public static boolean isNingbo(OrgUnit org) {
        if (org == null) {
            return false;
        }
        String code = org.getCode() != null ? org.getCode().trim() : "";
        if ("NB".equalsIgnoreCase(code)) {
            return true;
        }
        String name = org.getName() != null ? org.getName().trim() : "";
        if ("宁波公司".equals(name)) {
            return true;
        }
        String u9 = org.getU9OrgCode();
        return u9 != null && U9_CODE.equals(u9.trim());
    }
}
