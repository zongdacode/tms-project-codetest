package com.tms.framework.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 打开调度能力——没有它，{@code @Scheduled} 的投递器<b>不报错、不执行</b>，
 * 事件永远停在 PENDING，而系统看上去一切正常。
 *
 * <p>把 {@code @EnableScheduling} 放在框架层而不是启动类：它和投递器是一体的，
 * 分开放就会出现"有人新建了一个启动类、忘了加注解、事件不发"这种只能靠线上发现的故障。
 */
@Configuration
@EnableScheduling
public class EventFrameworkConfig {

    private static final Logger log = LoggerFactory.getLogger(EventFrameworkConfig.class);

    public EventFrameworkConfig() {
        log.info("事件框架已装配：调度已开启，发件箱投递器将按配置周期扫描");
    }
}
