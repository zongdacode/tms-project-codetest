package com.tms.framework.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.tms.common.context.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充。
 *
 * <p>操作人取 {@link UserContext}；定时任务、系统间调用无登录用户时填 {@code system}，
 * 而不是留空——留空会让"这条记录谁改的"彻底失去线索。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    /** 无登录用户时的操作人标识。 */
    public static final String SYSTEM_OPERATOR = "system";

    /**
     * 插入填充。
     *
     * <p>这里用 {@code strictInsertFill}（"字段为空才填"）是<b>有意</b>的，与 {@link #updateFill}
     * 相反：新增实体上这些字段本来就是空的，填充一定发生；而当调用方显式赋了值——
     * 例如历史数据迁移要保留原始 {@code created_at}——就该尊重调用方的值而不是覆盖它。
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperator();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createdBy", String.class, operator);
        strictInsertFill(metaObject, "updatedBy", String.class, operator);
        strictInsertFill(metaObject, "version", Integer.class, 0);
        strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    /**
     * 更新填充。
     *
     * <p><b>这里必须用 {@code setFieldValByName}（无条件覆盖），不能用 {@code strictUpdateFill}</b>——
     * {@code strict*} 系列的语义是"字段为空才填"，而更新的常规流程恰恰是
     * <b>先从库里读出来、改几个业务字段、再整对象存回去</b>：此时实体上的
     * {@code updatedAt}/{@code updatedBy} 是上次落库的值、并不为空，
     * {@code strictUpdateFill} 会直接跳过，UPDATE 语句于是把<b>旧的审计值原样写回</b>。
     *
     * <p>后果是静默的：更新成功、业务字段确实改了，只有 {@code updated_at} 停在插入时刻不动。
     * 靠它做的增量同步、审计追溯、缓存失效判定会全部失灵，而任何功能测试都不会失败。
     * 批 0 就是靠"断言 updated_at 严格变大"才把它暴露出来的（此前那条
     * {@code isAfterOrEqualTo} 的断言被时间精度截断掩盖成了通过）。
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        setFieldValByName("updatedAt", LocalDateTime.now(), metaObject);
        setFieldValByName("updatedBy", currentOperator(), metaObject);
    }

    private String currentOperator() {
        Long userId = UserContext.currentUserId();
        if (userId != null) {
            return String.valueOf(userId);
        }
        String userName = UserContext.currentUserName();
        return userName == null ? SYSTEM_OPERATOR : userName;
    }
}
