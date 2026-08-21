package com.qingsong.ai.entity.po.knowledge;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qingsong.ai.service.originfile.StorageFile;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 原始文件源实体类
 *
 * @author AI Architect
 * @since 2026-03-20
 */
@Data
@TableName("origin_file_source")
public class OriginFileSource implements StorageFile {

    /**
     * 文件唯一标识
     */
    @TableField("id")
    private String id;

    /**
     * 文件名
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 文件存储路径
     */
    @TableField("path")
    private String path;

    /**
     * 是否为图片文件
     */
    @TableField("is_image")
    private Boolean image;

    /**
     * 对象存储桶名称
     */
    @TableField("bucket_name")
    private String bucketName;

    /**
     * 对象存储中的文件名
     */
    @TableField("object_name")
    private String objectName;

    /**
     * 文件的 MIME 类型
     */
    @TableField("content_type")
    private String contentType;

    /**
     * 文件大小（字节）
     */
    @TableField("size")
    private Long size;

    /**
     * 文件 MD5 哈希值
     */
    @TableField("md5")
    private String md5;

    /**
     * 文档内包含的图片列表（JSON 数组）
     */
    @TableField("images")
    private String images;

    /**
     * 创建时间
     */
    @TableField(value = "create_date", fill = FieldFill.INSERT)
    private LocalDateTime createDate;

    /**
     * 更新时间
     */
    @TableField(value = "update_date", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateDate;
}
