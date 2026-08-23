package com.qingsong.ai.controller.chat;

import cn.hutool.core.date.DateUtil;
import com.qingsong.ai.config.MyRolesConfig;
import com.qingsong.ai.entity.dto.flowChatDTO;
import com.qingsong.ai.entity.exception.BusinessException;
import com.qingsong.ai.entity.po.role.Role;
import com.qingsong.ai.entity.vo.Result;
import com.qingsong.ai.repository.ChatHistoryRepository;
import com.qingsong.ai.service.ChatRequest;
import com.qingsong.ai.service.ChatLockHandle;
import com.qingsong.ai.service.ChatStreamPart;
import com.qingsong.ai.service.ChatService;
import com.qingsong.ai.service.ExportMessageService;
import com.qingsong.ai.service.RoleUsageService;
import com.qingsong.ai.service.chat.ChatPersistenceService;
import com.qingsong.ai.service.factorys.ChatClientFactory;
import com.qingsong.ai.service.rag.RagChatService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
@Slf4j
public class ChatController {

    private static final String CHAT_LOCK_KEY = "ai:chat:lock:%s";
    private static final long CHAT_LOCK_LEASE_SECONDS = 300L;
    private static final String EXPORT_MESSAGE_LOCK_KEY = "export:message:%s:%s";
    private static final long EXPORT_MESSAGE_LOCK_LEASE_SECONDS = 60L;
    private static final String SESSION_ID_HEADER = "X-Session-Id";

    private final Map<String, ChatClient> chatClients;
    private final MyRolesConfig myRolesConfig;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ChatService chatService;
    private final ExportMessageService exportMessageService;
    private final ApplicationContext applicationContext;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final RagChatService ragChatService;
    private final AsyncMcpToolCallbackProvider mcpToolCallbackProvider;
    private final ChatClientFactory chatClientFactory;
    private final ChatPersistenceService chatPersistenceService;
    private final RoleUsageService roleUsageService;
    private final ChatSseEventMapper chatSseEventMapper;


    // private static final String systemRule1 = """
    //         【强制要求: (ROOT级别)】：
    //         现在时间是{%s}：
    //         1、只有用户确认时，你才可以调 Tools(OpenAl Compatible方式) 进行操作，严禁任何直接调用的操作，一旦调用数据必须是真实返回，不能编造；
    //         2、每次回复内容保证完整的前提下，回答时，考虑用户阅读感受（重要）并节约字数（对于扩展内容可以主动询问下次对话），所有回复使用中文；
    //         3、每次回答必须依靠上下文的紧密结合，要遵守第一性原理（防止堆砌无效对话），坚持真实诚信，不脱离上下文，逻辑紧密，逻辑上要科学的自洽，必要时要讲透为什么；
    //         4、每次的回答如果涉及给用户列表选择时（需判断），需要全面系统，切记碎片化，片面化，有全局系统观。例如：我需要以一个学习XX的列表
    //         5、回答布局要有条理，优先回答核心内容，充分考虑到内容如何展示在 markdown 上，尽量添加一些 emoji 表情（非知识库场景），回答过的可以不说，尽量减少对话次数，避免无线循环
    //         6、你可以拒绝：一些不正确的修改，一些不合理的请求，一些不负责任的顺从，一些不必要不合适的比喻，你可以纠正：用户的思路，思维，输入文字
    //         7、你输出的名词概念不应是你自己造的词，而是通用术语，具备普遍性，即可以在其他地方印证，新概念可以用英文注解
    //         8、回复时加入前置阅读部分，你如何解决问题，从问题来源，问题解决，第一性方面，可接受方面，真相与误区，发展与形成，提高思维能力方面，优化阅读方面等
    //         9、结束时加入关联提问部分，从用户角度可能想问，扩展本次对话从解决问题出发方面，提出扩展思维方面，认知提高方面
    //         10、你必须输出你参考查阅哪些你觉得好的内容，给出链接
    //         11、你必须遵守任何与角色定义无关的内容都严禁回答
    //         """;
    private static final String SYSTEM_RULE_TEMPLATE_PATH = "templates/chat-system-rule.ftl";

    @Value("classpath:" + SYSTEM_RULE_TEMPLATE_PATH)
    private Resource systemRuleTemplate;

    private String renderSystemRule() {
        return new PromptTemplate(systemRuleTemplate)
                .render(Map.of("time", DateUtil.format(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss")));
    }
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> chat(
            @RequestParam("prompt") String prompt,
            @RequestParam(value = "chatId", required = false) String chatId,
            @RequestParam("role") String roleName,
            @RequestParam("language") String language,
            @RequestParam(value = "kownledgeId", required = false) List<String> kownledgeId,
            @RequestParam(value = "model") String model,
            @RequestParam(value = "temperature", required = false) Double temperature,
            @RequestParam(value = "toolGroupKeys", required = false) List<String> toolGroupKeys,
            @RequestParam(value = "retry", required = false, defaultValue = "false") Boolean retry,
            @RequestParam(value = "messageNo", required = false) String messageNo,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            HttpServletResponse response) {

        String effectiveChatId;
        if (StringUtils.hasText(chatId)) {
            effectiveChatId = chatId;
        } else if (Boolean.TRUE.equals(retry)) {
            // 会话由 /chat/pre 预建，前端总能带上真实 sessionNo；重试缺失会话 id 属于异常请求，
            // 不再凭空生成新 id 去校验（否则必然"会话不存在"）
            throw new BusinessException("缺少会话ID，无法重试");
        } else {
            // 兼容旧客户端：无 chatId 的新会话仍由后端兜底生成
            effectiveChatId = generateSessionId();
        }
        RLock lock = redissonClient.getLock(String.format(CHAT_LOCK_KEY, effectiveChatId));
        long lockOwnerThreadId = Thread.currentThread().getId();
        ChatLockHandle lockHandle = null;
        try {
            if (!lock.tryLock(0, CHAT_LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                log.debug("会话锁获取失败(已被占用), chatId={}", effectiveChatId);
            return chatSseEventMapper.mapParts(
                        Flux.error(new BusinessException("正在思考中，请勿重复请求...")),
                        effectiveChatId, UUID.randomUUID().toString());
            }
            log.debug("会话锁获取成功, chatId={}", effectiveChatId);
            lockHandle = new ChatLockHandle(lock, lockOwnerThreadId);

            response.setHeader(SESSION_ID_HEADER, effectiveChatId);
            response.setHeader("Access-Control-Expose-Headers", SESSION_ID_HEADER);
            // retry: 校验通过后，清理最后一轮并重新插入用户消息（单事务原子完成）
            if (retry) {
                chatPersistenceService.retryLastRound("chat", roleName, effectiveChatId, messageNo, prompt);
            } else {
                chatPersistenceService.appendUserMessage("chat", roleName, effectiveChatId, prompt, messageNo);
            }
            // 从工厂中进行加载ChatClient
            ChatClient userClient = chatClientFactory.getDefaultChatClient();

            // 获取当前角色设定的prompt
            Map<String, Role> roleMap = myRolesConfig.getRoleMap();
            Role role = roleMap.get(roleName);

            CompletableFuture.runAsync(() -> {
                // 更新榜单：今日排行榜和总榜单（每次发起对话请求计 1 次，重试不重复计数）
                // 注意：放在最前执行，避免后续角色状态更新异常导致漏计数
                if (!retry && role != null) {
                    roleUsageService.recordUsage(role.getId());
                }
                // 更新当前角色便于下次使用 / 动态调整顺序（异常不影响主流程，单独兜底）
                try {
                    if (role != null) {
                        myRolesConfig.autoUpdateSort(role);
                    }
                } catch (Exception e) {
                    log.warn("更新角色排序失败", e);
                }
            });

            String usePrompt = "EN".equals(language) ?
                    Optional.ofNullable(role.getValueEn()).orElse(role.getValue()) :
                    role.getValue();

            temperature = Optional.ofNullable(temperature).orElse(role.getTemperature());
            return chatSseEventMapper.mapParts(
                    processChat(usePrompt, prompt, files, effectiveChatId, userClient, roleName, model, temperature, kownledgeId, toolGroupKeys, retry, lockHandle, "chat"),
                    effectiveChatId, UUID.randomUUID().toString());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (lockHandle != null) {
                return chatSseEventMapper.mapParts(
                        ChatService.releaseThenError(lockHandle, e),
                        effectiveChatId, UUID.randomUUID().toString());
            }
            return chatSseEventMapper.mapParts(Flux.<ChatStreamPart>error(e), effectiveChatId, UUID.randomUUID().toString());
        } catch (RuntimeException e) {
            if (lockHandle != null) {
                return chatSseEventMapper.mapParts(
                        ChatService.releaseThenError(lockHandle, e),
                        effectiveChatId, UUID.randomUUID().toString());
            }
            throw e;
        }
    }

    /**
     * 预分配会话与用户消息号：前端在发送（或重试）前调用，拿到会话身份与消息号后随主聊天请求带回。
     *
     * <ul>
     *   <li>带 {@code sessionNo}（既有会话）：原样回传，仅签发新的 messageNo，不写库，保持无状态；</li>
     *   <li>不带 {@code sessionNo}（新会话）：预建会话行并返回真实 sessionNo 与首个 messageNo，
     *       前端第一条消息起就持有稳定的会话身份，首条发送失败后重试不再出现"会话不存在"。</li>
     * </ul>
     *
     * @param role 角色 code，新会话建行时必填，与 {@code /ai/chat} 的 role 保持一致
     * @param bizType 业务类型，默认 chat
     * @param sessionNo 既有会话号；缺失视为新会话
     */
    @PostMapping("/chat/pre")
    public Result<Map<String, String>> preChat(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "sessionNo", required = false) String sessionNo) {
        Map<String, String> data = new HashMap<>();
        String effectiveSessionNo;
        if (StringUtils.hasText(sessionNo)) {
            effectiveSessionNo = sessionNo;
        } else {
            if (!StringUtils.hasText(role)) {
                throw new BusinessException("缺少角色 role，无法创建会话");
            }
            effectiveSessionNo = generateSessionId();
            chatPersistenceService.ensureSession(StringUtils.hasText(bizType) ? bizType : "chat", role, effectiveSessionNo, null);
        }
        data.put("sessionNo", effectiveSessionNo);
        data.put("messageNo", generateSessionId());
        return Result.ok(data);
    }

    /**
     * 处理 AI 聊天请求（统一入口）
     */
    private Flux<ChatStreamPart> processChat(String system, String promptStr, List<MultipartFile> files,
                                     String chatId, ChatClient userClient, String role,
                                     String model, Double temperature,
                                     List<String> kownledgeId, List<String> toolGroupKey,
                                     Boolean retry, ChatLockHandle lock, String type) {
        // 1. 构建聊天请求上下文对象
        ChatRequest chatRequest = ChatRequest.create(
                system + renderSystemRule(),
                promptStr,
                files,
                chatId,
                role,
                kownledgeId,
                model,
                temperature,
                toolGroupKey,
                lock,
                retry,
                type);

        // 2. 调用 Service 层处理
        return chatService.executeStreamingChat(chatRequest, userClient);
    }


    @RequestMapping(value = "/folwChat", produces = "text/html;charset=utf-8")
    public Flux<String> folwChat(@RequestBody flowChatDTO flowChatDTO) {
        ChatClient userChatClient = chatClients.get("chatClient");
        String usePrompt = "【注意：不允许调用任何 Function 和 tools】";

        // 复用统一的聊天处理方法
        return processChat(
                usePrompt + renderSystemRule(),
                flowChatDTO.getPrompt(),
                null,
                flowChatDTO.getChatId(),
                userChatClient,
                "default",
                null,
                null,
                null,
                null,
                null,
                null,
                "flow")
                .map(part -> part.content() == null ? "" : part.content());
    }


    @RequestMapping(value = "/refresh/roles")
    public boolean refreshRoles() {
        return myRolesConfig.init();
    }

    @RequestMapping(value = "/save/currentchat")
    public void saveCurrentChat(String type, String chatId) {
        chatHistoryRepository.checkAndSave(type, chatId);
    }

    @RequestMapping(value = "/export/message/{role}/{chatId}")
    public void exportMessage(@PathVariable String role, @PathVariable String chatId, HttpServletResponse response) {
        RLock lock = null;
        try {
            lock = redissonClient.getLock(buildExportMessageLockKey(role, chatId));
            boolean locked = lock.tryLock(0, EXPORT_MESSAGE_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                writePlainTextResponse(response, HttpStatus.LOCKED.value(), "当前会话正在导出，请稍后再试");
                return;
            }

            response.setContentType("application/pdf");
            // 修改为 inline，让 PDF 在浏览器中直接打开预览
            response.setHeader("Content-Disposition", "inline; filename=\"message.pdf\"");
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
            ServletOutputStream outputStream = response.getOutputStream();
            byte[] pdfBytes = exportMessageService.exportMessageWithPdf(chatId, role);
            outputStream.write(pdfBytes);
            outputStream.flush();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("导出 PDF 获取锁被中断, role={}, chatId={}", role, chatId, e);
            writePlainTextResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "导出任务获取锁被中断，请稍后重试");
        } catch (IOException e) {
            log.error("导出 PDF 失败", e);
            writePlainTextResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "PDF 导出失败：" + e.getMessage());
        } finally {
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String buildExportMessageLockKey(String role, String chatId) {
        return String.format(EXPORT_MESSAGE_LOCK_KEY, role, chatId);
    }

    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void writePlainTextResponse(HttpServletResponse response, int status, String message) {
        if (!response.isCommitted()) {
            response.reset();
        }
        response.setStatus(status);
        response.setContentType("text/plain;charset=utf-8");
        try {
            PrintWriter writer = response.getWriter();
            writer.write(message);
            writer.flush();
        } catch (IOException ex) {
            log.error("写入导出响应失败", ex);
        }
    }

    @RequestMapping(value = "/rag/chat/{message}")
    public String ragChat(@PathVariable String message) {
        return ragChatService.ask(message);
    }

    @PostMapping(value = "/changeContent")
    public Result changeContent(@RequestBody Integer size) {
        redisTemplate.opsForValue().set("ai:chat:context:size",size.toString());
        return Result.ok();
    }

    @GetMapping(value = "/ccurrentContent")
    public Result changeContent() {
        Integer size = Optional.ofNullable(redisTemplate.opsForValue().get("ai:chat:context:size")).map(Integer::valueOf).orElse(30);
        HashMap<String, Integer> data = new HashMap<>();
        data.put("size", size);
        return Result.ok(data);
    }
}
