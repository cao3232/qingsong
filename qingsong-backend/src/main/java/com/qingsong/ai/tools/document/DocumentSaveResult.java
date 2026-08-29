package com.qingsong.ai.tools.document;

public record DocumentSaveResult(
        boolean success,
        String fileName,
        String filePath,
        Long size,
        String message) {

    public static DocumentSaveResult success(String fileName, String filePath, long size, String message) {
        return new DocumentSaveResult(true, fileName, filePath, size, message);
    }

    public static DocumentSaveResult failure(String message) {
        return new DocumentSaveResult(false, null, null, null, message);
    }
}
