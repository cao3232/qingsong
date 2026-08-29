package com.qingsong.ai.tools.document;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 颜色解析工具：支持 #RRGGBB、RRGGBB 及常见颜色名（中英文）。
 */
public final class DocColor {

    private static final Map<String, String> NAMED = new HashMap<>();

    static {
        put("black", "黑", "000000");
        put("white", "白", "FFFFFF");
        put("red", "红", "FF0000");
        put("blue", "蓝", "0000FF");
        put("green", "绿", "008000");
        put("yellow", "黄", "FFFF00");
        put("orange", "橙", "FFA500");
        put("purple", "紫", "800080");
        put("gray", "灰", "808080");
        put("grey", "灰", "808080");
        put("cyan", "青", "00FFFF");
        put("magenta", "品红", "FF00FF");
        put("brown", "棕", "A52A2A");
        put("gold", "金", "FFD700");
        put("silver", "银", "C0C0C0");
        put("navy", "藏青", "000080");
        put("lime", "亮绿", "00FF00");
        put("teal", "青绿", "008080");
        put("maroon", "栗色", "800000");
        put("olive", "橄榄", "808000");
        put("lightgray", "浅灰", "D3D3D3");
        put("lightgrey", "浅灰", "D3D3D3");
        put("darkgray", "深灰", "555555");
        put("darkgrey", "深灰", "555555");
    }

    private DocColor() {
    }

    public static String toHex6(String color) {
        if (color == null || color.isBlank()) {
            throw new IllegalArgumentException("颜色不能为空");
        }
        String c = color.trim();
        if (c.matches("(?i)#[0-9a-f]{6}")) {
            return c.substring(1).toUpperCase(Locale.ROOT);
        }
        if (c.matches("(?i)[0-9a-f]{6}")) {
            return c.toUpperCase(Locale.ROOT);
        }
        String named = NAMED.get(c.toLowerCase(Locale.ROOT));
        if (named != null) {
            return named;
        }
        throw new IllegalArgumentException("不支持的颜色：" + color
                + "，请使用 #RRGGBB 或常见颜色名（如 RED、BLUE、YELLOW、橙色）");
    }

    public static byte[] toRgb(String color) {
        String hex = toHex6(color);
        byte[] rgb = new byte[3];
        for (int i = 0; i < 3; i++) {
            rgb[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return rgb;
    }

    private static void put(String en, String zh, String hex) {
        NAMED.put(en, hex);
        NAMED.put(zh, hex);
        NAMED.put(hex.toLowerCase(Locale.ROOT), hex);
    }
}