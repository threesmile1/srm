package com.srm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "srm")
public class SrmProperties {

    /** 允许超收比例，例如 0.05 = 5% */
    private BigDecimal overReceiveRatio = new BigDecimal("0.05");

    private String exportPoTypeCode = "PO01";

    private String exportGrTypeCode = "GR01";

    /**
     * 业务时区（JVM 默认时区、合同到期 cron、建议与 Jackson 一致）。生产固定为 Asia/Shanghai。
     */
    private String businessTimezone = "Asia/Shanghai";

    /** U9 集成（物料 wuliao.cpt 等） */
    private U9 u9 = new U9();

    @Getter
    @Setter
    public static class U9 {
        /**
         * 是否允许通过配置 URL 拉取 U9 物料 JSON（关闭时仅支持请求体手工推送同步）。
         */
        private boolean enabled = false;

        /**
         * 帆软 Decision 数据接口完整 URL（POST application/json）。
         * 示例：http://host:8050/webroot/decision/url/api/data
         * 若配置此项，优先走帆软 POST 请求体，不再使用下方 GET URL。
         */
        private String decisionApiUrl = "";

        /** 报表路径，对应 body 中 report_path */
        private String reportPath = "API/wuliao.cpt";

        /** 数据集名，对应 datasource_name */
        private String datasourceName = "ds1";

        private int pageNumber = 1;

        /** -1 表示不分页（与帆软示例一致）；与 {@link #syncPageSize} 二选一使用 */
        private int pageSize = -1;

        /**
         * 帆软 Decision 分页拉取时每页条数；大于 0 时按页循环直到 {@code total_page_number} 或末页不足一页。
         * ≤0 时单次请求，使用上方的 {@code page_number}/{@code page_size}（例如一次性 -1 全量，易超时）。
         * 默认 500，建议 200～1000。
         */
        private int syncPageSize = 500;

        /**
         * 报表参数列表，对应 body.parameters；空则默认一条 pinming（与常见 wuliao 模板一致）。
         */
        private List<FineReportParameter> fineReportParameters = new ArrayList<>();

        /**
         * 完整物料同步 URL（GET，兼容旧方式）。示例：https://u9-host/.../wuliao.cpt?format=json
         */
        private String materialSyncUrl = "";
        /** 与 material-api-path 拼接：base-url + path */
        private String baseUrl = "";
        /** 相对路径，默认报表/API 名 wuliao.cpt */
        private String materialApiPath = "wuliao.cpt";

        /**
         * 物料供应商报表（lpgys.cpt），与 wuliao 共用 decision-api-url，POST body 中
         * report_path 用本字段；parameters 为一条 name=code、value=料号。
         */
        private String supplierReportPath = "API/lpgys.cpt";

        /**
         * 已废弃：供应商请使用「同步供应商」接口单独拉 lpgys，不再随全量物料同步跟跑。保留字段仅为兼容旧配置。
         */
        private boolean syncSuppliersFromLpgys = false;

        /** 仓库 cangku.cpt，与 wuliao 共用 decision-api-url */
        private String warehouseReportPath = "API/cangku.cpt";

        /**
         * 采购订单 caigou_cp.cpt（与 wuliao 共用 decision-api-url；parameters 默认一条空对象，与现场帆软一致）。
         */
        private String purchaseOrderReportPath = "API/caigou_cp.cpt";

        /**
         * 采购订单报表 parameters；空则 POST {@code [{}]}（与物料可配 pinming 不同）。
         */
        private List<FineReportParameter> purchaseOrderFineReportParameters = new ArrayList<>();

        /**
         * 料号-多厂仓库（衣柜等）：liaohao + cangku_suzhou/cangku_chengdu/cangku_huanan。
         */
        private String materialYiguiReportPath = "API/cangku_yigui.cpt";

        /** 料号-水漆厂仓库：liaohao + cangku_shuiqi（cangku_shuiqi.cpt） */
        private String materialShuiqiReportPath = "API/cangku_shuiqi.cpt";

        /**
         * 四厂仓报表按「单料号」拉取时，帆软参数名（与模板中参数一致），默认 {@code code}。
         */
        private String factoryWarehouseReportParameterName = "code";

        /**
         * 四厂仓帆软 parameters 模板；非空时按条使用，且每条参数的 value 若为空则注入当前物料编码。
         * 空则使用单条：name 为 factoryWarehouseReportParameterName（默认 code）、value 为物料编码。
         */
        private List<FineReportParameter> materialFactoryWarehouseParameters = new ArrayList<>();

        /**
         * 物料四厂仓写入 {@code warehouse} 表时绑定的采购组织 {@code org_unit.code}（与苏州/成都/华南/水漆列对应）。
         * 不配则按采购组织名称「苏州工厂」「成都工厂」「华南工厂」「水漆工厂」查找。
         */
        private String factoryWarehouseOrgCodeSuzhou = "";
        private String factoryWarehouseOrgCodeChengdu = "";
        private String factoryWarehouseOrgCodeHuanan = "";
        private String factoryWarehouseOrgCodeShuiqi = "";
        private String factoryWarehouseOrgCodeNingbo = "";

        private String httpUser = "";
        private String httpPassword = "";

        /** 连接帆软/U9 超时（毫秒），默认 15s */
        private int httpConnectTimeoutMs = 15_000;

        /**
         * 读取帆软/U9 响应超时（毫秒）。大报表可能超过 3 分钟，默认 10 分钟；
         * 仍超可在 application-local.yml 设置 srm.u9.http-read-timeout-ms。
         */
        private int httpReadTimeoutMs = 600_000;
    }

    @Getter
    @Setter
    public static class FineReportParameter {
        private String name = "";
        private String type = "String";
        private String value = "";
    }
}
