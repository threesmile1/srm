package com.srm.integration.u9;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.config.SrmProperties;
import com.srm.web.error.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 帆软 Decision POST /webroot/decision/url/api/data（与 wuliao / lpgys / cangku 共用）。
 */
@Component
@RequiredArgsConstructor
public class U9DecisionClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String postDecision(
            SrmProperties.U9 u9,
            String reportPath,
            List<Map<String, Object>> parameters,
            int pageNumber,
            int pageSize) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("report_path", reportPath);
        body.put("datasource_name", StringUtils.hasText(u9.getDatasourceName()) ? u9.getDatasourceName() : "ds1");
        body.put("page_number", pageNumber);
        body.put("page_size", pageSize);
        body.put("timestamp", String.valueOf(System.currentTimeMillis()));
        body.put("parameters", parameters);

        final String jsonBody;
        try {
            jsonBody = MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("构建帆软请求体失败: " + e.getMessage());
        }

        try {
            RestClient client = RestClient.builder()
                    .requestFactory(u9HttpRequestFactory(u9))
                    .build();
            var spec = client.post()
                    .uri(u9.getDecisionApiUrl().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(jsonBody);
            if (StringUtils.hasText(u9.getHttpUser())) {
                spec = spec.headers(h -> h.setBasicAuth(u9.getHttpUser(), passwordOrEmpty(u9.getHttpPassword()),
                        StandardCharsets.UTF_8));
            }
            return spec.retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            String errPayload = e.getResponseBodyAsString();
            String hint = errPayload != null && !errPayload.isBlank()
                    ? truncateForMessage(errPayload, 800)
                    : "";
            throw new BadRequestException("帆软 Decision HTTP " + e.getStatusCode().value()
                    + (hint.isEmpty() ? ": " + e.getMessage() : "，响应: " + hint));
        } catch (RestClientException e) {
            throw new BadRequestException("帆软 Decision 接口请求失败: " + e.getMessage());
        }
    }

    private static ClientHttpRequestFactory u9HttpRequestFactory(SrmProperties.U9 u9) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Math.max(1_000, u9.getHttpConnectTimeoutMs()));
        f.setReadTimeout(Math.max(5_000, u9.getHttpReadTimeoutMs()));
        return f;
    }

    private static String passwordOrEmpty(String p) {
        return p != null ? p : "";
    }

    private static String truncateForMessage(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
