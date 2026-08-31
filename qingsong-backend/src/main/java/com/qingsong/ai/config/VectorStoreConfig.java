package com.qingsong.ai.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2026/03/15 19:08
 */
@Configuration
public class VectorStoreConfig {

    private final PgVectorStoreProperties pgVectorStoreProperties;

    /**
     * 构造函数注入（使用 @RequiredArgsConstructor 会生成此构造函数）
     */
    public VectorStoreConfig(PgVectorStoreProperties pgVectorStoreProperties) {
        this.pgVectorStoreProperties = pgVectorStoreProperties;
    }

    // /**
    //  * 手动创建 PgVectorStoreProperties Bean
    //  * 因为禁用了自动配置，需要自己创建这个配置对象
    //  */
    // @Bean
    // @ConfigurationProperties(prefix = "spring.ai.vectorstore.pgvector")
    // public PgVectorStoreProperties pgVectorStoreProperties() {
    //     return new PgVectorStoreProperties();
    // }

    /**
     * PgVector 专用数据源（PostgreSQL）
     */
    @Bean
    @ConfigurationProperties("spring.ai.vectorstore.pgvector.datasource")
    public DataSourceProperties pgVectorDataSourceProperties() {
        return new DataSourceProperties();
    }


    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mysqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "mysqlDataSource")
    public DataSource mysqlDataSource(@Qualifier("mysqlDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }


    @Bean(name = "pgVectorDataSource")
    @ConditionalOnProperty(prefix = "spring.ai.vectorstore.pgvector.datasource", name = "url")
    public DataSource pgVectorDataSource(
            @Qualifier("pgVectorDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }


    @Value("${spring.ai.openai.embedding.base-url}")
    private String embeddingBaseUrl;

    @Value("${spring.ai.openai.embedding.api-key}")
    private String embeddingApiKey;

    @Value("${spring.ai.openai.embedding.options.model}")
    private String embeddingModel;

    /**
     * 🔑 创建 EmbeddingModel Bean，使用配置的模型名称
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        // 从配置中读取模型名称（Spring AI 会自动从 spring.ai.embedding.options.model 读取）
        OpenAiApi openAiApi = OpenAiApi.builder().baseUrl(embeddingBaseUrl).apiKey(embeddingApiKey).build();
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(embeddingModel).build());
    }

    /**
     * TokenTextSplitter Bean，供知识入库文本切分使用（参数见 qingsong.rag.chunk-*）
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter(RagProperties ragProperties) {
        return TokenTextSplitter.builder()
                .withChunkSize(ragProperties.getChunkSize())
                .withMinChunkSizeChars(ragProperties.getMinChunkSizeChars())
                .withMinChunkLengthToEmbed(ragProperties.getMinChunkLengthToEmbed())
                .withMaxNumChunks(ragProperties.getMaxNumChunks())
                .build();
    }

    @Bean
    @ConditionalOnBean(name = "pgVectorDataSource")
    public JdbcTemplate pgVectorJdbcTemplate(@Qualifier("pgVectorDataSource") DataSource pgVectorDataSource) {
        return new JdbcTemplate(pgVectorDataSource);
    }

    @Bean("pgVectorStore")
    @ConditionalOnBean(name = "pgVectorJdbcTemplate")
    public VectorStore pgVectorStore(@Qualifier("pgVectorJdbcTemplate") JdbcTemplate pgVectorJdbcTemplate,
                                     EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(pgVectorJdbcTemplate, embeddingModel)
                .initializeSchema(pgVectorStoreProperties.isInitializeSchema())
                .dimensions(pgVectorStoreProperties.getDimensions())
                .distanceType(pgVectorStoreProperties.getDistanceType())
                .indexType(pgVectorStoreProperties.getIndexType())
                .maxDocumentBatchSize(pgVectorStoreProperties.getMaxDocumentBatchSize())
                .schemaName(pgVectorStoreProperties.getSchemaName())
                .vectorTableName(pgVectorStoreProperties.getTableName())
                .removeExistingVectorStoreTable(pgVectorStoreProperties.isRemoveExistingVectorStoreTable())
                .idType(pgVectorStoreProperties.getIdType())
                .vectorTableValidationsEnabled(pgVectorStoreProperties.isSchemaValidation())
                .build();
    }


}
