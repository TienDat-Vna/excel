package com.example.excelimport.controller;

import com.example.excelimport.exception.APIException;
import com.example.excelimport.exception.APIExceptionHandler;
import com.example.excelimport.exception.ErrorCodeDetail;
import com.example.excelimport.service.CustomerTradingValueService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CustomerTradingValueControllerTest {

    @Mock
    private CustomerTradingValueService service;

    @InjectMocks
    private CustomerTradingValueController controller;

    @TempDir
    Path temporaryDirectory;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new APIExceptionHandler())
                .build();
    }

    @Test
    void downloadsFileAndDeletesTemporaryFile() throws Exception {
        byte[] content = new byte[]{1, 2, 3};
        Path file = Files.write(temporaryDirectory.resolve("export.xlsx"), content);
        when(service.exportCustomerTradingValues()).thenReturn(file);
        doCallRealMethod().when(service).deleteExportFile(any());

        mockMvc.perform(get("/api/customer-trading-values/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"customer-trading-values.xlsx\""))
                .andExpect(header().longValue("Content-Length", content.length))
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(content));

        assertFalse(Files.exists(file));
    }

    @Test
    void mapsConnectionAndTransactionFailuresTo503() throws Exception {
        when(service.exportCustomerTradingValues()).thenThrow(
                new CannotGetJdbcConnectionException("internal connection detail"),
                new CannotCreateTransactionException("internal transaction detail")
        );

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(get("/api/customer-trading-values/export"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("DATABASE_UNAVAILABLE"))
                    .andExpect(jsonPath("$.messageDetail").value("Không kết nối được cơ sở dữ liệu."))
                    .andExpect(header().doesNotExist("Content-Disposition"));
        }
    }

    @Test
    void mapsQueryFailureTo500() throws Exception {
        when(service.exportCustomerTradingValues()).thenThrow(
                new DataAccessResourceFailureException("internal SQL detail")
        );

        mockMvc.perform(get("/api/customer-trading-values/export"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("DATABASE_QUERY_FAILED"));
    }

    @Test
    void mapsExportFailureTo500() throws Exception {
        when(service.exportCustomerTradingValues()).thenThrow(new APIException(ErrorCodeDetail.EXCEL_EXPORT_FAILED));

        mockMvc.perform(get("/api/customer-trading-values/export"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("EXCEL_EXPORT_FAILED"));
    }

    @Test
    void deletesFileWhenClientDisconnects() throws Exception {
        Path file = Files.write(temporaryDirectory.resolve("export.xlsx"), new byte[]{1});
        when(service.exportCustomerTradingValues()).thenReturn(file);
        doCallRealMethod().when(service).deleteExportFile(any());
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream output = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(output);
        doThrow(new IOException("client disconnected")).when(output).write(any(byte[].class), anyInt(), anyInt());
        when(response.isCommitted()).thenReturn(true);

        controller.exportCustomerTradingValues(response);

        assertFalse(Files.exists(file));
        verify(response, never()).reset();
    }

    @Test
    void resetsUncommittedFileResponseBeforeReturningJsonError() throws Exception {
        Path file = Files.write(temporaryDirectory.resolve("export.xlsx"), new byte[]{1});
        when(service.exportCustomerTradingValues()).thenReturn(file);
        doCallRealMethod().when(service).deleteExportFile(any());
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenThrow(new IOException("cannot open stream"));

        APIException failure = assertThrows(APIException.class, () -> controller.exportCustomerTradingValues(response));

        assertEquals("EXCEL_EXPORT_FAILED", failure.getErrorCode());
        assertFalse(Files.exists(file));
        verify(response).reset();
    }
}
