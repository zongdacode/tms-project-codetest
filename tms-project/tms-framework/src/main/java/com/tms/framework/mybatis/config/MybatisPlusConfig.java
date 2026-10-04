package com.tms.framework.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件装配。
 *
 * <p><b>拦截器顺序不可随意调整</b>：分页必须排在防全表更新之前，否则分页 SQL 会先被
 * 当成"无 where 的全表更新/删除"拦截掉。
 *
 * <p><b>依赖提醒</b>：这三个 InnerInterceptor 都来自 {@code mybatis-plus-jsqlparser}。
 * Boot 4 下漏加该依赖时，{@code PaginationInnerInterceptor} 会静默不生效——查询不报错，
 * 只是不再分页（Gate 0 发现 §4-③）。所以本类同时是"依赖是否配对"的哨兵：
 * 启动时若类加载失败，直接炸在配置阶段，不会拖到线上。
 */
@Configuration
@MapperScan("com.tms.**.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1) 分页；maxLimit 兜底，防止 pageSize 被外部传成天文数字
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(200L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        // 2) 乐观锁：@Version 字段生效，并发更新靠它而不是靠锁表
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 3) 防全表更新/删除：UPDATE/DELETE 无 where 直接拒绝
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }
}
