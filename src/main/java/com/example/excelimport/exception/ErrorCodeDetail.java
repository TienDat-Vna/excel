package com.example.excelimport.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCodeDetail {
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "Chỉ nhận file .xlsx."),
    INVALID_EXCEL(HttpStatus.BAD_REQUEST, "File Excel không hợp lệ."),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "File tối đa 10 MB.");

    private final HttpStatus status;
    private final String message;

    public String getErrorCode() {
        return name();
    }
}
