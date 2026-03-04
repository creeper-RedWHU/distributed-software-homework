package com.example.demo.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillResultVO {
    private String orderNo;
    private String status; // QUEUING, SUCCESS, FAILED
}
