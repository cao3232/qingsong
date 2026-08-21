package com.qingsong.ai.mapper.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingsong.ai.entity.po.knowledge.OriginFileSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 原始文件源 Mapper 接口
 *
 * @author AI Architect
 * @since 2026-03-20
 */
@Mapper
public interface OriginFileSourceMapper extends BaseMapper<OriginFileSource> {

    /**
     * 根据 MD5 查询文件源
     *
     * @param md5 文件 MD5 值
     * @return 文件源信息
     */
    OriginFileSource selectByMd5(@Param("md5") String md5);

    /**
     * 根据对象存储路径查询文件源
     *
     * @param bucketName 桶名称
     * @param objectName 对象名称
     * @return 文件源信息
     */
    OriginFileSource selectByBucketAndObject(@Param("bucketName") String bucketName, @Param("objectName") String objectName);

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
    List<OriginFileSource> selectByIds(@Param("ids") List<String> ids);

    /**
     * 更新文件嵌入的图片列表
     *
     * @param id     文件 ID
     * @param images JSON 格式的图片列表
     * @return 影响行数
     */
    int updateImages(@Param("id") String id, @Param("images") String images);
}
