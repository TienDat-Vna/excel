package com.example.excelimport.service;

import com.example.excelimport.dto.CustomerTradingValueData;
import com.example.excelimport.exception.APIException;
import com.example.excelimport.exception.ErrorCodeDetail;
import com.example.excelimport.repository.CustomerTradingValueRepository;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CustomerTradingValueService {

    private final Logger logger = LoggerFactory.getLogger(CustomerTradingValueService.class);
    private final List<String> tradingValueHeaders = List.of("id", "customer_id", "branch_id", "pcc_id", "total_trading_value");

    @Autowired
    private CustomerTradingValueRepository customerTradingValueRepository;

    /** Xuất tất cả bản ghi */
    public Path exportAll() {

        Path exportFile = null;
        boolean completed = false;

        try {

            exportFile = Files.createTempFile("customer-trading-values-", ".xlsx");

            // Sử dụng SXSSFWorkbook để xuất dữ liệu
            try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
                workbook.setCompressTempFiles(true);
                createSheet(workbook);

                // Gọi repository đọc dữ liệu và ghi vào workbook
                customerTradingValueRepository.readAll(rowData -> writeDataRow(workbook, rowData));

                try (OutputStream outputStream = Files.newOutputStream(exportFile)) {
                    workbook.write(outputStream);
                }
            }
            completed = true;
            return exportFile;

        } catch (IOException | UncheckedIOException exception) {
            throw new APIException(ErrorCodeDetail.EXCEL_EXPORT_FAILED);
        } catch (IllegalStateException exception) {
            // Nếu client ngắt kết nối, ném Exception
            if (exception.getCause() instanceof IOException) {
                throw new APIException(ErrorCodeDetail.EXCEL_EXPORT_FAILED);
            }
            throw exception;
        } finally {
            // Xóa file cả khi xuất lỗi hoặc client ngắt kết nối.
            if (!completed && exportFile != null) {
                deleteExport(exportFile);
            }
        }
    }

    /** Xóa file */
    public void deleteExport(Path exportFile) {
        try {
            Files.deleteIfExists(exportFile);
        } catch (IOException exception) {
            logger.error("[TRADING_VALUE][CLEANUP] errorCode=EXPORT_CLEANUP_FAILED");
        }
    }

    // Tạo sheet mới với header
    private Sheet createSheet(SXSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("TradingValues_" + (workbook.getNumberOfSheets() + 1));
        Row headerRow = sheet.createRow(0);

        for (int columnIndex = 0; columnIndex < tradingValueHeaders.size(); columnIndex++) {
            headerRow.createCell(columnIndex).setCellValue(tradingValueHeaders.get(columnIndex));
            sheet.setColumnWidth(columnIndex, 26 * 256);
        }

        sheet.createFreezePane(0, 1);
        return sheet;
    }

    // Ghi dữ liệu vào sheet cuối cùng
    private void writeDataRow(SXSSFWorkbook workbook, CustomerTradingValueData rowData) {
        Sheet sheet = workbook.getSheetAt(workbook.getNumberOfSheets() - 1);
        int rowIndex = sheet.getLastRowNum() + 1;

        // Nếu vượt quá giới hạn số dòng của sheet, tạo sheet mới
        if (rowIndex >= SpreadsheetVersion.EXCEL2007.getMaxRows()) {
            sheet = createSheet(workbook);
            rowIndex = 1;
        }

        Row dataRow = sheet.createRow(rowIndex);
        // Ghi dữ liệu vào các cột
        dataRow.createCell(0).setCellValue(rowData.id().toString());
        dataRow.createCell(1).setCellValue(rowData.customerId().toString());
        dataRow.createCell(2).setCellValue(rowData.branchId());
        dataRow.createCell(3).setCellValue(rowData.pccId());
        dataRow.createCell(4).setCellValue(rowData.totalTradingValue().toPlainString());
    }
}
