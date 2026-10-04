package com.tms.framework.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 投递器装配期的自检（纯单元测试，不需要 Spring 容器）。
 *
 * <p>这两条校验针对的是同一类问题：<b>配置写错了，系统照常启动，只是行为不对</b>。
 * 这类错误在启动时不报就会一直藏着——
 * <ul>
 *   <li>退避序列比 {@code max-retries} 短：重试到一半没有等待时长可用（取下标越界）；</li>
 *   <li>比它长：最后几档退避永远轮不到，实际重试节奏和 [06] §8 写的不是一回事；</li>
 *   <li>同一个目标系统注册两个投递实现：随机挑一个用，投递结果不可预期。</li>
 * </ul>
 * 三者都不会让编译或测试失败，只会让线上的重试行为与文档不符。所以在这里钉死，
 * 让它们在启动那一刻就炸出来。
 */
class OutboxDispatcherConfigTest {

    private static OutboxProperties properties(int maxRetries, List<Duration> backoff) {
        OutboxProperties properties = new OutboxProperties();
        properties.setMaxRetries(maxRetries);
        properties.setBackoff(backoff);
        return properties;
    }

    private static OutboxDispatcher dispatcher(OutboxProperties properties, EventPublisher... publishers) {
        return new OutboxDispatcher(null, null, properties, List.of(publishers));
    }

    @Test
    @DisplayName("退避序列长度等于最大重试次数时可以正常装配，且默认值本身自洽")
    void matchingLengthPassesAndDefaultsAreSane() {
        OutboxProperties defaults = new OutboxProperties();
        assertThatCode(() -> dispatcher(defaults).validateConfiguration())
                .as("出厂的默认配置自己就不满足校验：应用根本起不来")
                .doesNotThrowAnyException();

        assertThat(defaults.getBackoff())
                .as("默认退避序列应为 [06] §8 的 1m/5m/30m/2h/6h")
                .containsExactly(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
                        Duration.ofHours(2), Duration.ofHours(6));
        assertThat(defaults.getMaxRetries()).isEqualTo(defaults.getBackoff().size());
    }

    @Test
    @DisplayName("退避序列比 max-retries 短：启动即失败，而不是等到第 4 次重试时下标越界")
    void shorterBackoffFailsAtStartup() {
        OutboxProperties properties = properties(5,
                List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30)));

        assertThatThrownBy(() -> dispatcher(properties).validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("退避序列长度")
                .hasMessageContaining("3")
                .hasMessageContaining("5");
    }

    @Test
    @DisplayName("退避序列比 max-retries 长：启动即失败，多余的那几档永远用不上")
    void longerBackoffFailsAtStartup() {
        OutboxProperties properties = properties(2,
                List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30)));

        assertThatThrownBy(() -> dispatcher(properties).validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("退避序列长度");
    }

    @Test
    @DisplayName("backoff 整段没配：启动即失败，且报错里点明是 null 而非长度 0")
    void nullBackoffFailsAtStartupWithClearMessage() {
        OutboxProperties properties = properties(5, null);

        assertThatThrownBy(() -> dispatcher(properties).validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null")
                .as("报成'长度 0'会让人去查序列内容，而真正的问题是这一段压根没绑定上")
                .hasMessageContaining("退避序列长度(null)");
    }

    @Test
    @DisplayName("同一目标系统注册两个投递实现：构造时就失败，不等到运行时随机挑一个")
    void duplicateTargetSystemIsRejectedAtConstruction() {
        EventPublisher first = publisher("WMS");
        EventPublisher second = publisher("WMS");

        assertThatThrownBy(() -> dispatcher(new OutboxProperties(), first, second))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WMS")
                .hasMessageContaining("多个投递实现");
    }

    @Test
    @DisplayName("目标系统名大小写与空白不同不算两个实现——否则 WMS/wms 会绕过重复校验")
    void duplicateDetectionIgnoresCaseAndPadding() {
        EventPublisher lower = publisher("wms");
        EventPublisher padded = publisher(" WMS ");

        assertThatThrownBy(() -> dispatcher(new OutboxProperties(), lower, padded))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("多个投递实现");
    }

    private static EventPublisher publisher(String targetSystem) {
        return new EventPublisher() {
            @Override
            public String targetSystem() {
                return targetSystem;
            }

            @Override
            public void publish(EventEnvelope envelope) {
                // 本测试只关心装配，不关心投递
            }
        };
    }
}
