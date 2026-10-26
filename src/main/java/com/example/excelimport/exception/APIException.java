package com.example.excelimport.exception;

import lombok.Getter;

@Getter
public class APIException extends RuntimeException {
    private final ErrorCodeDetail errorCodeDetail;

    public APIException(ErrorCodeDetail errorCodeDetail) {
        super(errorCodeDetail.getMessage());
        this.errorCodeDetail = errorCodeDetail;
    }
}
