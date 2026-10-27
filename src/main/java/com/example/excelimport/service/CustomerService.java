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

    private static final List<String> CUSTOMER_HEADERS = List.of("name", "email", "phone", "unitPrice");

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

            String name = dataFormatter.formatCellValue(dataRow.getCell(0));
            String email = dataFormatter.formatCellValue(dataRow.getCell(1));
            String phone = dataFormatter.formatCellValue(dataRow.getCell(2));
            BigDecimal unitPrice = readUnitPrice((XSSFCell) dataRow.getCell(3));

            if (name.isEmpty() && email.isEmpty() && phone.isEmpty() && unitPrice == null) {
                continue;
            }
            customers.add(new CustomerData(name, email, phone, unitPrice));
        }

        return customers;
    }

    /** Đọc giá gốc sang BigDecimal */
    private BigDecimal readUnitPrice(XSSFCell priceCell) {

        // Nếu ô trống hoặc null, trả về null
        if (priceCell == null || priceCell.getCellType() == CellType.BLANK) {
            return null;
        }

        // Nếu ô có công thức, lấy giá trị đã tính toán
        CellType cellType = priceCell.getCellType();
        if (cellType == CellType.FORMULA) {
            cellType = priceCell.getCachedFormulaResultType();
        }

        String priceValue;
        if (cellType == CellType.NUMERIC) {
            priceValue = priceCell.getRawValue();
        } else if (cellType == CellType.STRING) {
            priceValue = priceCell.getStringCellValue().strip();
            if (priceValue.isEmpty()) {
                return null;
            }
        } else {
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }

        if (priceValue == null) {
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }

        try {
            return new BigDecimal(priceValue);
        } catch (NumberFormatException exception) {
            throw new APIException(ErrorCodeDetail.INVALID_EXCEL);
        }
    }
}
