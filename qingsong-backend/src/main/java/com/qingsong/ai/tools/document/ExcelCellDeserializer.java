package com.qingsong.ai.tools.document;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * 兼容 LLM 输出的两种单元格写法：
 * <ul>
 *   <li>纯字符串 {@code "1234"} / 数字 {@code 1234} → 视为仅 value 的单元格（type/format 自动推断）</li>
 *   <li>对象 {@code {"value":"2026-08-28","type":"DATE"}}</li>
 * </ul>
 */
public class ExcelCellDeserializer extends StdDeserializer<ExcelCell> {

    public ExcelCellDeserializer() {
        super(ExcelCell.class);
    }

    @Override
    public ExcelCell deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_STRING || parser.currentToken() == JsonToken.VALUE_NUMBER_INT
                || parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
            return new ExcelCell(parser.getValueAsString(), null, null);
        }
        JsonNode node = parser.getCodec().readTree(parser);
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return new ExcelCell(node.asText(), null, null);
        }
        if (!node.isObject()) {
            return new ExcelCell(node.toString(), null, null);
        }
        String value = node.hasNonNull("value") ? node.get("value").asText() : null;
        String format = node.hasNonNull("format") ? node.get("format").asText() : null;
        ExcelCellType type = node.hasNonNull("type") ? parseType(node.get("type").asText()) : null;
        return new ExcelCell(value, type, format);
    }

    private ExcelCellType parseType(String raw) {
        try {
            return ExcelCellType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}