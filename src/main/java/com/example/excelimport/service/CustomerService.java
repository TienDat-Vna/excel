package com.example.excelimport.service;

import com.example.excelimport.dto.CustomerData;
import com.example.excelimport.exception.APIException;
import com.example.excelimport.exception.ErrorCodeDetail;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JRuntimeException;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CustomerService {

    private static final List<String> CUSTOMER_HEADERS = List.of("id", "name", "email", "phone");

    @Autowired
    private ExcelScanService excelScanService;

    /** Hàm Chính */
    public List<CustomerData> scanCustomers(MultipartFile excelFile) {

        // validate file
        excelScanService.validateFile(excelFile);

        // validate workbook and read data
        try (InputStream inputStream = excelFile.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {

            excelScanService.validateWorkbook(workbook);
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter dataFormatter = new DataFormatter(Locale.US);
            dataFormatter.setUseCachedValuesForFormulaCells(true);

            // validate header
            excelScanService.validateHeader(sheet, dataFormatter, CUSTOMER_HEADERS);

            // read data rows and map to CustomerData
            return readCustomers(sheet, dataFormatter);

        } catch (IOException | POIXMLException | IllegalArgumentException | OpenXML4JRuntimeException exception) {
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }
    }

    /** Đọc dữ liệu khách hàng từ sheet. */
    private List<CustomerData> readCustomers(Sheet sheet, DataFormatter dataFormatter) {
        List<CustomerData> customers = new ArrayList<>();

        for (Row dataRow : sheet) {
            if (dataRow.getRowNum() == 0) {
                continue;
            }
            excelScanService.validateDataRow(dataRow, CUSTOMER_HEADERS.size(), dataFormatter);

            Long id = readId((XSSFCell) dataRow.getCell(0));
            String name = dataFormatter.formatCellValue(dataRow.getCell(1));
            String email = dataFormatter.formatCellValue(dataRow.getCell(2));
            String phone = dataFormatter.formatCellValue(dataRow.getCell(3));

            if (id == null && name.isEmpty() && email.isEmpty() && phone.isEmpty()) {
                continue;
            }
            if (id == null) {
                throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
            }
            customers.add(new CustomerData(id, name, email, phone));
        }

        return customers;
    }

    /** Đọc id là số nguyên dương; ô trống trả null, sai kiểu hoặc vượt Long báo lỗi. */
    private Long readId(XSSFCell idCell) {

        // Nếu ô trống hoặc null, trả về null
        if (idCell == null || idCell.getCellType() == CellType.BLANK) {
            return null;
        }

        // Nếu ô có công thức, lấy giá trị đã tính toán
        CellType cellType = idCell.getCellType();
        if (cellType == CellType.FORMULA) {
            cellType = idCell.getCachedFormulaResultType();
        }

        String idValue;
        if (cellType == CellType.NUMERIC) {
            idValue = idCell.getRawValue();
        } else if (cellType == CellType.STRING) {
            idValue = idCell.getStringCellValue().strip();
            if (idValue.isEmpty()) {
                return null;
            }
        } else {
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }

        if (idValue == null) {
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }

        try {
            long id = cellType == CellType.NUMERIC
                    ? new BigDecimal(idValue).longValueExact() : Long.parseLong(idValue);
            if (id <= 0) {
                throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
            }
            return id;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }
    }
}
