package com.stockea.config.mensajeria;


import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

// Mensajes de RabbitMQ en JSON, lo usa tanto el que publica (RabbitTemplate) como el que consume(RabbitListener)
@Configuration(proxyBeanMethods = false)
class MensajeriaConfig {
    @Bean
    MessageConverter convertidorJson(JsonMapper jsonMapper){
        return new JacksonJsonMessageConverter(jsonMapper, "com.stockea.ventas.api");
    }
}
