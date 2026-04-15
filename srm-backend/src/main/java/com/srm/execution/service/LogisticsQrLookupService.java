package com.srm.execution.service;

import com.srm.web.error.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LogisticsQrLookupService {

    /**
     * 把二维码打开的网页抓取并解析为：承运商/运单号/收货人/手机/地址。
     * 设计目标：适配“页面上直接展示这些字段”的简易查询页（文本或 HTML）。
     */
    public LogisticsInfo fetchAndParseFromUrl(String url) {
        URI uri = safeUri(url);
        String raw = httpGet(uri);
        String text = toText(raw);
        LogisticsInfo info = parseFromText(text);
        // 承运商可能在 title/header 里；文本解析不到时做一层兜底：取第一行含“物流/快递/有限公司”
        if ((info.carrier() == null || info.carrier().isBlank()) && text != null) {
            String[] lines = text.split("\\R");
            for (String line : lines) {
                String t = line != null ? line.trim() : "";
                if (t.isEmpty()) continue;
                if (t.contains("物流") || t.contains("快递") || t.contains("运输") || t.contains("有限公司")) {
                    info = new LogisticsInfo(trimToNull(t), info.trackingNo(), info.receiverName(), info.receiverPhone(), info.receiverAddress());
                    break;
                }
            }
        }
        return info;
    }

    private static URI safeUri(String url) {
        if (url == null || url.isBlank()) {
            throw new BadRequestException("二维码网址为空");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new BadRequestException("二维码网址不合法");
        }
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BadRequestException("仅支持 http/https 链接");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BadRequestException("二维码网址缺少 host");
        }
        // SSRF 基本防护：禁止访问 localhost / 内网 / link-local
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
                throw new BadRequestException("禁止访问内网地址");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("解析二维码网址失败");
        }
        return uri;
    }

    private static String httpGet(URI uri) {
        try {
            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout((int) Duration.ofSeconds(8).toMillis());
            f.setReadTimeout((int) Duration.ofSeconds(12).toMillis());
            RestClient client = RestClient.builder()
                    .requestFactory(f)
                    .build();
            // 不强制 JSON，按文本取回
            return client.get()
                    .uri(Objects.requireNonNull(uri))
                    .accept(MediaType.TEXT_HTML, MediaType.TEXT_PLAIN, MediaType.ALL)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            String payload = e.getResponseBodyAsString();
            log.warn("物流二维码抓取失败 http={} url={} bodySnippet={}",
                    e.getStatusCode().value(), uri, truncate(payload, 300));
            throw new BadRequestException("查询链接返回 HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            log.warn("物流二维码抓取网络失败 url={}: {}", uri, e.getMessage());
            throw new BadRequestException("查询链接请求失败: " + e.getMessage());
        }
    }

    private static String toText(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.startsWith("<") || t.regionMatches(true, 0, "<!DOCTYPE", 0, 9)) {
            Document doc = Jsoup.parse(t);
            return doc.text();
        }
        return t;
    }

    private static LogisticsInfo parseFromText(String text) {
        String t = text != null ? text : "";
        // 统一冒号
        t = t.replace('：', ':');

        String carrier = pickAfterLabel(t, "(承运商|物流公司|承运单位)", 120);
        String trackingNo = normalizeDigits(pickAfterLabel(t, "(运单号|运单号码|快递单号|物流单号|单号)", 60));
        String name = pickAfterLabel(t, "(收货人|收件人|联系人|签收人)", 40);
        String phone = normalizePhone(pickAfterLabel(t, "(手机|手机号|收货人联系方式|联系方式|联系电话|电话)", 60));
        String address = pickAfterLabel(t, "(收货地址|收件地址|地址)", 200);

        return new LogisticsInfo(trimToNull(carrier), trimToNull(trackingNo), trimToNull(name), trimToNull(phone), trimToNull(address));
    }

    private static String pickAfterLabel(String text, String labelGroupRegex, int maxLen) {
        Pattern p = Pattern.compile(labelGroupRegex + "\\s*:?\\s*([^\\n\\r]{1," + maxLen + "})", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(2) != null ? m.group(2).trim() : null;
        }
        // 另一种：可能被空格拆开（HTML text() 会压缩空白），尝试在整段里找“label ... value”
        Pattern p2 = Pattern.compile(labelGroupRegex + "\\s*:?\\s*([^:]{1," + maxLen + "})", Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            return m2.group(2) != null ? m2.group(2).trim() : null;
        }
        return null;
    }

    private static String normalizeDigits(String s) {
        if (s == null) return null;
        Matcher m = Pattern.compile("(\\d[\\d\\s-]{4,40}\\d)").matcher(s);
        if (m.find()) {
            return m.group(1).replaceAll("[\\s-]+", "");
        }
        return s.trim();
    }

    private static String normalizePhone(String s) {
        if (s == null) return null;
        Matcher m = Pattern.compile("(\\+?\\d[\\d\\s-]{6,40}\\d)").matcher(s);
        if (m.find()) {
            return m.group(1).replaceAll("[\\s-]+", "");
        }
        return s.trim();
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    public record LogisticsInfo(
            String carrier,
            String trackingNo,
            String receiverName,
            String receiverPhone,
            String receiverAddress
    ) {}
}

