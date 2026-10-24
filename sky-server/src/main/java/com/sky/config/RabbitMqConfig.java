package com.sky.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/** RabbitMQ 拓扑与消费确认策略。 */
@Configuration
@Slf4j
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

    /** 订单事件消费的最大尝试次数（含首次），ORDER_PAID 与 ORDER_CLOSE 共用。 */
    public static final int ORDER_EVENT_MAX_ATTEMPTS = 3;
    public static final long ORDER_EVENT_RETRY_INITIAL_MILLIS = 500L;
    public static final double ORDER_EVENT_RETRY_MULTIPLIER = 2.0;
    public static final long ORDER_EVENT_RETRY_MAX_MILLIS = 5000L;

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
     * 消费确认策略：手动 ACK，业务抛异常时在同一投递内最多尝试 {@link #ORDER_EVENT_MAX_ATTEMPTS} 次，
     * 仍失败则 reject，由 RabbitMQ 路由至 DLQ。
     *
     * <p>重试与死信策略只在这里定义一次。消费者禁止再自建重试循环，
     * 否则会出现两套不联动的次数配置，且容器无法感知失败。</p>
     */
    @Bean("orderEventRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory orderEventRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);//设置手动ACK
        factory.setDefaultRequeueRejected(false);//设置默认不重新入队
        // 重试策略拦截器。手动 ACK 模式下，Spring 2.4 的默认
        // RejectAndDontRequeueRecoverer 不会主动拒绝尚未 ACK 的消息，
        // 可能使消息永久停留在 Unacked。这里显式标记 rejectManual=true，
        // 使重试耗尽后容器执行 basicNack(requeue=false)，由 RabbitMQ 路由至 DLQ。
        MethodInterceptor retryInterceptor = RetryInterceptorBuilder.stateless()
                .maxAttempts(ORDER_EVENT_MAX_ATTEMPTS) // 最大重试次数
                .backOffOptions(ORDER_EVENT_RETRY_INITIAL_MILLIS,
                        ORDER_EVENT_RETRY_MULTIPLIER,
                        ORDER_EVENT_RETRY_MAX_MILLIS) //退避策略：初始等待500ms，每次*2倍，上限5000ms
                .recoverer((message, cause) -> {
                    log.error("订单事件消费 {} 次均失败，NACK 后进入死信队列 queue={}, routingKey={}",
                            ORDER_EVENT_MAX_ATTEMPTS,
                            message.getMessageProperties().getConsumerQueue(),
                            message.getMessageProperties().getReceivedRoutingKey(), cause);
                    throw new ListenerExecutionFailedException(
                            "订单事件消费重试耗尽，拒绝消息并路由至死信队列",
                            new AmqpRejectAndDontRequeueException(
                                    "订单事件消费失败，拒绝且不重新入队", true, cause),
                            message);
                })
                .build();
        factory.setAdviceChain(retryInterceptor);
        return factory;
    }
}
