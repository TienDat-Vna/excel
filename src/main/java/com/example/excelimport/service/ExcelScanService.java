package com.example.excelimport.service;

import com.example.excelimport.constant.FileConstant;
import com.example.excelimport.exception.APIException;
import com.example.excelimport.exception.ErrorCodeDetail;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JRuntimeException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class ExcelScanService {

    /**
     * Đọc sheet đầu tiên của file Excel, dùng dòng đầu làm tên trường cho các dòng dữ liệu.
     *
     * @param excelFile file .xlsx được upload, tối đa 10 MB
     * @return danh sách các dòng dữ liệu; mỗi dòng ánh xạ tên cột với giá trị dạng chuỗi
     * @throws APIException nếu file sai định dạng, vượt dung lượng hoặc nội dung không hợp lệ
     */
    public List<Map<String, String>> scanExcel(MultipartFile excelFile) {
        validateFile(excelFile);
        Workbook workbook = openWorkbook(excelFile);
        return readWorkbook(workbook);
    }

    /**
     * Kiểm tra file có nội dung, không vượt dung lượng và có đuôi .xlsx trước khi mở file.
     *
     * @param excelFile file cần kiểm tra
     * @throws APIException nếu file thiếu, rỗng, vượt 10 MB hoặc sai đuôi
     */
    private void validateFile(MultipartFile excelFile) {
        if (excelFile == null) {
            log.warn("[EXCEL_SCAN][VALIDATE_FILE] errorCode=INVALID_EXCEL reason=MISSING_FILE");
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }
        if (excelFile.isEmpty()) {
            log.warn("[EXCEL_SCAN][VALIDATE_FILE] errorCode=INVALID_EXCEL reason=EMPTY_FILE");
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }
        if (excelFile.getSize() > FileConstant.MAX_FILE_SIZE_BYTES) {
            log.warn("[EXCEL_SCAN][VALIDATE_FILE] errorCode=FILE_TOO_LARGE fileSizeBytes={} maxFileSizeBytes={}",
                    excelFile.getSize(), FileConstant.MAX_FILE_SIZE_BYTES);
            throw new APIException(ErrorCodeDetail.FILE_TOO_LARGE);
        }
        String fileName = excelFile.getOriginalFilename();
        if (fileName == null) {
            log.warn("[EXCEL_SCAN][VALIDATE_FILE] errorCode=INVALID_FILE_TYPE reason=MISSING_FILE_NAME");
            throw new APIException(ErrorCodeDetail.INVALID_FILE_TYPE);
        }
        String lowercaseFileName = fileName.toLowerCase(Locale.ROOT);
        if (!lowercaseFileName.endsWith(".xlsx")) {
            log.warn("[EXCEL_SCAN][VALIDATE_FILE] errorCode=INVALID_FILE_TYPE reason=INVALID_EXTENSION");
            throw new APIException(ErrorCodeDetail.INVALID_FILE_TYPE);
        }
    }

    /**
     * Mở workbook .xlsx và ghi log nếu không đọc được file.
     *
     * @param excelFile file đã qua kiểm tra đầu vào
     * @return workbook được chuyển cho readWorkbook quản lý và đóng
     * @throws APIException nếu không mở được workbook
     */
    private Workbook openWorkbook(MultipartFile excelFile) {
        try (InputStream inputStream = excelFile.getInputStream()) {
            return new XSSFWorkbook(inputStream);
        } catch (IOException | POIXMLException | IllegalArgumentException | OpenXML4JRuntimeException exception) {
            log.warn("[EXCEL_SCAN][OPEN_WORKBOOK] errorCode=INVALID_EXCEL fileSizeBytes={} errorType={}",
                    excelFile.getSize(), exception.getClass().getSimpleName());
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }
    }

    /**
     * Đọc sheet đầu tiên và đóng workbook sau khi hoàn tất, kể cả khi có lỗi.
     *
     * @param workbook workbook đã mở
     * @return danh sách các dòng dữ liệu Excel
     * @throws APIException nếu không đọc hoặc đóng được workbook
     */
    private List<Map<String, String>> readWorkbook(Workbook workbook) {
        long startTime = System.nanoTime();
        List<Map<String, String>> records;
        int columnCount;

        try (workbook) {
            if (workbook.getNumberOfSheets() == 0) {
                log.warn("[EXCEL_SCAN][READ_WORKBOOK] errorCode=INVALID_EXCEL reason=MISSING_SHEET");
                throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
            }
            Sheet worksheet = workbook.getSheetAt(0);
            if (worksheet.getNumMergedRegions() > 0) {
                log.warn("[EXCEL_SCAN][READ_WORKBOOK] errorCode=INVALID_EXCEL reason=MERGED_CELLS mergedRegionCount={}",
                        worksheet.getNumMergedRegions());
                throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
            }
            DataFormatter dataFormatter = new DataFormatter(Locale.US);
            dataFormatter.setUseCachedValuesForFormulaCells(true);
            List<String> columnNames = readColumnNames(worksheet, dataFormatter);
            columnCount = columnNames.size();
            records = readDataRows(worksheet, columnNames, dataFormatter);
        } catch (IOException | POIXMLException | IllegalArgumentException | OpenXML4JRuntimeException exception) {
            log.warn("[EXCEL_SCAN][READ_WORKBOOK] errorCode=INVALID_EXCEL errorType={}",
                    exception.getClass().getSimpleName());
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }

        long readDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        log.info("[EXCEL_SCAN][READ_WORKBOOK] result=SUCCESS sheetIndex=0 rowCount={} columnCount={} readDurationMs={}",
                records.size(), columnCount, readDurationMs);
        return records;
    }

    /**
     * Đọc tên trường JSON từ header, kiểm tra tên trống hoặc trùng để tránh mất dữ liệu.
     *
     * @param worksheet sheet đầu tiên của workbook
     * @param dataFormatter bộ chuyển giá trị ô sang chuỗi
     * @return danh sách tên cột theo thứ tự trong Excel
     * @throws APIException nếu header không hợp lệ
     */
    private List<String> readColumnNames(Sheet worksheet, DataFormatter dataFormatter) {
        Row headerRow = worksheet.getRow(0);
        if (headerRow == null) {
            log.warn("[EXCEL_SCAN][READ_HEADER] errorCode=INVALID_EXCEL reason=MISSING_HEADER");
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }

        int columnCount = getHeaderColumnCount(headerRow, dataFormatter);
        if (columnCount == 0) {
            log.warn("[EXCEL_SCAN][READ_HEADER] errorCode=INVALID_EXCEL reason=EMPTY_HEADER");
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }

        List<String> columnNames = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            Cell headerCell = headerRow.getCell(columnIndex);
            String headerValue = dataFormatter.formatCellValue(headerCell);
            String columnName = headerValue.strip();

            if (columnName.isEmpty()) {
                log.warn("[EXCEL_SCAN][READ_HEADER] errorCode=INVALID_EXCEL reason=EMPTY_COLUMN_NAME row=1 column={}",
                        columnIndex + 1);
                throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
            }
            if (columnNames.contains(columnName)) {
                log.warn("[EXCEL_SCAN][READ_HEADER] errorCode=INVALID_EXCEL reason=DUPLICATE_COLUMN_NAME row=1 column={} firstColumn={}",
                        columnIndex + 1, columnNames.indexOf(columnName) + 1);
                throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
            }

            columnNames.add(columnName);
        }
        return columnNames;
    }

    /**
     * Xác định số cột đến ô header cuối cùng có tên, bỏ qua các ô trống ở cuối dù còn định dạng.
     *
     * @param headerRow dòng chứa tên các cột
     * @param dataFormatter bộ chuyển giá trị ô sang chuỗi
     * @return số cột đến header cuối có tên; bằng 0 nếu không có tên cột
     */
    private int getHeaderColumnCount(Row headerRow, DataFormatter dataFormatter) {
        int columnCount = 0;
        for (Cell headerCell : headerRow) {
            String headerValue = dataFormatter.formatCellValue(headerCell);
            if (!headerValue.isBlank()) {
                columnCount = headerCell.getColumnIndex() + 1;
            }
        }
        return columnCount;
    }

    /**
     * Đọc các dòng sau header thành dữ liệu JSON, bỏ qua dòng trống và giữ ô thiếu là chuỗi rỗng.
     *
     * @param worksheet sheet đầu tiên của workbook
     * @param columnNames tên trường tương ứng với vị trí từng cột
     * @param dataFormatter bộ chuyển giá trị ô sang chuỗi
     * @return danh sách các dòng dữ liệu theo thứ tự trong Excel
     * @throws APIException nếu có giá trị ở cột không có header
     */
    private List<Map<String, String>> readDataRows(
            Sheet worksheet,
            List<String> columnNames,
            DataFormatter dataFormatter
    ) {
        List<Map<String, String>> records = new ArrayList<>();
        for (Row dataRow : worksheet) {
            if (dataRow.getRowNum() == 0) {
                continue;
            }

            /* Khởi tạo đủ trường để ô thiếu vẫn có giá trị chuỗi rỗng trong JSON. */
            Map<String, String> rowData = new LinkedHashMap<>();
            for (String columnName : columnNames) {
                rowData.put(columnName, "");
            }
            boolean hasData = false;
            for (Cell dataCell : dataRow) {
                String cellValue = dataFormatter.formatCellValue(dataCell);
                int columnIndex = dataCell.getColumnIndex();
                if (columnIndex >= columnNames.size()) {
                    if (!cellValue.isEmpty()) {
                        log.warn("[EXCEL_SCAN][READ_ROWS] errorCode=INVALID_EXCEL reason=COLUMN_WITHOUT_HEADER row={} column={} headerColumnCount={}",
                                dataRow.getRowNum() + 1, columnIndex + 1, columnNames.size());
                        throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
                    }
                } else {
                    String columnName = columnNames.get(columnIndex);
                    rowData.put(columnName, cellValue);
                    if (!cellValue.isEmpty()) {
                        hasData = true;
                    }
                }
            }
            if (hasData) {
                records.add(rowData);
            }
        }
        return records;
    }
}
