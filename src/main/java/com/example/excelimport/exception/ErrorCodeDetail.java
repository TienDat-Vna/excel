package com.example.excelimport.exception;

import lombok.Getter;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCodeDetail {
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "INVALID_FILE_TYPE", "Chỉ nhận file .xlsx."),
    INVALID_EXCEL(HttpStatus.BAD_REQUEST, "INVALID_EXCEL", "INVALID_EXCEL", "File Excel không hợp lệ."),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "FILE_TOO_LARGE", "File tối đa 10 MB.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
    private final String messageDetail;
}
