package com.example.excelimport.dto.response;

import java.util.List;
import java.util.Map;

public record ExcelDataResponse(
        String code,
        String message,
        List<Map<String, String>> data,
        List<String> errors
) {
}
