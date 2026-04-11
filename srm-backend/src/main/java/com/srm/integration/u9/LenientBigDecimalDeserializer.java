package com.srm.integration.u9;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * 帆软等接口常把单价写成空字符串，默认 BigDecimal 反序列化会整批失败。
 */
public class LenientBigDecimalDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return switch (p.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_NUMBER_FLOAT, VALUE_NUMBER_INT -> p.getDecimalValue();
            case VALUE_STRING -> {
                String t = p.getText();
                if (t == null || t.isBlank()) {
                    yield null;
                }
                try {
                    yield new BigDecimal(t.trim());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }
}
