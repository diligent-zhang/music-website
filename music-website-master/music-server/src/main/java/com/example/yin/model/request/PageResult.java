package com.example.yin.model.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult {
    /** 当前页数据 */
    private List<?> records;
    /** 总条数 */
    private long total;
    /** 当前页码 */
    private int page;
    /** 每页大小 */
    private int pageSize;
}