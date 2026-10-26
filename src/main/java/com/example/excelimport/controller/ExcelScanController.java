package com.example.excelimport.controller;

import com.example.excelimport.dto.response.ExcelDataResponse;
import com.example.excelimport.service.ExcelScanService;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ExcelScanController {

    @Autowired
    private ExcelScanService excelScanService;

    @PostMapping("/excel-scans")
    public ResponseEntity<ExcelDataResponse> scanExcel(
            @RequestParam("file") MultipartFile excelFile
    ) {
        List<Map<String, String>> records = excelScanService.scanExcel(excelFile);

        ExcelDataResponse response = new ExcelDataResponse(
                "SCAN_SUCCESS",
                "Đọc file Excel thành công",
                records,
                List.of()
        );

        return ResponseEntity.ok(response);
    }
}
