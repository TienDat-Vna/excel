package com.example.excelimport.controller;

import com.example.excelimport.exception.APIException;
import com.example.excelimport.exception.ErrorCodeDetail;
import com.example.excelimport.service.CustomerTradingValueService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer-trading-values")
public class CustomerTradingValueController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerTradingValueController.class);

    @Autowired
    private CustomerTradingValueService customerTradingValueService;

    /** Xuất tất cả bản ghi */
    @GetMapping("/export")
    public void exportAll(HttpServletResponse response) {

        Path exportFile = customerTradingValueService.exportAll();

        try {
            // Lấy kích thước file để set Content-Length
            long fileSize = Files.size(exportFile);

            // Set các header để trình duyệt tải file về
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename("customer-trading-values.xlsx")
                    .build().toString());
            response.setContentLengthLong(fileSize);

            // Ghi file vào OutputStream của response
            Files.copy(exportFile, response.getOutputStream());
        } catch (IOException exception) {

            // Nếu response chưa được commit, reset response và ném APIException
            if (!response.isCommitted()) {
                response.reset();
                throw new APIException(ErrorCodeDetail.EXCEL_EXPORT_FAILED);
            }
            LOGGER.error("[TRADING_VALUE][DOWNLOAD] errorCode=EXPORT_DOWNLOAD_FAILED");
        } finally {
            // Xóa file tạm sau khi xuất xong
            customerTradingValueService.deleteExport(exportFile);
        }
    }
}
