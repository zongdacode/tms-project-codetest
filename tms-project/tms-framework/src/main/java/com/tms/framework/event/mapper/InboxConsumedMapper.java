package com.tms.framework.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tms.framework.event.entity.InboxConsumed;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InboxConsumedMapper extends BaseMapper<InboxConsumed> {

    /** 按去重键取已有记录；返回 null 表示未消费过。 */
    @Select("""
            SELECT * FROM inbox_consumed
             WHERE event_id = #{eventId}
               AND biz_order_no = #{bizOrderNo}
               AND line_no = #{lineNo}
               AND event_type = #{eventType}
            """)
    InboxConsumed findByDedupKey(@Param("eventId") String eventId,
                                 @Param("bizOrderNo") String bizOrderNo,
                                 @Param("lineNo") Integer lineNo,
                                 @Param("eventType") String eventType);

    /** 回填处理结果摘要（重复事件原样返回它，[06] §6-3）。 */
    @Update("""
            UPDATE inbox_consumed
               SET result_summary = #{resultSummary}
             WHERE event_id = #{eventId}
               AND biz_order_no = #{bizOrderNo}
               AND line_no = #{lineNo}
               AND event_type = #{eventType}
            """)
    int updateSummary(@Param("eventId") String eventId,
                      @Param("bizOrderNo") String bizOrderNo,
                      @Param("lineNo") Integer lineNo,
                      @Param("eventType") String eventType,
                      @Param("resultSummary") String resultSummary);
}
