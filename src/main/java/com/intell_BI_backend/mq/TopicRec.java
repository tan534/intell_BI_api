package com.intell_BI_backend.mq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

public class TopicRec {

    private static final String EXCHANGE_NAME = "topic_Exchange";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();

        Channel channel1 = connection.createChannel();
        channel1.exchangeDeclare(EXCHANGE_NAME, "topic");
        String queueName1="后端组的消息队列";
        channel1.queueDeclare(queueName1, true, false, false, null);
        channel1.queueBind(queueName1, EXCHANGE_NAME, "*.后端.*");

        Channel channel2 = connection.createChannel();
        channel2.exchangeDeclare(EXCHANGE_NAME, "topic");
        String queueName2="前端组的消息队列";
        channel2.queueDeclare(queueName2, true, false, false, null);
        channel2.queueBind(queueName2, EXCHANGE_NAME, "*.前端.*");

        Channel channel3 = connection.createChannel();
        channel3.exchangeDeclare(EXCHANGE_NAME, "topic");
        String queueName3="商务组的消息队列";
        channel3.queueDeclare(queueName3, true, false, false, null);
        channel3.queueBind(queueName3, EXCHANGE_NAME,"*.商务.*");
        channel3.queueBind(queueName3, EXCHANGE_NAME,"*.前端.*");


        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        DeliverCallback deliverCallback1 = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [后端] Received '" +
                    delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
        };
        channel1.basicConsume(queueName1, true, deliverCallback1, consumerTag -> { });

        DeliverCallback deliverCallback2 = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [前端] Received '" +
                    delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
        };
        channel2.basicConsume(queueName2, true, deliverCallback2, consumerTag -> { });

        DeliverCallback deliverCallback3 = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [商务] Received '" +
                    delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
        };
        channel3.basicConsume(queueName3, true, deliverCallback3, consumerTag -> { });
    }
}
