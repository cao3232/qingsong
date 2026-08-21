package com.qingsong.ai.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 飞书交互式卡片消息 DTO
 * 包含消息类型和卡片内容
 *
 * @author lingma
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeishuCardDTO {

    /**
     * 消息类型，interactive 表示交互式卡片消息
     */
    @JsonProperty("msg_type")
    private String msgType = "interactive";

    /**
     * 卡片内容
     */
    @JsonProperty("card")
    private Card card;

    /**
     * 时间戳，用于签名验证
     */
    @JsonProperty("timestamp")
    private String timestamp;

    /**
     * 签名信息
     */
    @JsonProperty("sign")
    private String sign;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Card {
        /**
         * 卡片 JSON 结构的版本
         */
        @JsonProperty("schema")
        private String schema = "2.0";

        /**
         * 卡片配置
         */
        @JsonProperty("config")
        private Config config;

        /**
         * 卡片主体内容
         */
        @JsonProperty("body")
        private Body body;

        /**
         * 卡片头部
         */
        @JsonProperty("header")
        private Header header;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Config {
        /**
         * 是否允许多次更新
         */
        @JsonProperty("update_multi")
        private Boolean updateMulti = true;

        /**
         * 样式配置
         */
        @JsonProperty("style")
        private Style style;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Style {
        /**
         * 文本大小配置
         */
        @JsonProperty("text_size")
        private TextSize textSize;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TextSize {
        /**
         * 正常文本大小配置
         */
        @JsonProperty("normal_v2")
        private NormalV2 normalV2;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NormalV2 {
        /**
         * 默认文本大小
         */
        @JsonProperty("default")
        private String defaultValue = "normal";

        /**
         * PC端文本大小
         */
        @JsonProperty("pc")
        private String pc = "normal";

        /**
         * 移动端文本大小
         */
        @JsonProperty("mobile")
        private String mobile = "heading";
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Body {
        /**
         * 布局方向
         */
        @JsonProperty("direction")
        private String direction = "vertical";

        /**
         * 内边距
         */
        @JsonProperty("padding")
        private String padding = "12px 12px 12px 12px";

        /**
         * 卡片元素列表
         */
        @JsonProperty("elements")
        private List<Element> elements;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Element {
        /**
         * 元素标签类型
         */
        @JsonProperty("tag")
        private String tag;

        /**
         * 操作组件的唯一标识。JSON 2.0 新增属性。用于在调用组件相关接口中指定组件。需开发者自定义。
         */
        @JsonProperty("element_id")
        private String elementId;

        /**
         * 组件的外边距，JSON 2.0 新增属性。默认值 "0"，支持范围 [-99,99]px。
         */
        @JsonProperty("margin")
        private String margin;

        /**
         * 采用 markdown 语法编写的内容。2.0 结构不再支持 "[差异化跳转]($urlVal)" 语法
         */
        @JsonProperty("content")
        private String content;

        /**
         * 文本大小。默认值 normal。支持自定义在移动端和桌面端的不同字号。
         */
        @JsonProperty("text_size")
        private String textSize;

        /**
         * 文本对齐方式。默认值 left。
         */
        @JsonProperty("text_align")
        private String textAlign;

        /**
         * 前缀图标
         */
        @JsonProperty("icon")
        private Icon icon;

        /**
         * 按钮文本
         */
        @JsonProperty("text")
        private Text text;

        /**
         * 按钮类型
         */
        @JsonProperty("type")
        private String type;

        /**
         * 宽度
         */
        @JsonProperty("width")
        private String width;

        /**
         * 大小
         */
        @JsonProperty("size")
        private String size;

        /**
         * 行为列表
         */
        @JsonProperty("behaviors")
        private List<Behavior> behaviors;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Icon {
        /**
         * 图标类型
         */
        @JsonProperty("tag")
        private String tag;

        /**
         * 图标的 token。仅在 tag 为 standard_icon 时生效。
         */
        @JsonProperty("token")
        private String token;

        /**
         * 图标颜色。仅在 tag 为 standard_icon 时生效。
         */
        @JsonProperty("color")
        private String color;

        /**
         * 图片的 key。仅在 tag 为 custom_icon 时生效。
         */
        @JsonProperty("img_key")
        private String imgKey;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Text {
        /**
         * 文本标签类型
         */
        @JsonProperty("tag")
        private String tag = "plain_text";

        /**
         * 文本内容
         */
        @JsonProperty("content")
        private String content;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Behavior {
        /**
         * 行为类型
         */
        @JsonProperty("type")
        private String type = "open_url";

        /**
         * 默认链接
         */
        @JsonProperty("default_url")
        private String defaultUrl;

        /**
         * PC端链接
         */
        @JsonProperty("pc_url")
        private String pcUrl;

        /**
         * iOS端链接
         */
        @JsonProperty("ios_url")
        private String iosUrl;

        /**
         * Android端链接
         */
        @JsonProperty("android_url")
        private String androidUrl;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Header {
        /**
         * 标题
         */
        @JsonProperty("title")
        private Title title;

        /**
         * 副标题
         */
        @JsonProperty("subtitle")
        private Subtitle subtitle;

        /**
         * 模板样式
         */
        @JsonProperty("template")
        private String template;

        /**
         * 内边距
         */
        @JsonProperty("padding")
        private String padding = "12px 12px 12px 12px";
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Title {
        /**
         * 标题标签类型
         */
        @JsonProperty("tag")
        private String tag = "plain_text";

        /**
         * 标题内容
         */
        @JsonProperty("content")
        private String content;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Subtitle {
        /**
         * 副标题标签类型
         */
        @JsonProperty("tag")
        private String tag = "plain_text";

        /**
         * 副标题内容
         */
        @JsonProperty("content")
        private String content;
    }
}
