package com.intell_BI_backend.mq;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

public class MultiRec {

    private static final String TASK_QUEUE_NAME = "Multi_queue";

    public static void main(String[] argv) throws Exception {
        //创建连接
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        final Connection connection = factory.newConnection();

        for (int i = 0; i < 2; i++) {
            final Channel channel = connection.createChannel();

            channel.queueDeclare(TASK_QUEUE_NAME, true, false, false, null);
            System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

            //控制每个消费者只处理一个消息
            channel.basicQos(1);

            //定义如何处理消息
            int finalI = i;
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {

                String message = new String(delivery.getBody(), "UTF-8");

                try {
                    //处理工作
                    System.out.println("编号"+ finalI +" Received '" + message + "'");
                    //模拟处理时间
                    Thread.sleep(10000);
                    //手动确认消息
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                    //手动拒绝消息(第一个参数是和获取信息，第二个是一次性拒绝到目前这条信息的所有记录，第三个是要不要将信息放回队列--可用于重试)
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                }finally {
                    System.out.println("编号"+ finalI +" Done");
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                }
            };
            //监听消费消息
            channel.basicConsume(TASK_QUEUE_NAME, false, deliverCallback, consumerTag -> {});
        }
    }
}