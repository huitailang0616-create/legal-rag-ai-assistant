package com.ailegal.dto;

import java.util.List;

/**
 * 通用分页响应（模块6）。
 * 仅暴露前端需要的字段，避免透出 MyBatis-Plus Page 内部结构。
 */
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long page;
    private long size;

    public PageResult(List<T> records, long total, long page, long size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size);
    }

    public List<T> getRecords() { return records; }
    public void setRecords(List<T> records) { this.records = records; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getPage() { return page; }
    public void setPage(long page) { this.page = page; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
}
