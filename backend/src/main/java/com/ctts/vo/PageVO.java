package com.ctts.vo;

import lombok.Data;

import java.util.List;

/**
 * 分页返回结构：不是表映射，而是"一页数据 + 总条数"的载体。
 *
 * 为什么必须带 total：只返回 list 时，前端算不出总页数，
 * 连"还有没有下一页"都判断不了——只能一直请求到返回空数组为止。
 *
 * 为什么 total 用 long：COUNT(*) 在 MySQL 里就是 BIGINT，
 * 用 int 接会在超大表上溢出（虽然本项目远到不了，但类型应该跟着数据走）。
 */
@Data
public class PageVO<T> {

    /** 当前页数据 */
    private List<T> list;

    /** 满足条件的总条数（不是本页条数） */
    private long total;

    /** 当前页码，从 1 开始 */
    private int page;

    /** 每页条数（已按上限裁剪） */
    private int pageSize;

    /**
     * 静态工厂：省掉"new + 四次 set"。
     * 泛型方法由调用方推断 T，返回 PageVO<Task> 时不需要强制转型。
     */
    public static <T> PageVO<T> of(List<T> list, long total, int page, int pageSize) {
        PageVO<T> vo = new PageVO<>();
        vo.setList(list);
        vo.setTotal(total);
        vo.setPage(page);
        vo.setPageSize(pageSize);
        return vo;
    }
}
