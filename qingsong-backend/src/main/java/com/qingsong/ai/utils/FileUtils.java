package com.qingsong.ai.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传工具类
 *
 * @author : caojiangjiang
 * @data : 2025/12/21 12:31
 */
@Slf4j
public class FileUtils {

    public static final String URL_PREFIX = "/upload";
    private static String UPLOAD_PATH;

    static {
        UPLOAD_PATH = System.getProperty("user.dir") + "/upload/";
    }

    public static final Map<String, String> PAGE_MAP = new HashMap<String, String>() {{
        put("topic", "topic-tracker/");
        put("motto", "motto/");
    }};

    /**
     * 上传文件到指定文件夹
     *
     * @param file   上传的文件
     * @param folder 目标文件夹
     * @return 文件存储路径，如果失败则返回null
     */
    public static String uploadFile(MultipartFile file, String folder) {
        // 文件原始名
        String originalFilename = file.getOriginalFilename();

        // 校验文件名
        if (originalFilename == null || originalFilename.isEmpty()) {
            log.warn("文件名为空");
            return null;
        }

        // 防止路径遍历攻击
        originalFilename = Paths.get(originalFilename).getFileName().toString();

        // 文件后缀
        int lastDotIndex = originalFilename.lastIndexOf(".");
        if (lastDotIndex == -1) {
            log.warn("文件无扩展名: {}", originalFilename);
            return null;
        }

        String suffix = originalFilename.substring(lastDotIndex);
        String newFileName = UUID.randomUUID().toString() + suffix;

        // 文件夹路径（确保有分隔符）
        String folderPath = UPLOAD_PATH + folder;
        File dirFile = new File(folderPath);
        if (!dirFile.exists()) {
            if (!dirFile.mkdirs()) {
                log.error("创建目录失败: {}", folderPath);
                return null;
            }
        }

        // 文件保存路径
        String filePath = folderPath + newFileName;
        try {
            file.transferTo(new File(filePath));
            return URL_PREFIX + "/" + folder + newFileName;
        } catch (IOException e) {
            log.error("文件上传失败: {}", filePath, e);
            return null;
        }
    }

    /**
     * 删除指定文件
     *
     * @param fileName 文件名
     * @param source   文件来源类型
     * @return 删除结果，true表示删除成功，false表示删除失败
     */
    public static boolean deleteFile(String fileName, String source) {
        log.info("开始删除文件，文件名: {}, 来源: {}", fileName, source);

        try {
            // 根据source查找文件
            String folder = PAGE_MAP.get(source);
            if (folder == null) {
                log.warn("无效的文件来源类型: {}", source);
                return false;
            }

            // 构造文件路径（确保有分隔符）
            String filePath = UPLOAD_PATH + folder + "/" + fileName;
            log.debug("构造的文件路径: {}", filePath);

            File file = new File(filePath);
            if (file.exists()) {
                boolean result = file.delete();
                if (result) {
                    log.info("文件删除成功，路径: {}", filePath);
                } else {
                    log.error("文件删除失败，路径: {}", filePath);
                }
                return result;
            } else {
                log.warn("文件不存在，路径: {}", filePath);
                return false;
            }
        } catch (Exception e) {
            log.error("删除文件时发生异常，文件名: {}, 来源: {}", fileName, source, e);
            return false;
        }
    }

    /**
     * 从文件路径中提取文件名
     *
     * @param filePath 文件路径
     * @return 文件名
     */
    public static String extractFileName(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        int lastSeparator = filePath.lastIndexOf("/");
        if (lastSeparator != -1) {
            return filePath.substring(lastSeparator + 1);
        }
        return filePath;
    }

    /**
     * 获取文件扩展名
     *
     * @param fileName 文件名
     * @return 扩展名
     */
    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot != -1) {
            return fileName.substring(lastDot + 1);
        }
        return "";
    }
}
