package com.example.excelimport.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class APIExceptionTest {

    @Test
    void usesEnumDefaultsWhenDetailIsMissingOrBlank() {
        for (ErrorCodeDetail detail : ErrorCodeDetail.values()) {
            assertDefaults(detail, new APIException(detail));
            assertDefaults(detail, new APIException(detail, " \t"));
        }
    }

    @Test
    void handlerPreservesCustomDetailAndStatus() {
        APIException exception = new APIException(ErrorCodeDetail.INVALID_EXCEL, "Thiếu header bắt buộc.");
        var response = new APIExceptionHandler().handleAPIException(exception);

        assertEquals(ErrorCodeDetail.INVALID_EXCEL.getStatus(), response.getStatusCode());
        assertEquals(-1, response.getBody().code());
        assertEquals("INVALID_EXCEL", response.getBody().errorCode());
        assertEquals("INVALID_EXCEL", response.getBody().message());
        assertEquals("Thiếu header bắt buộc.", response.getBody().messageDetail());
    }

    private void assertDefaults(ErrorCodeDetail detail, APIException exception) {
        assertEquals(-1, exception.getCode());
        assertEquals(detail.getStatus(), exception.getStatus());
        assertEquals(detail.getErrorCode(), exception.getErrorCode());
        assertEquals(detail.getMessage(), exception.getMessage());
        assertEquals(detail.getMessageDetail(), exception.getMessageDetail());
    }
}
