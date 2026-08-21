package com.qingsong.ai.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatterBuilder;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2025/07/12 17:22
 */
public class DateUtils {

    public static String getCurrentStringDateTime() {
        return LocalDateTime.now().format(new DateTimeFormatterBuilder().appendPattern("yyyyMMddHHmmss").toFormatter());
    }

    public static String getCurrentFormatDateTime() {
        return LocalDateTime.now().format(new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd HH:mm:ss").toFormatter());
    }
}
