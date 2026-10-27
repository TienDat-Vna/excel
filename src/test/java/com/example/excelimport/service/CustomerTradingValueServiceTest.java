package com.example.excelimport.service;

import com.example.excelimport.dto.CustomerTradingValueData;
import com.example.excelimport.exception.APIException;
import com.example.excelimport.repository.CustomerTradingValueRepository;
import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomerTradingValueServiceTest {

    @Mock
    private CustomerTradingValueRepository repository;

    @InjectMocks
    private CustomerTradingValueService service;

    @Test
    void exportsAllRowsIncludingDuplicatesBeyondWorkbookWindow() throws Exception {
        doAnswer(invocation -> {
            Consumer<CustomerTradingValueData> consumer = invocation.getArgument(0);
            for (long id = 1; id <= 250; id++) {
                consumer.accept(new CustomerTradingValueData(
                        id, 9007199254740993L, "0009", "0103",
                        new BigDecimal("123456789012345678.90")
                ));
            }
            consumer.accept(new CustomerTradingValueData(
                    251L, 1002L, "0002", "=1+1", new BigDecimal("0.00")
            ));
            return null;
        }).when(repository).readAllTradingValues(any());

        Path exportFile = service.exportCustomerTradingValues();
        try {
            try (InputStream input = Files.newInputStream(exportFile);
                 XSSFWorkbook workbook = new XSSFWorkbook(input)) {
                var sheet = workbook.getSheetAt(0);
                assertEquals(252, sheet.getPhysicalNumberOfRows());
                assertEquals(5, sheet.getRow(0).getLastCellNum());
                assertEquals("total_trading_value", sheet.getRow(0).getCell(4).getStringCellValue());
                assertEquals("9007199254740993", sheet.getRow(1).getCell(1).getStringCellValue());
                assertEquals("0009", sheet.getRow(1).getCell(2).getStringCellValue());
                assertEquals("0103", sheet.getRow(1).getCell(3).getStringCellValue());
                assertEquals("123456789012345678.90", sheet.getRow(250).getCell(4).getStringCellValue());
                assertEquals("250", sheet.getRow(250).getCell(0).getStringCellValue());
                assertEquals("0.00", sheet.getRow(251).getCell(4).getStringCellValue());
                assertEquals(CellType.STRING, sheet.getRow(251).getCell(3).getCellType());
                assertEquals("=1+1", sheet.getRow(251).getCell(3).getStringCellValue());
            }
        } finally {
            service.deleteExportFile(exportFile);
        }
        assertFalse(Files.exists(exportFile));
    }

    @Test
    void exportsHeaderWhenDatabaseIsEmpty() throws Exception {
        Path exportFile = service.exportCustomerTradingValues();
        try {
            try (InputStream input = Files.newInputStream(exportFile);
                 XSSFWorkbook workbook = new XSSFWorkbook(input)) {
                assertEquals(1, workbook.getNumberOfSheets());
                assertEquals(1, workbook.getSheetAt(0).getPhysicalNumberOfRows());
                assertEquals("id", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            }
        } finally {
            service.deleteExportFile(exportFile);
        }
    }

    @Test
    void preservesDatabaseFailureAndDeletesIncompleteExport() {
        var failure = new DataAccessResourceFailureException("query failed");
        doThrow(failure).when(repository).readAllTradingValues(any());
        CustomerTradingValueService observedService = spy(service);

        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                observedService::exportCustomerTradingValues));

        ArgumentCaptor<Path> file = ArgumentCaptor.forClass(Path.class);
        verify(observedService).deleteExportFile(file.capture());
        assertFalse(Files.exists(file.getValue()));
    }

    @Test
    void mapsWorkbookIoFailureAndDeletesIncompleteExport() {
        doThrow(new UncheckedIOException(new IOException("write failed")))
                .when(repository).readAllTradingValues(any());
        CustomerTradingValueService observedService = spy(service);

        APIException failure = assertThrows(APIException.class, observedService::exportCustomerTradingValues);
        assertEquals("EXCEL_EXPORT_FAILED", failure.getErrorCode());

        ArgumentCaptor<Path> file = ArgumentCaptor.forClass(Path.class);
        verify(observedService).deleteExportFile(file.capture());
        assertFalse(Files.exists(file.getValue()));
    }

    @Test
    void mapsSxssfFlushFailureAndDeletesIncompleteExport() {
        doThrow(new IllegalStateException(new IOException("flush failed")))
                .when(repository).readAllTradingValues(any());
        CustomerTradingValueService observedService = spy(service);

        APIException failure = assertThrows(APIException.class, observedService::exportCustomerTradingValues);
        assertEquals("EXCEL_EXPORT_FAILED", failure.getErrorCode());

        ArgumentCaptor<Path> file = ArgumentCaptor.forClass(Path.class);
        verify(observedService).deleteExportFile(file.capture());
        assertFalse(Files.exists(file.getValue()));
    }
}
