package com.example.demo.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductCreateRequest {
    @NotBlank(message = "商品名称不能为空")
    private String name;
    private String description;
    @NotNull @Positive
    private BigDecimal price;
    private String imageUrl;
    @NotNull
    private Long categoryId;
}
