package com.tms.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TMS 启动入口。
 *
 * <p>{@code scanBasePackages = "com.tms"}：各业务模块的包名不在本类所在包之下，
 * 默认扫描（从 {@code com.tms.admin} 起）扫不到任何东西——框架组件、Mapper、业务 Bean
 * 会全部缺失，而 Spring 不会因此报错，只是启动后接口 404。
 * 所以这个基包路径是必须显式声明的，不是可选项。
 *
 * <p>本模块只做"装配"，不写业务：所有 Bean 来自 tms-framework / tms-core / tms-plan。
 * 往这里加业务代码会让它从启动器变成上帝模块，以后无处安放。
 */
@SpringBootApplication(scanBasePackages = "com.tms")
public class TmsAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(TmsAdminApplication.class, args);
    }
}
