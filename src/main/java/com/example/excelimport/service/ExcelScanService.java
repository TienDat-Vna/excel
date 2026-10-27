package com.example.excelimport.service;

import com.example.excelimport.constant.FileConstant;
import com.example.excelimport.exception.APIException;
import com.example.excelimport.exception.ErrorCodeDetail;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExcelScanService {

    /** Kiểm tra file không rỗng, có đuôi .xlsx và không vượt 10 MB. */
    public void validateFile(MultipartFile excelFile) {
        if (excelFile == null) {
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }
        if (excelFile.isEmpty()) {
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }

        if (excelFile.getSize() > FileConstant.MAX_FILE_SIZE_BYTES) {
            throw new APIException(ErrorCodeDetail.FILE_TOO_LARGE);
        }

        String fileName = excelFile.getOriginalFilename();
        if (fileName == null) {
            throw new APIException(ErrorCodeDetail.INVALID_FILE_TYPE);
        }
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new APIException(ErrorCodeDetail.INVALID_FILE_TYPE);
        }
    }

    /** validate workbook */
    public void validateWorkbook(Workbook workbook) {
        if (workbook.getNumberOfSheets() == 0) {
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }

        Sheet firstSheet = workbook.getSheetAt(0);
        if (firstSheet.getNumMergedRegions() > 0) {
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }
    }

    /** validate header */
    public void validateHeader(Sheet sheet, DataFormatter dataFormatter, List<String> expectedHeaders) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }

        for (int columnIndex = 0; columnIndex < expectedHeaders.size(); columnIndex++) {
            Cell headerCell = headerRow.getCell(columnIndex);
            String actualHeader = dataFormatter.formatCellValue(headerCell).strip();
            String expectedHeader = expectedHeaders.get(columnIndex);
            if (!expectedHeader.equals(actualHeader)) {
                throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
            }
        }

        // Excel có thể lưu ô trống có định dạng ở cuối dòng header.
        for (Cell headerCell : headerRow) {
            if (headerCell.getColumnIndex() >= expectedHeaders.size()
                    && !dataFormatter.formatCellValue(headerCell).isBlank()) {
                throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
            }
        }
    }

    /** validate data row */
    public void validateDataRow(Row dataRow, int columnCount, DataFormatter dataFormatter) {
        for (Cell cell : dataRow) {
            if (cell.getColumnIndex() < columnCount) {
                continue;
            }
            CellType cellType = cell.getCellType();
            if (cellType == CellType.FORMULA) {
                cellType = cell.getCachedFormulaResultType();
            }

            // Nếu ô có giá trị lưu trữ (numeric, boolean, error) hoặc ô không rỗng, báo lỗi.
            boolean hasStoredValue = cellType == CellType.NUMERIC
                    || cellType == CellType.BOOLEAN || cellType == CellType.ERROR;
            if (hasStoredValue || !dataFormatter.formatCellValue(cell).isEmpty()) {
                throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
            }
        }
    }
}
