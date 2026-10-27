package com.example.excelimport.exception;

import com.example.excelimport.dto.response.APIErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class APIExceptionHandler {

    @ExceptionHandler(APIException.class)
    ResponseEntity<APIErrorResponse> handleAPIException(APIException exception) {
        APIErrorResponse response = new APIErrorResponse(
                exception.getCode(),
                exception.getErrorCode(),
                exception.getMessage(),
                exception.getMessageDetail()
        );
        return ResponseEntity.status(exception.getStatus()).body(response);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<APIErrorResponse> handleMissingFileException(MissingServletRequestPartException exception) {
        return handleAPIException(new APIException(ErrorCodeDetail.INVALID_EXCEL));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<APIErrorResponse> handleMaxUploadSizeException(MaxUploadSizeExceededException exception) {
        return handleAPIException(new APIException(ErrorCodeDetail.FILE_TOO_LARGE));
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<APIErrorResponse> handleMultipartException(MultipartException exception) {
        return handleAPIException(new APIException(ErrorCodeDetail.INVALID_EXCEL));
    }
}
