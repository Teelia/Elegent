package com.datalabeling.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果封装
 *
 * @param <T> 数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据列表
     */
    private List<T> items;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer page;

    /**
     * 每页大小
     */
    private Integer size;

    /**
     * 总页数
     */
    private Integer pages;

    /**
     * 是否有下一页
     */
    private Boolean hasNext;

    /**
     * 是否有上一页
     */
    private Boolean hasPrevious;

    /**
     * 从Spring Data Page对象构建分页结果
     */
    public static <T> PageResult<T> fromPage(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setItems(page.getContent());
        result.setTotal(page.getTotalElements());
        result.setPage(page.getNumber() + 1); // Spring Data从0开始，转换为从1开始
        result.setSize(page.getSize());
        result.setPages(page.getTotalPages());
        result.setHasNext(page.hasNext());
        result.setHasPrevious(page.hasPrevious());
        return result;
    }

    /**
     * 手动构建分页结果
     */
    public static <T> PageResult<T> of(List<T> items, Long total, Integer page, Integer size) {
        PageResult<T> result = new PageResult<>();
        result.setItems(items);
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        result.setPages((int) Math.ceil((double) total / size));
        result.setHasNext(page < result.getPages());
        result.setHasPrevious(page > 1);
        return result;
    }
}
