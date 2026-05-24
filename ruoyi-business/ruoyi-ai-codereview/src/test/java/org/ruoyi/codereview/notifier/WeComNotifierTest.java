package org.ruoyi.codereview.notifier;

import cn.hutool.core.io.IoUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 企业微信通知器测试
 */
@Slf4j
public class WeComNotifierTest {

    public static void main(String[] args) {
        String webhookUrl = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=7104736f-a047-4007-a4c8-82a1d2f1944a";

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("msgtype", "markdown");

            Map<String, Object> markdown = new HashMap<>();
            markdown.put("content", "### 🤖 AI代码审查结果\n\n" +
                    "> **项目**: TrendRadar\n" +
                    "> **平台**: GitHub\n" +
                    "> **作者**: MuSan-Li\n" +
                    "> **分支**: feature/test → main\n\n" +
                    "---\n\n" +
                    "#### 📊 评分: 85分 ✅\n\n" +
                    "| 维度 | 得分 | 满分 |\n" +
                    "|------|------|------|\n" +
                    "| 功能性健壮性 | 34 | 40 |\n" +
                    "| 安全风险 | 26 | 30 |\n" +
                    "| 最佳实践 | 17 | 20 |\n" +
                    "| 性能效率 | 4 | 5 |\n" +
                    "| Commit信息 | 4 | 5 |\n\n" +
                    "---\n\n" +
                    "#### ⚠️ 主要问题:\n" +
                    "1. 空指针检查缺失\n" +
                    "2. 异常处理不完整\n" +
                    "3. 类型检查缺失\n\n" +
                    "#### ✅ 改进建议:\n" +
                    "- 添加空指针检查\n" +
                    "- 增强异常处理\n" +
                    "- 添加类型注解\n\n" +
                    "---\n" +
                    "> 审查时间: 2026-05-19\n" +
                    "> AI审查由 ruoyi-ai-codereview 提供");
            requestBody.put("markdown", markdown);

            String jsonStr = JSONUtil.toJsonStr(requestBody);
            System.out.println("发送内容: " + jsonStr);

            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonStr.getBytes(StandardCharsets.UTF_8));
            }

            String response = IoUtil.read(conn.getInputStream(), StandardCharsets.UTF_8);
            System.out.println("响应: " + response);

            JSONObject result = JSONUtil.parseObj(response);
            if ("0".equals(result.getStr("errcode"))) {
                System.out.println("✅ 通知发送成功！");
            } else {
                System.out.println("❌ 通知发送失败: " + result.getStr("errmsg"));
            }
        } catch (Exception e) {
            System.err.println("发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}