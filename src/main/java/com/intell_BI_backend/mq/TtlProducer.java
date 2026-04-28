package com.intell_BI_backend.mq;

import com.rabbitmq.client.*;

import java.nio.charset.StandardCharsets;


public class TtlProducer {

    private final static String QUEUE_NAME = "ttl_queue";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        // 创建链接通道
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            //发送消息
            String message = "Hello World!";

            // 创建一个临时队列，用于接收回调消息
            BasicProperties props = new AMQP.BasicProperties
                    .Builder()
                    .expiration("5000")
                    .build();
            channel.basicPublish("", QUEUE_NAME, (AMQP.BasicProperties) props, message.getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Sent '" + message + "'");
        }
    }
}