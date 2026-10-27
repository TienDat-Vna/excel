package com.example.excelimport.controller;

import com.example.excelimport.dto.CustomerData;
import com.example.excelimport.service.CustomerService;
import java.util.List;
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
    private CustomerService customerService;

    // POST /api/excel-scans-customer
    @PostMapping("/excel-scans-customer")
    public ResponseEntity<List<CustomerData>> scanCustomers(
            @RequestParam("file") MultipartFile excelFile
    ) {
        List<CustomerData> customers = customerService.scanCustomers(excelFile);

        return ResponseEntity.ok(customers);
    }
}
