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
            "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）：\n" +
            "1. 输出结构：{\"genChart\": \"标准ECharts JSON配置字符串\", \"genResult\": [\"分析结论\", \"分析建议\"]}\n" +
            "2. genChart 必须是：\n" +
            "- 纯JSON格式的ECharts配置（无option=、无分号、无单引号、无注释）\n" +
            "- 所有字符串用双引号\n" +
            "- 直接是对象，比如 {\"title\":{\"text\":\"标题\"},\"xAxis\":...}\n" +
            "3. genResult 是数组格式，每条结论一句话（20-50字）\n" +
            "4. 不要返回任何JS代码、不要加markdown、不要加解释文字\n";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 调用DeepSeek分析CSV数据
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
