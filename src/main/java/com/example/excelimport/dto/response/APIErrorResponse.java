package com.example.excelimport.dto.response;

public record APIErrorResponse(
        int code,
        String errorCode,
        String message,
        String messageDetail
) {
}
