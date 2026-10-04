package com.tms.common.page;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 分页查询入参基类。
 *
 * <p>页大小上限强制存在（[06-api-contracts] §5：分页强制、不提供无上限查询），
 * 防止列表页被拉成全表扫描。
 */
public class PageQuery {

    public static final int MAX_PAGE_SIZE = 200;
    public static final int DEFAULT_PAGE_SIZE = 20;

    @Min(value = 1, message = "页码从 1 开始")
    private int pageNum = 1;

    @Min(value = 1, message = "页大小至少为 1")
    @Max(value = MAX_PAGE_SIZE, message = "页大小不得超过 " + MAX_PAGE_SIZE)
    private int pageSize = DEFAULT_PAGE_SIZE;

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
