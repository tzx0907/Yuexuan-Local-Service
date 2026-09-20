package com.sky.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/** RabbitMQ 拓扑与消费确认策略。 */
@Configuration
public class RabbitMqConfig {
    public static final String ORDER_EVENT_EXCHANGE = "yuexuan.order.event.exchange";// 业务主题交换机
    public static final String ORDER_PAID_QUEUE = "yuexuan.order.paid.queue";// 订单支付队列
    public static final String ORDER_PAID_ROUTING_KEY = "order.paid";// 订单支付路由键
    public static final String ORDER_DLX = "yuexuan.order.dlx";// 死信交换机
    public static final String ORDER_PAID_DLQ = "yuexuan.order.paid.dlq";// 死信队列
    public static final String ORDER_PAID_DLQ_ROUTING_KEY = "order.paid.dead";// 死信路由键
    public static final String ORDER_CLOSE_DELAY_QUEUE = "yuexuan.order.close.delay.queue";// 延迟队列
    public static final String ORDER_CLOSE_QUEUE = "yuexuan.order.close.queue";// 关闭队列
    public static final String ORDER_CLOSE_DELAY_ROUTING_KEY = "order.close.delay";// 延迟路由键
    public static final String ORDER_CLOSE_ROUTING_KEY = "order.close";// 关闭路由键
    public static final String ORDER_CLOSE_DLQ = "yuexuan.order.close.dlq";// 死信队列
    public static final String ORDER_CLOSE_DLQ_ROUTING_KEY = "order.close.dead";// 死信路由键
    public static final int ORDER_CLOSE_DELAY_MILLIS = 15 * 60 * 1000;

    //交换机
    @Bean
    public TopicExchange orderEventExchange() {
        return new TopicExchange(ORDER_EVENT_EXCHANGE, true, false);
    }

    //死信交换机
    @Bean
    public DirectExchange orderDeadLetterExchange() {
        return new DirectExchange(ORDER_DLX, true, false);
    }

    //队列
    @Bean
    public Queue orderPaidQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", ORDER_DLX);
        arguments.put("x-dead-letter-routing-key", ORDER_PAID_DLQ_ROUTING_KEY);
        return new Queue(ORDER_PAID_QUEUE, true, false, false, arguments);
    }

    //死信队列
    @Bean
    public Queue orderPaidDeadLetterQueue() {
        return new Queue(ORDER_PAID_DLQ, true);
    }

    //队列绑定交换机
    @Bean
    public Binding orderPaidBinding() {
        return BindingBuilder.bind(orderPaidQueue()).to(orderEventExchange()).with(ORDER_PAID_ROUTING_KEY);
    }
    //死信队列绑定死信交换机

    @Bean
    public Binding orderPaidDeadLetterBinding() {
        return BindingBuilder.bind(orderPaidDeadLetterQueue()).to(orderDeadLetterExchange())
                .with(ORDER_PAID_DLQ_ROUTING_KEY);
    }

    /** 延迟队列不设置消费者；消息存活 15 分钟后成为死信并转发到真正的关闭队列。 */
    @Bean
    public Queue orderCloseDelayQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-message-ttl", ORDER_CLOSE_DELAY_MILLIS);
        arguments.put("x-dead-letter-exchange", ORDER_EVENT_EXCHANGE);
        arguments.put("x-dead-letter-routing-key", ORDER_CLOSE_ROUTING_KEY);
        return new Queue(ORDER_CLOSE_DELAY_QUEUE, true, false, false, arguments);
    }

    @Bean
    public Queue orderCloseQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", ORDER_DLX);
        arguments.put("x-dead-letter-routing-key", ORDER_CLOSE_DLQ_ROUTING_KEY);
        return new Queue(ORDER_CLOSE_QUEUE, true, false, false, arguments);
    }

    @Bean
    public Queue orderCloseDeadLetterQueue() {
        return new Queue(ORDER_CLOSE_DLQ, true);
    }

    @Bean
    public Binding orderCloseDelayBinding() {
        return BindingBuilder.bind(orderCloseDelayQueue()).to(orderEventExchange())
                .with(ORDER_CLOSE_DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding orderCloseBinding() {
        return BindingBuilder.bind(orderCloseQueue()).to(orderEventExchange()).with(ORDER_CLOSE_ROUTING_KEY);
    }

    @Bean
    public Binding orderCloseDeadLetterBinding() {
        return BindingBuilder.bind(orderCloseDeadLetterQueue()).to(orderDeadLetterExchange())
                .with(ORDER_CLOSE_DLQ_ROUTING_KEY);
    }
    //消息转换器

    @Bean
    public MessageConverter rabbitMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        return new Jackson2JsonMessageConverter(objectMapper);
    }
    //消费确认策略

    /**
     * 业务抛异常时在同一次投递内最多尝试三次；仍失败则 reject，RabbitMQ 自动路由至 DLQ。
     * 手动 ACK 保证只有业务完成后才确认该条消息。
     */
    @Bean("orderEventRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory orderEventRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);//设置手动ACK
        factory.setDefaultRequeueRejected(false);//设置默认不重新入队
        // 重试策略拦截器
        MethodInterceptor retryInterceptor = RetryInterceptorBuilder.stateless()
                .maxAttempts(3) // 最大重试次数
                .backOffOptions(500, 2.0, 5000) //退避策略：初始等待500ms，每次*2倍，上限5000ms
                .recoverer(new RejectAndDontRequeueRecoverer()) //失败后进入恢复器
                .build();
        factory.setAdviceChain(retryInterceptor);
        return factory;
    }
}
