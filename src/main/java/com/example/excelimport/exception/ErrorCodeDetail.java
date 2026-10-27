package com.example.excelimport.exception;

import lombok.Getter;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCodeDetail {
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "INVALID_FILE_TYPE", "Chỉ nhận file .xlsx."),
    INVALID_EXCEL(HttpStatus.BAD_REQUEST, "INVALID_EXCEL", "INVALID_EXCEL", "File Excel không hợp lệ."),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "FILE_TOO_LARGE", "File tối đa 10 MB."),
    DATABASE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "DATABASE_UNAVAILABLE", "Không kết nối được cơ sở dữ liệu."),
    DATABASE_QUERY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_QUERY_FAILED", "DATABASE_QUERY_FAILED", "Không đọc được dữ liệu giao dịch."),
    EXCEL_EXPORT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EXCEL_EXPORT_FAILED", "EXCEL_EXPORT_FAILED", "Không tạo được file Excel.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
    private final String messageDetail;
}
