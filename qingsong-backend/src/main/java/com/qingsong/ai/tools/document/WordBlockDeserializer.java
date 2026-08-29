package com.qingsong.ai.tools.document;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Word 内容块宽容反序列化：模型漏发 blockType 时按字段推断，
 * HEADING/TABLE 强特征（level/data）可识别，其余默认按 PARAGRAPH 处理。
 */
public class WordBlockDeserializer extends StdDeserializer<WordBlock> {

    public WordBlockDeserializer() {
        super(WordBlock.class);
    }

    @Override
    public WordBlock deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || !node.isObject()) {
            return new WordParagraphBlock(node == null ? null : node.asText(), null);
        }
        String blockType = node.hasNonNull("blockType")
                ? node.get("blockType").asText().trim().toUpperCase(Locale.ROOT) : "";
        return switch (blockType) {
            case "HEADING" -> new WordHeadingBlock(text(node), level(node));
            case "PARAGRAPH" -> new WordParagraphBlock(text(node), style(node, parser));
            case "BULLET" -> new WordBulletBlock(text(node), style(node, parser));
            case "NUMBERED" -> new WordNumberedBlock(text(node), style(node, parser));
            case "QUOTE" -> new WordQuoteBlock(text(node));
            case "TABLE" -> new WordTableBlock(data(node, parser));
            case "PAGE_BREAK" -> new WordPageBreakBlock();
            case "DIVIDER" -> new WordDividerBlock();
            case "TOC" -> new WordTocBlock();
            default -> infer(node, parser);
        };
    }

    private WordBlock infer(JsonNode node, JsonParser parser) throws IOException {
        if (node.hasNonNull("data") && node.get("data").isObject()) {
            return new WordTableBlock(data(node, parser));
        }
        if (node.hasNonNull("level")) {
            return new WordHeadingBlock(text(node), level(node));
        }
        return new WordParagraphBlock(text(node), style(node, parser));
    }

    private static String text(JsonNode node) {
        return node.hasNonNull("text") ? node.get("text").asText() : "";
    }

    private static int level(JsonNode node) {
        return node.hasNonNull("level") ? node.get("level").asInt(1) : 1;
    }

    private static WordTextStyle style(JsonNode node, JsonParser parser) throws IOException {
        JsonNode style = node.get("style");
        if (style == null || !style.isObject()) {
            return null;
        }
        return parser.getCodec().treeToValue(style, WordTextStyle.class);
    }

    private static WordTableData data(JsonNode node, JsonParser parser) throws IOException {
        JsonNode data = node.get("data");
        if (data == null || !data.isObject()) {
            return new WordTableData(List.of(), List.of());
        }
        return parser.getCodec().treeToValue(data, WordTableData.class);
    }
}