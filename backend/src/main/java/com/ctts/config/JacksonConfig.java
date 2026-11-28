package com.ctts.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 全局时间格式：java.time 三种类型的读写格式只在这里定义。
 *
 * <p>为什么不能只写 application.yaml 里的 {@code spring.jackson.date-format}：
 * 那个配置项最终作用在 {@code java.util.Date} 上，对 {@code LocalDateTime} 这类 JSR-310 类型无效。
 * 不额外配置时 java.time 走 ISO-8601 默认格式（{@code 2026-11-28T10:53:04}），
 * 于是同一个响应体里会出现「带 T」和「不带 T」两种写法。
 *
 * <p>这里注册的是 Jackson 的<b>类型级</b>序列化器/反序列化器，优先级低于字段上的
 * {@code @JsonFormat}（字段级覆盖类型级）。所以字段上加注解仍然能单独改格式，
 * 但本项目不再使用——统一格式只有一个来源，改这一处就够。
 */
@Configuration
public class JacksonConfig {

    /** 日期时间统一格式，前后端契约的一部分 */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    /** 纯日期，如项目起止日期 */
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    /** 纯时间 */
    public static final String TIME_PATTERN = "HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer javaTimeFormatCustomizer() {
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        DateTimeFormatter date = DateTimeFormatter.ofPattern(DATE_PATTERN);
        DateTimeFormatter time = DateTimeFormatter.ofPattern(TIME_PATTERN);

        return builder -> {
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(dateTime));
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(dateTime));

            builder.serializerByType(LocalDate.class, new LocalDateSerializer(date));
            builder.deserializerByType(LocalDate.class, new LocalDateDeserializer(date));

            builder.serializerByType(LocalTime.class, new LocalTimeSerializer(time));
            builder.deserializerByType(LocalTime.class, new LocalTimeDeserializer(time));
        };
    }
}
