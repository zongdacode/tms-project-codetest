package com.tms.framework.event;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 事件投递配置（[06-api-contracts] §8 的默认值在此固化）。
 *
 * <p>标注 {@code @Component} 而非依赖 {@code @ConfigurationPropertiesScan}：
 * 后者忘加时本类<b>静默不绑定</b>、全部走默认值，配置改了却不生效——这类问题只在线上暴露。
 */
@Component
@ConfigurationProperties(prefix = "tms.event.outbox")
public class OutboxProperties {

    /** 单轮扫描最多取多少条，防止一轮扫出几十万条把内存打满。 */
    private int scanBatchSize = 100;

    /**
     * 最大重试次数。
     *
     * <p><b>口径</b>：{@code maxRetries = 5} 指首次投递失败后<b>再重试 5 次</b>，
     * 因此总投递次数为 6。这个口径与 {@link #backoff} 长度为 5 相配——
     * 若把它理解成"总投递 5 次"，最后一个 6h 退避就永远用不上，与 [06] §8 列出的序列不符。
     */
    private int maxRetries = 5;

    /**
     * 退避序列，第 n 次重试前的等待时长（[06] §8：1min → 5min → 30min → 2h → 6h）。
     * 长度应等于 {@link #maxRetries}；不相等时启动即失败（见 {@code OutboxDispatcher}）。
     */
    private List<Duration> backoff = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            Duration.ofHours(6));

    /** 投递器开关。批 0 无订阅方，可关；批 2 起必须开。 */
    private boolean dispatcherEnabled = true;

    public int getScanBatchSize() {
        return scanBatchSize;
    }

    public void setScanBatchSize(int scanBatchSize) {
        this.scanBatchSize = scanBatchSize;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public List<Duration> getBackoff() {
        return backoff;
    }

    public void setBackoff(List<Duration> backoff) {
        this.backoff = backoff;
    }

    public boolean isDispatcherEnabled() {
        return dispatcherEnabled;
    }

    public void setDispatcherEnabled(boolean dispatcherEnabled) {
        this.dispatcherEnabled = dispatcherEnabled;
    }
}
