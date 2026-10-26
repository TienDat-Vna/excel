package com.example.excelimport.exception;

import com.example.excelimport.dto.response.ExcelDataResponse;
import com.example.excelimport.constant.FileConstant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
@Slf4j
public class APIExceptionHandler {

    @ExceptionHandler(APIException.class)
    ResponseEntity<ExcelDataResponse> handleAPIException(APIException exception) {
        ErrorCodeDetail errorCodeDetail = exception.getErrorCodeDetail();
        return createErrorResponse(errorCodeDetail);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ExcelDataResponse> handleMissingFileException(MissingServletRequestPartException exception) {
        log.warn("[EXCEL_SCAN][UPLOAD] errorCode=INVALID_EXCEL reason=MISSING_FILE");
        return createErrorResponse(ErrorCodeDetail.INVALID_EXCEL);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ExcelDataResponse> handleMaxUploadSizeException(MaxUploadSizeExceededException exception) {
        log.warn("[EXCEL_SCAN][UPLOAD] errorCode=FILE_TOO_LARGE reason=MULTIPART_LIMIT_EXCEEDED maxFileSizeBytes={}",
                FileConstant.MAX_FILE_SIZE_BYTES);
        return createErrorResponse(ErrorCodeDetail.FILE_TOO_LARGE);
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ExcelDataResponse> handleMultipartException(MultipartException exception) {
        log.warn("[EXCEL_SCAN][UPLOAD] errorCode=INVALID_EXCEL reason=INVALID_MULTIPART");
        return createErrorResponse(ErrorCodeDetail.INVALID_EXCEL);
    }

    private ResponseEntity<ExcelDataResponse> createErrorResponse(ErrorCodeDetail errorCodeDetail) {
        HttpStatus status = errorCodeDetail.getStatus();
        String errorCode = errorCodeDetail.getErrorCode();
        String message = errorCodeDetail.getMessage();
        ExcelDataResponse errorResponse = new ExcelDataResponse(
                errorCode,
                message,
                null,
                List.of()
        );

        return new ResponseEntity<>(errorResponse, status);
    }
}
