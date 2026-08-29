package com.qingsong.ai.tools.document;

import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Component
public class DocumentPathResolver {

    private static final int MAX_BASE_NAME_LENGTH = 120;
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private final Path baseDir;

    public DocumentPathResolver(DocumentToolProperties properties) {
        if (properties.getBaseDir() == null) {
            throw new IllegalArgumentException("文档目录不能为空");
        }
        this.baseDir = properties.getBaseDir().toAbsolutePath().normalize();
    }

    public Path writeVersioned(String requestedName, String extension, OutputWriter writer) throws Exception {
        String baseName = validateAndExtractBaseName(requestedName, extension);
        Files.createDirectories(baseDir);
        Path tempFile = Files.createTempFile(baseDir, ".document-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(tempFile)) {
                writer.write(output);
            }
            for (int version = 1; ; version++) {
                String suffix = version == 1 ? "" : "-" + version;
                Path target = baseDir.resolve(baseName + suffix + extension).normalize();
                ensureInsideBaseDir(target);
                try {
                    Files.move(tempFile, target);
                    return target.toAbsolutePath();
                } catch (FileAlreadyExistsException ignored) {
                    // Preserve existing files and try the next version.
                }
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * 仅校验文件名并返回安全的 MinIO objectName（不写本地文件）。
     * 返回形如 {@code 文件名.docx}。
     */
    public String resolveObjectName(String requestedName, String extension) {
        return validateAndExtractBaseName(requestedName, extension) + extension;
    }

    private String validateAndExtractBaseName(String requestedName, String extension) {
        if (requestedName == null || requestedName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (!extension.equals(".docx") && !extension.equals(".xlsx") && !extension.equals(".pdf")) {
            throw new IllegalArgumentException("不支持的文档扩展名");
        }
        String name = requestedName.trim();
        if (name.matches("(?i)^[a-z]:.*") || name.contains("..")
                || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("文件名不能包含路径");
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && !lowerName.endsWith(extension)) {
            throw new IllegalArgumentException("文件扩展名必须为 " + extension);
        }
        String baseName = lowerName.endsWith(extension)
                ? name.substring(0, name.length() - extension.length())
                : name;
        baseName = baseName.replaceAll("[<>:\"|?*\\p{Cntrl}]", "_").trim();
        baseName = baseName.replaceAll("[. ]+$", "");
        if (baseName.isBlank()) {
            throw new IllegalArgumentException("文件名无效");
        }
        if (WINDOWS_RESERVED_NAMES.contains(baseName.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("文件名不能使用系统保留名称");
        }
        if (baseName.length() > MAX_BASE_NAME_LENGTH) {
            throw new IllegalArgumentException("文件名不能超过 120 个字符");
        }
        return baseName;
    }

    private void ensureInsideBaseDir(Path target) {
        if (!target.startsWith(baseDir)) {
            throw new IllegalArgumentException("目标文件必须位于文档目录内");
        }
    }

    @FunctionalInterface
    public interface OutputWriter {
        void write(OutputStream output) throws Exception;
    }
}
