package org.ruoyi.chat.service.chat.impl;

import lombok.extern.slf4j.Slf4j;
import org.ruoyi.chat.config.ChatConfig;
import org.ruoyi.chat.enums.ChatModeType;
import org.ruoyi.chat.listener.SSEEventSourceListener;
import org.ruoyi.chat.service.chat.IChatService;
import org.ruoyi.chat.support.ChatServiceHelper;
import org.ruoyi.common.chat.entity.chat.ChatCompletion;
import org.ruoyi.common.chat.entity.chat.Message;
import org.ruoyi.common.chat.openai.OpenAiStreamClient;
import org.ruoyi.common.chat.request.ChatRequest;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.vo.ChatModelVo;
import org.ruoyi.service.IChatModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * RagFlow 聊天管理
 * 使用 RagFlow 兼容 OpenAI 的 API 接口
 *
 * API: /api/v1/chats_openai/{chat_id}/chat/completions
 *
 * 配置说明：
 * - apiHost: RagFlow 服务地址，如 http://localhost:9380
 * - apiKey: RagFlow API 密钥
 * - remark(备注): 聊天助手的 chat_id
 *
 * @author Robust_H
 */
@Service
@Slf4j
public class RagFlowServiceImpl implements IChatService {

    private static final String CHAT_API_PATH = "/api/v1/chats_openai/%s/chat/completions";

    @Autowired
    private IChatModelService chatModelService;

    @Override
    public SseEmitter chat(ChatRequest chatRequest, SseEmitter emitter) {
        ChatModelVo chatModelVo = chatModelService.selectModelByName(chatRequest.getModel());

        // 构建 RagFlow API 地址
        String apiHost = buildRagFlowApiHost(chatModelVo);

        // 创建 OpenAI 兼容的流式客户端
        OpenAiStreamClient openAiStreamClient = ChatConfig.createOpenAiStreamClient(apiHost, chatModelVo.getApiKey());

        List<Message> messages = chatRequest.getMessages();
        SSEEventSourceListener listener = ChatServiceHelper.createOpenAiListener(emitter, chatRequest);

        // 构建聊天请求，RagFlow 兼容 OpenAI 格式
        ChatCompletion completion = ChatCompletion
                .builder()
                .messages(messages)
                .model(chatRequest.getModel())
                .stream(true)
                .build();

        try {
            openAiStreamClient.streamChatCompletion(completion, listener);
        } catch (Exception ex) {
            log.error("RagFlow 请求失败：{}", ex.getMessage());
            ChatServiceHelper.onStreamError(emitter, ex.getMessage());
        }

        return emitter;
    }


    /**
     * 构建 RagFlow API 地址
     *
     * @param chatModelVo 模型配置
     * @return 完整的 API 地址
     */
    private String buildRagFlowApiHost(ChatModelVo chatModelVo) {
        String baseHost = chatModelVo.getApiHost();
        String chatId = chatModelVo.getModelName();

        // 移除末尾的斜杠
        if (StringUtils.isNotEmpty(baseHost) && baseHost.endsWith("/")) {
            baseHost = baseHost.substring(0, baseHost.length() - 1);
        }

        // 构建聊天助手 API 地址
        String apiPath = String.format(CHAT_API_PATH, chatId);
        log.info("RagFlow 使用聊天助手模式，chatId: {}", chatId);
        return baseHost + apiPath;
    }

    @Override
    public String getCategory() {
        return ChatModeType.RAGFLOW.getCode();
    }
}
