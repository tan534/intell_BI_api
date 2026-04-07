package com.intell_BI_backend.service.impl;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.intell_BI_backend.service.DeepSeekService;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public  class DeepSeekServiceImpl implements DeepSeekService {
    private static final String DEEPSEEK_API_KEY = "sk-9481d67191a343dfa4848579992f8442";
    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String SYSTEM_PROMPT = "你是专业智能BI数据分析师，严格按以下规则处理CSV数据：\n" +
            "1. 输入是带\\n换行的CSV字符串，第一行是表头（必须完整读取，如日期、用户数），后续是数据行，逗号分隔列\n" +
            "2. 所有分析100%基于输入数据，严禁编造、外推，只基于表头字段和数据值\n" +
            "3. 输出仅返回纯JSON，无任何额外文字、markdown、注释，严格遵循以下结构：\n" +
            "{\n" +
            "  \"analysisSummary\": [\"核心结论1（对应数据值）\", \"核心结论2（对应数据值）\"],\n" +
            "  \"chartConfig\": {\n" +
            "    \"chartType\": \"line/bar/pie/scatter/histogram\",\n" +
            "    \"xAxis\": \"表头字段（如日期）\",\n" +
            "    \"yAxis\": \"表头字段（如用户数）\",\n" +
            "    \"series\": [\"用户数\"],\n" +
            "    \"title\": \"图表标题（业务化）\"\n" +
            "  },\n" +
            "  \"dataInsight\": \"可落地业务建议，基于数据趋势\"\n" +
            "}\n" +
            "4. 异常处理：数据空/格式错→{\"error\":\"数据格式异常\"}；数据<3条→{\"error\":\"数据量不足\"}\n" +
            "5. 时间序列（日期）优先折线图，计数数据（用户数）计算总和、均值、峰值、谷值";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 调用DeepSeek分析CSV数据（适配你带\n的CSV）
     * @param csvData 接口输出的CSV字符串（如"日期,用户数\n1,10\n2,20\n3,30"）
     * @return 标准JSON分析结果（适配BI前端渲染）
     */
    @Override
    public JSONObject analyzeCsv(String csvData) {
        try {
            // 2. 构建请求头（鉴权+格式）
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + DEEPSEEK_API_KEY);

            // 3. 构建请求体（System Prompt + 用户CSV数据）
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "deepseek-chat"); // 模型选择
            requestBody.put("temperature", 0.1); // 温度=0.1，保证输出稳定、不编造（关键！）
            requestBody.put("max_tokens", 1024); // 输出长度限制
            // 消息列表：system（预设）+ user（CSV数据）
            requestBody.put("messages", new Object[]{
                    new JSONObject().fluentPut("role", "system").fluentPut("content", SYSTEM_PROMPT),
                    new JSONObject().fluentPut("role", "user").fluentPut("content", "分析以下CSV数据：\n" + csvData)
            });

            // 4. 发送POST请求
            HttpEntity<JSONObject> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    DEEPSEEK_API_URL, HttpMethod.POST, requestEntity, String.class
            );

            // 5. 解析DeepSeek返回结果（提取content里的JSON）
            JSONObject responseJson = JSON.parseObject(response.getBody());
            String aiOutput = responseJson.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            // 6. 转成标准BI JSON（直接返回给前端）
            return JSON.parseObject(aiOutput);
        } catch (Exception e) {
            // 异常处理：返回错误JSON
            JSONObject errorResult = new JSONObject();
            errorResult.put("error", "DeepSeek调用失败：" + e.getMessage());
            return errorResult;
        }
    }
}
