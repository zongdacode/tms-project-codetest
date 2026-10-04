package com.tms.framework.mybatis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通用实体基类（[05-data-model] §1）。
 *
 * <p>命名说明：采用 {@code created_at / updated_at}（与事件信封的 {@code occurred_at} 同风格），
 * 非 {@code create_time / update_time}；[05-系统架构] §4 原写的后者已按此对齐。
 *
 * <p><b>不适用于事实表</b>：事件记录、节点时间戳只增不改（[05-data-model] §1-4），
 * 不继承本类，也不带 {@code deleted}。业务事实表不删，硬删/软删都禁止。
 */
public abstract class BaseEntity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 最后修改人 / 修改时间。
     *
     * <p><b>改这两个字段的填充逻辑前先读 {@link com.tms.framework.mybatis.handler.AuditMetaObjectHandler}</b>：
     * {@code updateFill} 里必须无条件覆盖，用 {@code strictUpdateFill} 会让
     * "读出实体 → 改业务字段 → 存回"的常规流程把旧时间戳原样写回，
     * 表现为 {@code updated_at} 永远停在插入时刻，而且不报任何错。
     */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 乐观锁版本号。
     *
     * <p>{@code fill = INSERT} 是必需的：MyBatis-Plus 的 {@code strictInsertFill} 只填充
     * 显式声明了 {@code FieldFill} 的字段。不写的话插入时该列为 null，
     * 只能靠建表时的 {@code DEFAULT 0} 兜住——换一张忘了加默认值的表就插不进去，
     * 而且报的是 NOT NULL 约束错误，看不出跟乐观锁有关。
     */
    @Version
    @TableField(value = "version", fill = FieldFill.INSERT)
    private Integer version;

    /** 逻辑删除；事实表不用本字段（见类注释）。同样显式声明 fill，不依赖 DB 默认值。 */
    @TableLogic
    @TableField(value = "deleted", fill = FieldFill.INSERT)
    private Integer deleted;

    /**
     * 多工厂隔离预留字段：字段建、拦截器不启用（[ADR-016]，单工厂起步）。
     * 启用时需要补回归测试，见 [12-execution-plan] §3.2。
     */
    @TableField("tenant_id")
    private Long tenantId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
