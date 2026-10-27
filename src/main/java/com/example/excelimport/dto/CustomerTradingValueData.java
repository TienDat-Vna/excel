package com.example.excelimport.dto;

import java.math.BigDecimal;

public record CustomerTradingValueData(
        Long id,
        Long customerId,
        String branchId,
        String pccId,
        BigDecimal totalTradingValue
) {
}
