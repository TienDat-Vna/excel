package com.example.excelimport.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

@Getter
public class APIException extends RuntimeException {
    private final int code = -1;
    private final HttpStatus status;
    private final String errorCode;
    private final String messageDetail;

    public APIException(ErrorCodeDetail errorCodeDetail) {
        this(errorCodeDetail, null);
    }

    public APIException(ErrorCodeDetail errorCodeDetail, String messageDetail) {
        super(errorCodeDetail.getMessage());
        this.status = errorCodeDetail.getStatus();
        this.errorCode = errorCodeDetail.getErrorCode();
        this.messageDetail = StringUtils.hasText(messageDetail)
                ? messageDetail : errorCodeDetail.getMessageDetail();
    }
}
