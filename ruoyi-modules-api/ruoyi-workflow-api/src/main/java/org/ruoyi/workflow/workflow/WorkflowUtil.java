package org.ruoyi.workflow.workflow;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import dev.langchain4j.data.message.UserMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.langchain4j.generators.StreamingChatGenerator;
import org.bsc.langgraph4j.state.AgentState;
import org.ruoyi.chat.factory.ChatServiceFactory;
import org.ruoyi.chat.service.chat.IChatService;
import org.ruoyi.common.chat.entity.chat.Message;
import org.ruoyi.common.chat.request.ChatRequest;
import org.ruoyi.workflow.base.NodeInputConfigTypeHandler;
import org.ruoyi.workflow.entity.WorkflowNode;
import org.ruoyi.workflow.enums.WfIODataTypeEnum;
import org.ruoyi.workflow.util.JsonUtil;
import org.ruoyi.workflow.workflow.data.NodeIOData;
import org.ruoyi.workflow.workflow.data.NodeIODataContent;
import org.ruoyi.workflow.workflow.def.WfNodeParamRef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.ruoyi.workflow.cosntant.AdiConstant.WorkflowConstant.DEFAULT_OUTPUT_PARAM_NAME;

@Slf4j
@Component
public class WorkflowUtil {

    @Resource
    private ChatServiceFactory chatServiceFactory;

    // 添加默认名称的成员变量
    private static final String DEFAULT_NODE_NAME = "input";

    // 添加文档解析的前缀字段
    private static final String UPLOAD_FILE_API_PREFIX = "fileid";

    public static String renderTemplate(String template, List<NodeIOData> values) {
        // 🔒 关键修复：如果 template 为 null，直接返回 null 或空字符串
        if (template == null) {
            return null; // 或 return ""; 根据业务需求
        }

        String result = template;

        // 防御 values 为 null
        if (values == null) {
            return result;
        }

        for (NodeIOData next : values) {
            if (next == null || next.getName() == null) {
                continue;
            }

            String name = next.getName();
            NodeIODataContent<?> dataContent = next.getContent();
            if (dataContent == null || dataContent.getValue() == null) {
                // 变量值为 null，替换为空字符串
                result = result.replace("{" + name + "}", "");
                continue;
            }

            String replacement;
            if (dataContent.getType().equals(WfIODataTypeEnum.FILES.getValue())) {
                @SuppressWarnings("unchecked")
                List<String> value = (List<String>) dataContent.getValue();
                replacement = String.join(",", value);
            } else if (dataContent.getType().equals(WfIODataTypeEnum.OPTIONS.getValue())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> value = (Map<String, Object>) dataContent.getValue();
                replacement = value.toString();
            } else {
                replacement = dataContent.getValue().toString();
            }

            result = result.replace("{" + name + "}", replacement);
        }

        return result;
    }

    public static String getHumanFeedbackTip(String nodeUuid, List<WorkflowNode> wfNodes) {
        WorkflowNode wfNode = wfNodes.stream()
                .filter(item -> item.getUuid().equals(nodeUuid))
                .findFirst().orElse(null);
        if (null == wfNode) {
            return "";
        }
        String wfNodeNodeConfig = wfNode.getNodeConfig();
        if (StrUtil.isBlank(wfNodeNodeConfig)) {
            return "";
        }
        Map<String, Object> map = JsonUtil.toMap(wfNodeNodeConfig);
        Object tip = map.getOrDefault("tip", "");
        return String.valueOf(tip);
    }

    public void streamingInvokeLLM(WfState wfState, WfNodeState state, WorkflowNode node, String category,
                                   String modelName, List<UserMessage> systemMessage) {
        log.info("stream invoke, category: {}, modelName: {}", category, modelName);

        // 根据 category 获取对应的 ChatService（不使用计费代理，工作流场景单独计费）
        IChatService chatService = chatServiceFactory.getOriginalService(category);

        StreamingChatGenerator<AgentState> streamingGenerator = StreamingChatGenerator.builder()
                .mapResult(response -> {
                    String responseTxt = response.aiMessage().text();
                    log.info("llm response:{}", responseTxt);

                    // 传递所有输入数据 + 添加 LLM 输出
                    wfState.getNodeStateByNodeUuid(node.getUuid()).ifPresent(item -> {
                        List<NodeIOData> outputs = new ArrayList<>(item.getInputs());
                        NodeIOData output = NodeIOData.createByText(DEFAULT_OUTPUT_PARAM_NAME, "", responseTxt);
                        outputs.add(output);
                        item.setOutputs(outputs);
                    });

                    return Map.of("completeResult", response.aiMessage().text());
                })
                .startingNode(node.getUuid())
                .startingState(state)
                .build();

        // 构建 ruoyi-ai 的 ChatRequest
        List<Message> messages = new ArrayList<>();

        List<NodeIOData> inputs = state.getInputs();
        addUserMessage(node, inputs, messages);
        addSystemMessage(systemMessage, inputs, messages);

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setModel(modelName);
        chatRequest.setMessages(messages);

        // 使用工作流专用方法
        chatService.chat(chatRequest, streamingGenerator.handler());
        wfState.getNodeToStreamingGenerator().put(node.getUuid(), streamingGenerator);
    }

    /**
     * 添加用户信息
     *
     * @param node
     * @param messages
     */
    private void addUserMessage(WorkflowNode node, List<NodeIOData> userMessage, List<Message> messages) {
        if (CollUtil.isEmpty(userMessage)) {
            return;
        }

        WfNodeInputConfig nodeInputConfig = NodeInputConfigTypeHandler.fillNodeInputConfig(node.getInputConfig());
        List<WfNodeParamRef> refInputs = nodeInputConfig.getRefInputs();
        Set<String> nameSet = CollStreamUtil.toSet(refInputs, WfNodeParamRef::getName);

        // 检查是否存在包含fileId的NodeIOData对象
        boolean hasFileIdData = hasFileIdData(userMessage);
        // 构建消息列表
        List<Message> messageList = buildMessageList(userMessage, nameSet, hasFileIdData, DEFAULT_NODE_NAME);
        // 如果没有找到匹配的消息，尝试使用input字段
        if (CollUtil.isEmpty(messageList)) {
            messageList = buildMessageList(userMessage, Set.of("input"), hasFileIdData, DEFAULT_NODE_NAME);
        }
        messages.addAll(messageList);
    }


    /**
     * 检查是否包含fileId数据
     */
    private boolean hasFileIdData(List<NodeIOData> userMessage) {
        return userMessage.stream().anyMatch(item ->
                item != null &&
                        item.getContent() != null &&
                        item.getContent().getValue() != null &&
                        String.valueOf(item.getContent().getValue()).toLowerCase().contains(UPLOAD_FILE_API_PREFIX)
        );
    }

    /**
     * 构建消息列表
     */
    private List<Message> buildMessageList(List<NodeIOData> userMessage, Set<String> nameSet, boolean hasFileIdData, String defaultName) {
        String role = hasFileIdData ? "system" : "user";

        return userMessage.stream()
                .filter(item -> item != null && item.getName() != null)
                .filter(item -> nameSet.contains(item.getName()) || defaultName.equals(item.getName()))
                .map(item -> getMessage(role, item.getContent().getValue()))
                .toList();
    }

    /**
     * 组装message对象
     *
     * @param role
     * @param value
     * @return
     */
    private Message getMessage(String role, Object value) {
        log.info("Creating message with role: {}, content: {}", role, value); // 🔥
        Message message = new Message();
        message.setRole(role);
        message.setContent(value);
        return message;
    }

    /**
     * 添加系统信息
     *
     * @param systemMessage
     * @param messages
     */
    private void addSystemMessage(List<UserMessage> systemMessage, List<NodeIOData> userMessage, List<Message> messages) {
        log.info("addSystemMessage received: {}", systemMessage);

        if (CollUtil.isEmpty(systemMessage)) {
            return;
        }

        // 检查是否存在包含fileId的NodeIOData对象
        boolean hasFileIdData = hasFileIdData(userMessage);

        // 根据是否有fileId数据确定消息角色
        String role = hasFileIdData ? "user" : "system";

        // 添加消息
        systemMessage.stream()
                .map(userMsg -> getMessage(role, userMsg.singleText()))
                .forEach(messages::add);
    }
}
