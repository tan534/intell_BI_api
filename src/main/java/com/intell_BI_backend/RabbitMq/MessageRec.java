package com.intell_BI_backend.RabbitMq;


import com.alibaba.fastjson.JSONObject;
import com.intell_BI_backend.common.ErrorCode;
import com.intell_BI_backend.exception.BusinessException;
import com.intell_BI_backend.model.entity.Chart;
import com.intell_BI_backend.service.ChartService;
import com.intell_BI_backend.service.DeepSeekService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.aop.framework.AopContext;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
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
     * 接收图表生成任务
     */
    @RabbitListener(queues = {Constant.QUEUE_NAME}, ackMode = "MANUAL")
    public void receive(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("接收到新图表id消息：{}", message);
        long chartId;
        try {
            chartId = Long.parseLong(message);
        } catch (NumberFormatException e) {
            log.error("消息格式错误，无法解析chartId：{}", message);
            basicAck(channel, deliveryTag);
            return;
        }
        // 获取代理对象，子线程中通过代理调用才能触发 @Retryable
        MessageRec proxy = (MessageRec) AopContext.currentProxy();
        threadPoolExecutor.execute(() -> {
            proxy.processWithRetry(chartId);
            // 无论成功还是 @Recover 兜底，都确认消息移出队列
            basicAck(channel, deliveryTag);
        });
    }

    /**
     * 处理图表生成任务（带重试，最多2次）
     */
    @Retryable(value = Exception.class, maxAttempts = 2, backoff = @Backoff(delay = 2000))
    public void processWithRetry(long chartId) {
        processChart(chartId);
    }

    /**
     * 重试耗尽后的兜底处理：更新图表为失败状态
     */
    @Recover
    public void recoverProcessWithRetry(Exception e, long chartId) {
        log.error("图表id:{}处理失败，已达最大重试次数", chartId, e);
        updateChartError(chartId, "AI 生成图表失败：" + e.getMessage());
    }

    /**
     * 处理图表制作任务
     */
    private void processChart(long chartId) {
        Chart chart = chartService.getById(chartId);
        if (chart == null) {
            log.error("图表不存在：{}", chartId);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图表不存在");
        }

        // 更新状态为执行中
        Chart updateChart = new Chart();
        updateChart.setId(chartId);
        updateChart.setStatus("processing");
        if (!chartService.updateById(updateChart)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图表状态更新为\"执行中\"失败");
        }

        // 重构prompt
        String prompt = "分析目标：" + chart.getGoal() + "\n"
                + "图表类型：" + chart.getChartType() + "\n"
                + "数据如下：\n" + chart.getChartData();

        // 调用AI分析
        JSONObject aiResult = deepSeekService.analyzeCsv(prompt);
        if (aiResult == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 生成图表失败");
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
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图表状态更新为\"成功\"失败");
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

    private void basicAck(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            log.error("确认消息失败", e);
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