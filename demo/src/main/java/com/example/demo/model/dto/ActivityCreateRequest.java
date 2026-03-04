package com.example.demo.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ActivityCreateRequest {
    @NotBlank(message = "活动名称不能为空")
    private String name;
    @NotNull
    private Long productId;
    @NotNull @Positive
    private BigDecimal seckillPrice;
    @NotNull @Positive
    private Integer totalStock;
    @NotNull
    private LocalDateTime startTime;
    @NotNull
    private LocalDateTime endTime;
    private Integer limitPerUser = 1;
}
