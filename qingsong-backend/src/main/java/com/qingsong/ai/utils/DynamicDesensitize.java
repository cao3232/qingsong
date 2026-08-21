package com.qingsong.ai.utils;

import cn.hutool.core.util.StrUtil;

/**
 * 动态脱敏工具类
 * 提供通用的字符串脱敏功能，支持自定义保留头尾长度
 *
 * @author Generator
 * @date 2026-04-02
 */
public class DynamicDesensitize {

    /**
     * 通用动态脱敏：保留头尾，遮蔽中间
     *
     * @param str    原字符串
     * @param prefix 保留头部长度
     * @param suffix 保留尾部长度
     * @return 脱敏后的字符串
     */
    public static String maskMiddle(String str, int prefix, int suffix) {
        if (StrUtil.isBlank(str)) {
            return "";
        }

        int len = str.length();

        // 1. 健壮性处理：如果字符串总长度小于等于保留长度之和，则不脱敏或全脱敏
        if (len <= (prefix + suffix)) {
            // 策略：如果太短，至少保留第一位，其他全部打码，防止信息泄露
            return StrUtil.hide(str, 1, len);
        }

        // 2. 核心逻辑：从第 prefix 位开始，到倒数第 suffix 位结束进行遮蔽
        // StrUtil.hide(str, startInclude, endExclude)
        return StrUtil.hide(str, prefix, len - suffix);
    }

    /**
     * 默认脱敏：保留前 1 位和后 1 位
     *
     * @param str 原字符串
     * @return 脱敏后的字符串
     */
    public static String maskMiddle(String str) {
        return maskMiddle(str, 1, 1);
    }

    /**
     * 手机号脱敏：保留前 3 位和后 4 位（11位手机号标准）
     *
     * @param phone 手机号
     * @return 脱敏后的手机号
     */
    public static String maskPhone(String phone) {
        return maskMiddle(phone, 3, 4);
    }

    /**
     * 身份证脱敏：保留前 3 位和后 4 位
     *
     * @param idCard 身份证号
     * @return 脱敏后的身份证号
     */
    public static String maskIdCard(String idCard) {
        return maskMiddle(idCard, 3, 4);
    }

    /**
     * 邮箱脱敏：保留@符号及前后部分
     *
     * @param email 邮箱地址
     * @return 脱敏后的邮箱
     */
    public static String maskEmail(String email) {
        if (StrUtil.isBlank(email) || !email.contains("@")) {
            return email;
        }

        int atIndex = email.indexOf('@');
        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);

        // 对@前面部分进行脱敏
        String maskedLocalPart = maskMiddle(localPart, 1, 0);

        return maskedLocalPart + domainPart;
    }
}
