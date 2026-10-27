package com.example.excelimport.exception;

import com.example.excelimport.dto.response.APIErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class APIExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(APIExceptionHandler.class);

    @ExceptionHandler(APIException.class)
    ResponseEntity<APIErrorResponse> handleAPIException(APIException exception) {
        if (exception.getStatus().is5xxServerError()) {
            LOGGER.error("[TRADING_VALUE][EXPORT] errorCode={}", exception.getErrorCode());
        }
        APIErrorResponse response = new APIErrorResponse(
                exception.getCode(),
                exception.getErrorCode(),
                exception.getMessage(),
                exception.getMessageDetail()
        );
        return ResponseEntity.status(exception.getStatus()).body(response);
    }

    @ExceptionHandler(CannotGetJdbcConnectionException.class)
    ResponseEntity<APIErrorResponse> handleDatabaseConnectionException(CannotGetJdbcConnectionException exception) {
        return handleAPIException(new APIException(ErrorCodeDetail.DATABASE_UNAVAILABLE));
    }

    @ExceptionHandler(CannotCreateTransactionException.class)
    ResponseEntity<APIErrorResponse> handleDatabaseTransactionException(CannotCreateTransactionException exception) {
        return handleAPIException(new APIException(ErrorCodeDetail.DATABASE_UNAVAILABLE));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<APIErrorResponse> handleDatabaseQueryException(DataAccessException exception) {
        return handleAPIException(new APIException(ErrorCodeDetail.DATABASE_QUERY_FAILED));
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
