package com.example.demo.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SeckillRequest {
    @NotNull(message = "活动ID不能为空")
    private Long activityId;

    @Min(value = 1, message = "购买数量至少为1")
    private Integer quantity = 1;
}
