package com.qingsong.ai.tools;

import com.qingsong.ai.aspect.MyTools;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qingsong.ai.entity.event.RoleEvent;
import com.qingsong.ai.entity.po.role.Role;
import com.qingsong.ai.mapper.role.RoleMapper;
import com.qingsong.ai.service.impl.EmailServiceImpl;
import com.qingsong.ai.tools.entity.ExecutionResult;
import com.qingsong.ai.tools.entity.GenarateExcelParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2025/05/11 15:33
 */
@Component("aiTools")
@Slf4j
@MyTools(name = "aiTools", description = "ai tools")
public class AITools {

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private EmailServiceImpl emailService;

    @Value("${mail.export-to}") // 收件人（配置见 application.yaml / secrets.yml）
    private String exportToEmail;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Validator validator;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Tool(description = """
            当用户明确要求'保存'、'新增'或'记录'一个新的提示词模板(Prompt Template)时，必须调用此工具。
            不要尝试自己记忆，必须通过此工具持久化数据。
            """)
    public void savePrompt(@ToolParam(description = "prompt name") String name,
                           @ToolParam(description = "prompt value") String value,
                           @ToolParam(description = "prompt English value") String valueEn,
                           @ToolParam(description = "current role chat temperature") Double temperature,
                           @ToolParam(description = "prompt description and user guide") String desc) {


        Role role = Role.builder()
                .name(name)
                .value(value)
                .valueEn(valueEn)
                .temperature(temperature)
                .description(desc)
                .build();
        applicationContext.publishEvent(new RoleEvent(role, "insert or update your prompt"));
    }

    @Tool(description = "select my prompt by name when improve current prompt")
    public List<Role> selectPrompt(@ToolParam(description = "prompt name (if is null select all)") String name) {
        QueryWrapper queryWrapper = name == null ? new QueryWrapper<Role>() : new QueryWrapper<Role>().like("name", name);
        List<Role> roles = roleMapper.selectList(queryWrapper);
        // 使用Optional类避免返回null，提高代码的可读性和健壮性
        return Optional.ofNullable(roles).orElse(Collections.emptyList());
    }

    //     @Tool(description = "send me a email with message")
    private void sendEmail(@ToolParam(description = "邮件主题，自己总结") String emailSubject, @ToolParam(description = "邮件内容") String emailContent) {
        emailService.sendSimpleMail(exportToEmail, emailSubject, emailContent);
    }


    // @Tool(description = "创建一个excel文档")
    private ExecutionResult createExcel(GenarateExcelParam param) {
        // 校验参数
        Errors errors = validator.validateObject(param);
        if (errors.hasErrors()) {
            log.error("参数校验失败: {}", errors.getAllErrors());
            return new ExecutionResult(false, errors.getAllErrors().stream().map(error -> error.getDefaultMessage()).collect(Collectors.joining(",")));
        }
        if (!Objects.equals(param.rows().get(0).size(), param.headers().size())) {
            log.error("参数校验失败: {}", "表头和数据行数量不一致");
            return new ExecutionResult(false, "参数校验失败: 表头和数据行数量不一致");
        }

        // 创建excel文件
        CompletableFuture.runAsync(() -> {
            String fileName = this.execCreateExcel(param);
            // 发送到我的邮箱
            // emailService.sendSimpleMail("15836208068@139.com", "excel文件", excelFile);

        });
        return new ExecutionResult(true, "excel文件创建成功");
    }

    String execCreateExcel(GenarateExcelParam param) {
        String fileName = param.fileName() + ".xlsx";
        File excelFile = new File(fileName);
        try {
            // 获取列数
            List<String> headers = param.headers();
            List<List<String>> headList = headers.stream()
                    .map(header -> List.of(header)) // 将 "姓名" 变成 ["姓名"]
                    .collect(Collectors.toList());

            EasyExcel.write()
                    .file(excelFile)
                    .head(headList)
                    .sheet(param.sheetName())
                    .doWrite(param.rows());

            return fileName;

        } catch (Exception e) {
            log.error("创建excel文件失败: {}", e.getMessage(), e);
        }
        return null;
    }


    @Tool(description = "简单存档当前会话信息，赋予AI角色级记忆能力，防止不同会话生成相似的内容，会话结束时可主动提示用户保存")
    public void saveRoleHistory(@ToolParam(description = "角色名称") String roleName, @ToolParam(description = "记忆内容") String chatHistory) {
        // 校验角色是否存在
        // 保存到redis中
        redisTemplate.opsForHash().put(roleName + ":menory", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), chatHistory);
    }

    @Tool(description = "根据角色名称，获取历史会话内容,用于区分当前会话不同于之前的会话，只能获取简要信息")
    public List<String> getRoleHistory(@ToolParam(description = "角色名称") String roleName) {
        // 获取历史会话内容
        List<Object> values = redisTemplate.opsForHash().values(roleName + ":menory");
        return values.stream().map(value -> value.toString()).collect(Collectors.toList());
    }

}
