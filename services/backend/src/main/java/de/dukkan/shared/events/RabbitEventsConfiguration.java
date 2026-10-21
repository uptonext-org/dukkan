package de.dukkan.shared.events;

import java.net.URI;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.amqp.health.RabbitHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ transport ({@code dukkan.events.transport=rabbitmq}, ADR 0012). Spring Boot's own Rabbit
 * auto-configuration is excluded in {@code application.yml}, so nothing connects to a broker
 * unless this is enabled. Topology: topic exchange {@value #EXCHANGE}; quorum queue {@value #QUEUE}
 * bound to {@code #} and dead-lettered to {@value #DEAD_LETTER_QUEUE} via {@value #DEAD_LETTER_EXCHANGE}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "dukkan.events.transport", havingValue = "rabbitmq")
@EnableRabbit
class RabbitEventsConfiguration {

  static final String EXCHANGE = "dukkan.events";
  static final String QUEUE = "dukkan.backend.events";
  static final String DEAD_LETTER_EXCHANGE = "dukkan.events.dlx";
  static final String DEAD_LETTER_QUEUE = "dukkan.backend.events.dlq";

  @Bean
  CachingConnectionFactory rabbitConnectionFactory(@Value("${dukkan.events.rabbitmq-uri}") String uri) {
    CachingConnectionFactory factory = new CachingConnectionFactory(URI.create(uri));
    factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
    factory.setPublisherReturns(true);
    factory.setConnectionNameStrategy(f -> "dukkan-backend");
    return factory;
  }

  @Bean
  RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMandatory(true);
    return template;
  }

  @Bean
  RabbitAdmin rabbitAdmin(CachingConnectionFactory connectionFactory) {
    return new RabbitAdmin(connectionFactory);
  }

  @Bean
  Declarables dukkanEventTopology() {
    TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
    TopicExchange deadLetters = new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    Queue queue =
        QueueBuilder.durable(QUEUE).quorum().withArguments(Map.of("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)).build();
    Queue deadLetterQueue = QueueBuilder.durable(DEAD_LETTER_QUEUE).quorum().build();
    Binding binding = BindingBuilder.bind(queue).to(exchange).with("#");
    Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue).to(deadLetters).with("#");
    return new Declarables(exchange, deadLetters, queue, deadLetterQueue, binding, deadLetterBinding);
  }

  @Bean
  SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(CachingConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    // A failing handler is not retried in a loop: the message goes to the dead-letter queue.
    factory.setDefaultRequeueRejected(false);
    factory.setConcurrentConsumers(1);
    factory.setMaxConcurrentConsumers(4);
    return factory;
  }

  @Bean
  RabbitHealthIndicator rabbitHealthIndicator(RabbitTemplate rabbitTemplate) {
    return new RabbitHealthIndicator(rabbitTemplate);
  }

  @Bean
  RabbitEventTransport rabbitEventTransport(RabbitTemplate rabbitTemplate) {
    return new RabbitEventTransport(rabbitTemplate);
  }

  @Bean
  RabbitEventListener rabbitEventListener(EventDispatcher dispatcher) {
    return new RabbitEventListener(dispatcher);
  }
}
