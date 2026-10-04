package com.tms.arch;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tms.plan.entity.OrderPlan;
import com.tms.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 红线 [10] R-2：计划表字段白名单。
 *
 * <p>断言"实体实际会映射出的列"与"文档 {@code [05-data-model] §2} 列出的字段"完全一致。
 * 方向是双向的：
 * <ul>
 *   <li>实体多一列 → 失败。这就是 R-2 要防的"顺手加个字段"；</li>
 *   <li>实体少一列 → 也失败。少一列通常意味着有人删了字段但没改文档，
 *       或者给字段加了 {@code @TableField(exist = false)} 让它悄悄不落库。</li>
 * </ul>
 *
 * <p><b>本测试失败时怎么办</b>：不要改这个白名单来让它变绿。先到 [10] §5 走变更流程
 * （架构评审 → 修订/新增 ADR → 同步改 [05-data-model] → 再改这里）。
 * 把白名单当"当前实体长什么样"的镜像，这条红线就废了。
 *
 * <p>白名单用 {@link TableInfo} 而不是反射读注解：TableInfo 是 MyBatis-Plus
 * 实际用来生成 SQL 的东西，反射读注解则是在复刻 MP 的推导规则——
 * 复刻一旦与真实实现有出入，就会出现"测试说没有这列、SQL 里却有"。
 */
class R2PlanTableFieldWhitelistTest extends IntegrationTestBase {

    /**
     * 抄自 [05-data-model] §2「计划表 tms_order_plan」字段清单，共 26 列。
     * 顺序按文档，便于逐行比对。
     */
    private static final Set<String> DOCUMENTED_COLUMNS = Set.of(
            "id",                     // 主键
            "biz_order_no",
            "line_no",
            "source_system",
            "source_doc_no",
            "order_type",
            "ship_from_code",
            "ship_to_code",
            "plan_qty",
            "dispatch_status",
            "dispatched_at",
            "outbound_status",
            "outbound_completed_at",
            "inbound_status",
            "inbound_completed_at",
            "signed_status",
            "signed_completed_at",
            "cancel_flag",
            "cancelled_at",
            "cancel_reason",
            "reverse_flag",
            "reversed_at",
            "last_event_id",
            "version",
            "created_at",
            "updated_at");

    @Test
    @DisplayName("R-2：计划表映射列 == 文档字段清单（多一列或少一列都算违规）")
    void planTableColumnsMustMatchDocumentedWhitelist() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(OrderPlan.class);
        assertThat(tableInfo)
                .as("未取到 OrderPlan 的 TableInfo：说明 Mapper 没被扫描到，本测试会失去意义")
                .isNotNull();

        Set<String> actualColumns = new HashSet<>();
        actualColumns.add(tableInfo.getKeyColumn());
        for (TableFieldInfo field : tableInfo.getFieldList()) {
            actualColumns.add(field.getColumn());
        }

        assertThat(actualColumns)
                .as("计划表列与 [05-data-model] §2 不一致。加字段前先走 [10] §5 变更流程，"
                        + "不要直接改本测试的白名单")
                .containsExactlyInAnyOrderElementsOf(DOCUMENTED_COLUMNS);
    }

    @Test
    @DisplayName("白名单规模锁：文档清单本身被悄悄删减时要失败")
    void documentedWhitelistSizeMustNotSilentlyShrink() {
        assertThat(DOCUMENTED_COLUMNS)
                .as("白名单条目数与 [05-data-model] §2 不符。若确实改了文档，请同步更新本常量")
                .hasSize(26);
    }
}
