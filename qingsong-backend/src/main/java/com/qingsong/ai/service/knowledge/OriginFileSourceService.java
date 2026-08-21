package com.qingsong.ai.service.knowledge;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingsong.ai.entity.po.knowledge.DocumentBase;
import com.qingsong.ai.entity.po.knowledge.OriginFileSource;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * 原始文件源 Service 接口
 *
 * @author AI Architect
 * @since 2026-03-20
 */
public interface OriginFileSourceService extends IService<OriginFileSource> {

    /**
     * 根据 MD5 查询文件源
     *
     * @param md5 文件 MD5 值
     * @return 文件源信息
     */
    OriginFileSource getByMd5(String md5);

    /**
     * 根据对象存储路径查询文件源
     *
     * @param bucketName 桶名称
     * @param objectName 对象名称
     * @return 文件源信息
     */
    OriginFileSource getByBucketAndObject(String bucketName, String objectName);

    /**
     * 统计文件总数
     *
     * @return 文件总数
     */
    Long countTotalFiles();

    /**
     * 根据文件 ID 列表批量查询
     *
     * @param ids 文件 ID 列表
     * @return 文件源列表
     */
    List<OriginFileSource> getByIds(List<String> ids);

    /**
     * 更新文件嵌入的图片列表
     *
     * @param id     文件 ID
     * @param images JSON 格式的图片列表
     * @return 是否成功
     */
    boolean updateImages(String id, String images);

    /**
     * 保存文件源信息
     *
     * @param fileSource 文件源信息
     * @return 是否成功
     */
    boolean saveFileSource(OriginFileSource fileSource);

    /**
     * 根据 MD5 检查文件是否存在
     *
     * @param md5 文件 MD5 值
     * @return 是否存在
     */
    boolean existsByMd5(String md5);

    boolean uploadFile(MultipartFile file, String knowledgeId);

    boolean deleteFile(OriginFileSource fileSource);

    InputStream downloadFileByDocument(DocumentBase document);
}
