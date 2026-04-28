package com.intell_BI_backend.RabbitMq;


import com.alibaba.fastjson.JSONObject;
import com.intell_BI_backend.model.entity.Chart;
import com.intell_BI_backend.service.ChartService;
import com.intell_BI_backend.service.DeepSeekService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Component
public class MessageRec {

    @Resource
    private ChartService chartService;

    @Resource
    private DeepSeekService deepSeekService;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;


    /**
     * 接收图表制作消息->线程池处理
     * @param message
     * @param channel
     * @param deliveryTag
     */
    @RabbitListener(queues = {Constant.QUEUE_NAME}, ackMode = "MANUAL")
    public void receive(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("接收到消息：{}", message);
        try {
            long chartId = Long.parseLong(message);
            // 提交到线程池异步处理
            threadPoolExecutor.execute(() -> processChart(chartId));
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("消息处理失败", e);
            try {
                channel.basicAck(deliveryTag, false);
            } catch (IOException ex) {
                log.error("确认消息失败", ex);
            }
        }
    }

    private void processChart(long chartId) {
        Chart chart = chartService.getById(chartId);
        if (chart == null) {
            log.error("图表不存在：{}", chartId);
            return;
        }

        // 更新状态为执行中
        Chart updateChart = new Chart();
        updateChart.setId(chartId);
        updateChart.setStatus("processing");
        if (!chartService.updateById(updateChart)) {
            updateChartError(chartId, "图表状态更新为\"执行中\"失败！");
            return;
        }

        // 重构prompt
        String prompt = "分析目标：" + chart.getGoal() + "\n"
                + "图表类型：" + chart.getChartType() + "\n"
                + "数据如下：\n" + chart.getChartData();

        // 调用AI分析
        JSONObject aiResult = deepSeekService.analyzeCsv(prompt);
        if (aiResult == null) {
            updateChartError(chartId, "AI 生成图表失败！");
            return;
        }

        // 分离结果
        String genChart = aiResult.getString("genChart");
        String genResult = aiResult.getString("genResult");
        String genChartStr = cleanEchartsJsToJson(genChart);

        // 更新为成功
        Chart successChart = new Chart();
        successChart.setId(chartId);
        successChart.setStatus("succeed");
        successChart.setGenChart(genChartStr);
        successChart.setGenResult(genResult);
        if (!chartService.updateById(successChart)) {
            updateChartError(chartId, "图表状态更新为\"成功\"失败！");
        }
    }

    private void updateChartError(long chartId, String message) {
        Chart chart = new Chart();
        chart.setId(chartId);
        chart.setStatus("failed");
        chart.setMessage(message);
        boolean result = chartService.updateById(chart);
        if (!result) {
            log.error("更新图表状态为'failed'失败！chartId:{}, message:{}", chartId, message);
        }
    }

    private String cleanEchartsJsToJson(String echartsJs) {
        if (StringUtils.isBlank(echartsJs)) {
            return "{}";
        }
        String jsonStr = echartsJs.replaceAll("option\\s*=\\s*", "");
        jsonStr = jsonStr.replaceAll(";$", "").replaceAll(",\\s*}$", "}");
        jsonStr = jsonStr.replace("'", "\"");
        jsonStr = jsonStr.replaceAll("\\n|\\r", "").trim();
        try {
            JSONObject.parseObject(jsonStr);
            return jsonStr;
        } catch (Exception e) {
            return "{}";
        }
    }
}
