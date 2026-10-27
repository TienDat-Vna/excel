package com.example.excelimport;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** Chỉ đọc DB local đã có dữ liệu; không tạo bảng hoặc sửa bản ghi. */
@EnabledIfEnvironmentVariable(named = "EXCEL_EXPORT_MYSQL_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerTradingValueDatabaseTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void exportsEveryDatabaseRowWithoutLosingPrecision() throws Exception {
        List<List<String>> expectedRows = jdbcTemplate.query("""
                SELECT id, customer_id, branch_id, pcc_id, total_trading_value
                FROM customer_trading_value_history ORDER BY id ASC
                """, (row, index) -> List.of(
                Long.toString(row.getLong("id")),
                Long.toString(row.getLong("customer_id")),
                row.getString("branch_id"),
                row.getString("pcc_id"),
                row.getBigDecimal("total_trading_value").toPlainString()
        ));

        HttpRequest request = HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + "/api/customer-trading-values/export"
        )).GET().build();
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofByteArray()
        );

        assertEquals(200, response.statusCode());
        int recordIndex = 0;
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.body()))) {
            for (var sheet : workbook) {
                assertEquals(5, sheet.getRow(0).getLastCellNum());
                for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    var dataRow = sheet.getRow(rowIndex);
                    List<String> expected = expectedRows.get(recordIndex++);
                    for (int columnIndex = 0; columnIndex < expected.size(); columnIndex++) {
                        assertEquals(expected.get(columnIndex), dataRow.getCell(columnIndex).getStringCellValue());
                    }
                }
            }
        }
        assertEquals(expectedRows.size(), recordIndex);
        System.out.println("Verified MySQL export rows=" + recordIndex);
    }
}
