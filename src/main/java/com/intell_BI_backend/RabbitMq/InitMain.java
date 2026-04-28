package com.intell_BI_backend.RabbitMq;


import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/*
 * @Description: RabbitMq启动类
 */
public class InitMain {

    public static void main(String[] args) throws TimeoutException {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            Connection connection=factory.newConnection();
            Channel channel=connection.createChannel();

            // 创建交换机
            String exchangeName=Constant.EXCHANGE_NAME;
            channel.exchangeDeclare(exchangeName,"direct");

            // 创建队列
            String queueName=Constant.QUEUE_NAME;
            channel.queueDeclare(queueName,true,false,false,null);
            channel.queueBind(queueName,exchangeName,Constant.ROUTING_KEY);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
