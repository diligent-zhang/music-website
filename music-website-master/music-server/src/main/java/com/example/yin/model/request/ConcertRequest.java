package com.example.yin.model.request;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
public class ConcertRequest {
    private Integer id;
    private String title;
    private Integer singerId;
    private String singerName;
    private String venue;
    private String coverPic;
    private Date showTime;
    private Date saleStartTime;
    private String introduction;
    private Integer status;
    private List<TierRequest> tiers;     // 嵌套票档列表

    @Data
    public static class TierRequest {
        private String tierName;
        private BigDecimal price;
        private Integer totalStock;
    }
}