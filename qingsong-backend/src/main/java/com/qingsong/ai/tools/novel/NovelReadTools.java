package com.qingsong.ai.tools.novel;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 小说阅读工具类 - 专用于读取各类小说设定与内容
 */
@Component
public class NovelReadTools {

    /**
     * 小说工作目录，建议通过 application.yml 配置注入
     */
    private static final String BASE_DIR = "data/novel";

    @Tool(description = "读取小说基础设定，例如世界观、题材、背景、金手指、主线目标等")
    public String readBookBible() {
        return readFileSafe(BASE_DIR + "/book_bible.md");
    }

    @Tool(description = "读取人物设定表，包含主角、配角、反派、人际关系、说话风格等")
    public String readCharacterProfiles() {
        return readFileSafe(BASE_DIR + "/characters.md");
    }

    @Tool(description = "读取时间线与剧情进度表，避免前后矛盾")
    public String readTimeline() {
        return readFileSafe(BASE_DIR + "/timeline.md");
    }

    @Tool(description = "读取章节摘要总表，帮助续写时快速了解前文")
    public String readChapterSummaries() {
        return readFileSafe(BASE_DIR + "/chapter_summaries.md");
    }

    @Tool(description = "根据章节号读取具体章节内容，用于续写、审稿或检查上下文")
    public String readChapter(
            @ToolParam(description = "章节号，例如 1、23、105") Integer chapterNo) {
        String path = BASE_DIR + "/chapters/chapter_" + chapterNo + ".md";
        return readFileSafe(path);
    }

    @Tool(description = "读取最近若干章内容，用于续写下一章时保持衔接")
    public String readRecentChapters(
            @ToolParam(description = "当前章节号，例如 120") Integer currentChapter,
            @ToolParam(description = "向前读取几章，例如 3") Integer count) {

        if (currentChapter == null || count == null || currentChapter <= 0 || count <= 0) {
            return "参数错误：currentChapter 和 count 必须为正整数";
        }

        StringBuilder sb = new StringBuilder();
        int start = Math.max(1, currentChapter - count);

        for (int i = start; i < currentChapter; i++) {
            sb.append("## 第").append(i).append("章\n");
            sb.append(readFileSafe(BASE_DIR + "/chapters/chapter_" + i + ".md")).append("\n\n");
        }
        return sb.toString();
    }

    @Tool(description = "列出当前已有的章节文件，帮助AI确认写作进度")
    public String listChapters() {
        Path dir = Paths.get(BASE_DIR, "chapters");
        if (!Files.exists(dir)) {
            return "章节目录不存在：" + dir.toAbsolutePath();
        }

        try (Stream<Path> stream = Files.list(dir)) {
            List<String> files = stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());

            if (files.isEmpty()) {
                return "当前没有章节文件";
            }

            return String.join("\n", files);
        } catch (IOException e) {
            return "读取章节列表失败：" + e.getMessage();
        }
    }

    private String readFileSafe(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "文件不存在：" + path.toAbsolutePath();
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "读取文件失败：" + e.getMessage();
        }
    }
}
