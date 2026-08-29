package com.qingsong.ai.tools.document;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "app.tools.document")
public class DocumentToolProperties {

    private Path baseDir = Path.of(System.getProperty("user.home"), "qingsong-backend", "documents");

    public Path getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(Path baseDir) {
        this.baseDir = baseDir;
    }
}
