package com.example.excelimport.dto;

import java.math.BigDecimal;

public record CustomerData(
        String name,
        String email,
        String phone,
        BigDecimal unitPrice
) {
}
