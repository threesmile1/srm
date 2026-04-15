package com.srm.execution.web;

import com.srm.execution.service.LogisticsQrLookupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PortalLogisticsLookup", description = "门户 - 物流单二维码查询解析")
@RestController
@RequestMapping("/api/v1/portal/logistics")
@RequiredArgsConstructor
public class PortalLogisticsLookupController {

    private final LogisticsQrLookupService logisticsQrLookupService;

    @PostMapping("/parse-by-url")
    public LogisticsInfoResponse parseByUrl(@Valid @RequestBody ParseByUrlRequest req) {
        LogisticsQrLookupService.LogisticsInfo info = logisticsQrLookupService.fetchAndParseFromUrl(req.url());
        return LogisticsInfoResponse.from(info);
    }

    public record ParseByUrlRequest(@NotBlank String url) {}

    public record LogisticsInfoResponse(
            String carrier,
            String trackingNo,
            String receiverName,
            String receiverPhone,
            String receiverAddress
    ) {
        static LogisticsInfoResponse from(LogisticsQrLookupService.LogisticsInfo i) {
            return new LogisticsInfoResponse(i.carrier(), i.trackingNo(), i.receiverName(), i.receiverPhone(), i.receiverAddress());
        }
    }
}

