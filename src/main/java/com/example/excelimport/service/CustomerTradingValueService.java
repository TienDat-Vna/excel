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

    /**
     * Tạo file Excel chứa toàn bộ bản ghi giao dịch.
     *
     * @return file tạm hoàn chỉnh; controller xóa sau khi gửi
     * @throws APIException nếu tạo hoặc ghi file thất bại
     */
    public Path exportCustomerTradingValues() {

        Path exportFile = null;
        boolean completed = false;

        try {

            exportFile = Files.createTempFile("customer-trading-values-", ".xlsx");

            // Giữ cửa sổ 100 dòng; các dòng cũ được ghi xuống file tạm của POI.
            try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
                 OutputStream outputStream = Files.newOutputStream(exportFile)) {
                workbook.setCompressTempFiles(true);
                createSheet(workbook);

                // Gọi repository đọc dữ liệu và ghi vào workbook
                customerTradingValueRepository.readAllTradingValues(rowData -> writeDataRow(workbook, rowData));

                workbook.write(outputStream);
            }
            completed = true;
            return exportFile;

        } catch (IOException | UncheckedIOException exception) {
            throw new APIException(ErrorCodeDetail.EXCEL_EXPORT_FAILED);
        } catch (IllegalStateException exception) {
            // SXSSF bọc lỗi I/O khi tự ghi các dòng cũ xuống đĩa.
            if (exception.getCause() instanceof IOException) {
                throw new APIException(ErrorCodeDetail.EXCEL_EXPORT_FAILED);
            }
            throw exception;
        } finally {
            // Chỉ bàn giao file cho controller khi đã tạo và đóng thành công.
            if (!completed && exportFile != null) {
                deleteExportFile(exportFile);
            }
        }
    }

    /**
     * Xóa file tạm, ghi log nếu không xóa được để giữ nguyên kết quả xuất ban đầu.
     *
     * @param exportFile file tạm cần xóa
     */
    public void deleteExportFile(Path exportFile) {
        try {
            Files.deleteIfExists(exportFile);
        } catch (IOException exception) {
            logger.error("[TRADING_VALUE][CLEANUP] errorCode=EXPORT_CLEANUP_FAILED");
        }
    }

    /**
     * Tạo sheet với header cố định, dùng cho sheet đầu và khi sheet cũ đầy.
     *
     * @param workbook workbook đang xuất
     * @return sheet vừa tạo
     */
    private Sheet createSheet(SXSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("TradingValues_" + (workbook.getNumberOfSheets() + 1));
        Row headerRow = sheet.createRow(0);

        for (int columnIndex = 0; columnIndex < tradingValueHeaders.size(); columnIndex++) {
            headerRow.createCell(columnIndex).setCellValue(tradingValueHeaders.get(columnIndex));
            // Độ rộng tương ứng khoảng 26 ký tự, đơn vị của POI là 1/256 ký tự.
            sheet.setColumnWidth(columnIndex, 26 * 256);
        }

        sheet.createFreezePane(0, 1);
        return sheet;
    }

    /**
     * Ghi một bản ghi vào sheet cuối, chuyển sheet khi đạt giới hạn dòng.
     *
     * @param workbook workbook đang xuất
     * @param rowData bản ghi lấy từ DB
     */
    private void writeDataRow(SXSSFWorkbook workbook, CustomerTradingValueData rowData) {
        Sheet sheet = workbook.getSheetAt(workbook.getNumberOfSheets() - 1);
        int rowIndex = sheet.getLastRowNum() + 1;

        // Nếu vượt quá giới hạn số dòng của sheet, tạo sheet mới
        if (rowIndex >= SpreadsheetVersion.EXCEL2007.getMaxRows()) {
            sheet = createSheet(workbook);
            rowIndex = 1;
        }

        Row dataRow = sheet.createRow(rowIndex);
        // Text giữ mã có số 0 đầu, ID dài và precision của tiền.
        dataRow.createCell(0).setCellValue(rowData.id().toString());
        dataRow.createCell(1).setCellValue(rowData.customerId().toString());
        dataRow.createCell(2).setCellValue(rowData.branchId());
        dataRow.createCell(3).setCellValue(rowData.pccId());
        dataRow.createCell(4).setCellValue(rowData.totalTradingValue().toPlainString());
    }
}
