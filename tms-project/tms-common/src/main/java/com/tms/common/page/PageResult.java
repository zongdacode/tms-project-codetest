package com.tms.common.page;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果。刻意不复用 ORM 的 Page 对象，避免持久层类型泄漏到 API 契约。
 */
public class PageResult<T> implements Serializable {

    private List<T> records = Collections.emptyList();
    private long total;
    private int pageNum;
    private int pageSize;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, int pageNum, int pageSize) {
        this.records = records == null ? Collections.emptyList() : records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> empty(int pageNum, int pageSize) {
        return new PageResult<>(Collections.emptyList(), 0, pageNum, pageSize);
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

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
