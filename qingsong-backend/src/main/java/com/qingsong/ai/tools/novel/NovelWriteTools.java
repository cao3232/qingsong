package com.qingsong.ai.tools.novel;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;

/**
 * 小说写作工具类 - 专用于保存各类生成内容
 */
@Component
public class NovelWriteTools {

    /**
     * 小说工作目录，建议通过 application.yml 配置注入
     */
    private static final String BASE_DIR = "data/novel";

    @Tool(description = "写入小说基础设定（book_bible.md），用于更新世界观/背景/主线等")
    public String writeBookBible(
            @ToolParam(description = "基础设定内容（Markdown）") String content) {
        return writeSimpleFile("book_bible.md", content);
    }

    @Tool(description = "写入人物设定表（characters.md），用于更新角色关系与设定")
    public String writeCharacterProfiles(
            @ToolParam(description = "人物设定内容（Markdown）") String content) {
        return writeSimpleFile("characters.md", content);
    }

    @Tool(description = "写入时间线与剧情进度表（timeline.md）")
    public String writeTimeline(
            @ToolParam(description = "时间线内容（Markdown）") String content) {
        return writeSimpleFile("timeline.md", content);
    }

    @Tool(description = "写入章节摘要总表（chapter_summaries.md）")
    public String writeChapterSummaries(
            @ToolParam(description = "章节摘要总表内容（Markdown）") String content) {
        return writeSimpleFile("chapter_summaries.md", content);
    }

    @Tool(description = "保存章节草稿，便于AI生成后落盘")
    public String saveChapterDraft(
            @ToolParam(description = "章节号，例如 121") Integer chapterNo,
            @ToolParam(description = "章节正文内容") String content) {

        if (chapterNo == null || chapterNo <= 0) {
            return "章节号必须为正整数";
        }
        if (content == null || content.isBlank()) {
            return "章节内容不能为空";
        }

        String path = BASE_DIR + "/drafts/chapter_" + chapterNo + "_draft.md";
        return writeFileSafe(path, content);
    }

    @Tool(description = "保存章节摘要，用于构建长期记忆和RAG资料")
    public String saveChapterSummary(
            @ToolParam(description = "章节号，例如 121") Integer chapterNo,
            @ToolParam(description = "该章节摘要内容") String summary) {

        if (chapterNo == null || chapterNo <= 0) {
            return "章节号必须为正整数";
        }
        if (summary == null || summary.isBlank()) {
            return "摘要不能为空";
        }

        String path = BASE_DIR + "/summaries/chapter_" + chapterNo + "_summary.md";
        return writeFileSafe(path, summary);
    }

    @Tool(description = "保存人物状态变更，例如受伤、黑化、换阵营等")
    public String saveCharacterStatus(
            @ToolParam(description = "角色名称") String characterName,
            @ToolParam(description = "状态描述") String status) {

        if (characterName == null || characterName.isBlank()) {
            return "角色名称不能为空";
        }
        if (status == null || status.isBlank()) {
            return "状态描述不能为空";
        }

        String path = BASE_DIR + "/characters/status/" + characterName + "_status.md";
        return writeFileSafe(path, status);
    }

    @Tool(description = "保存伏笔信息，便于后续回收")
    public String savePlotForeshadowing(
            @ToolParam(description = "伏笔关键词或主题") String keyword,
            @ToolParam(description = "伏笔详细描述") String description) {

        if (keyword == null || keyword.isBlank()) {
            return "伏笔关键词不能为空";
        }
        if (description == null || description.isBlank()) {
            return "伏笔描述不能为空";
        }

        String path = BASE_DIR + "/foreshadowing/" + keyword + "_foreshadowing.md";
        return writeFileSafe(path, description);
    }

    private String writeSimpleFile(String relativePath, String content) {
        if (content == null || content.isBlank()) {
            return "内容不能为空";
        }
        String path = BASE_DIR + "/" + relativePath;
        return writeFileSafe(path, content);
    }

    private String writeFileSafe(String filePath, String content) {
        try {
            Path path = Paths.get(filePath);
            if (Objects.nonNull(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(
                    path,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            return "保存成功：" + path.toAbsolutePath();
        } catch (IOException e) {
            return "保存失败：" + e.getMessage();
        }
    }
}
