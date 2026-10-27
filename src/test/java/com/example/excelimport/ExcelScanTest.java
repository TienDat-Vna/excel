package com.example.excelimport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.usermodel.FormulaError;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExcelScanTest {
    @LocalServerPort
    int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void scansFixedCustomerDtoAndFormattedValuesFromFirstSheet() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Customers");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue(" name ");
            header.createCell(1).setCellValue("email");
            header.createCell(2).setCellValue("phone");
            header.createCell(3).setCellValue("unitPrice");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Nguyễn Văn A");
            row.createCell(1).setCellValue(12);
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("000000"));
            row.createCell(2).setCellValue(123);
            row.getCell(2).setCellStyle(style);
            sheet.createRow(2);
            sheet.createRow(3).createCell(0).setCellValue("B");
            workbook.createSheet("Ignored").createRow(0).createCell(0).setCellValue("other");
            var response = upload("data.xlsx", bytes(workbook));
            assertEquals(200, response.statusCode());
            var json = mapper.readTree(response.body());
            assertTrue(json.isArray());
            assertEquals(2, json.size());
            assertEquals("Nguyễn Văn A", json.at("/0/name").asText());
            assertEquals("12", json.at("/0/email").asText());
            assertEquals("000123", json.at("/0/phone").asText());
            assertEquals(4, json.at("/0").size());
            assertTrue(json.at("/0/unitPrice").isNull());
            assertEquals("", json.at("/1/email").asText());
        }
    }

    @Test
    void headerOnlyReturnsEmptyArray() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            createCustomerHeader(workbook.createSheet());
            var response = upload("data.xlsx", bytes(workbook));
            assertEquals(200, response.statusCode());
            assertTrue(mapper.readTree(response.body()).isEmpty());
        }
    }

    @Test
    void rejectsInvalidFiles() throws Exception {
        assertError(upload("data.xlsx", new byte[0]), 400, "INVALID_EXCEL");
        assertError(upload("data.csv", new byte[]{1}), 400, "INVALID_FILE_TYPE");
        assertError(upload("data.xlsx", "not excel".getBytes(StandardCharsets.UTF_8)), 400, "INVALID_EXCEL");
        assertError(upload("data.xlsx", new byte[10 * 1024 * 1024 + 1]), 413, "FILE_TOO_LARGE");
        // Exactly 10 MB passes the size check and fails workbook parsing instead.
        assertError(upload("data.xlsx", new byte[10 * 1024 * 1024]), 400, "INVALID_EXCEL");
    }

    @Test
    void rejectsMissingAndDuplicateHeaders() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("id");
            header.createCell(1).setCellValue(" id ");
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
        }
    }

    @Test
    void validatesHeaderOrderAndIgnoresTrailingBlankCells() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            createCustomerHeader(sheet);
            var header = sheet.getRow(0);
            header.createCell(6).setBlank();
            assertEquals(200, upload("data.xlsx", bytes(workbook)).statusCode());

            header.getCell(1).setCellValue("phone");
            header.getCell(2).setCellValue("email");
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");

            header.getCell(1).setCellValue("email");
            header.getCell(2).setBlank();
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");

            header.getCell(2).setCellValue("phone");
            header.getCell(6).setCellValue("extra");
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
        }
    }

    @Test
    void rejectsLegacyWorkbookRenamedToXlsx() throws Exception {
        try (var workbook = new HSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.createSheet().createRow(0).createCell(0).setCellValue("id");
            workbook.write(output);
            assertError(upload("renamed.xlsx", output.toByteArray()), 400, "INVALID_EXCEL");
        }
    }

    @Test
    void readsCachedFormulasAndErrorsButRejectsMergedCells() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            createCustomerHeader(sheet);
            var cell = sheet.createRow(1).createCell(0);
            cell.setCellFormula("1+1");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(cell);
            sheet.createRow(2).createCell(0).setCellErrorValue(FormulaError.DIV0.getCode());
            var response = upload("data.xlsx", bytes(workbook));
            assertEquals(200, response.statusCode());
            var json = mapper.readTree(response.body());
            assertEquals("2", json.at("/0/name").asText());
            assertEquals("#DIV/0!", json.at("/1/name").asText());
            cell.setBlank();
            sheet.addMergedRegion(new CellRangeAddress(1, 2, 0, 0));
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
        }
    }

    @Test
    void handlesMissingFileAndWrongContentType() throws Exception {
        var missing = client.send(HttpRequest.newBuilder(endpoint())
                .header("Content-Type", "multipart/form-data; boundary=scan-boundary")
                .POST(HttpRequest.BodyPublishers.ofString("--scan-boundary--\r\n")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertError(missing, 400, "INVALID_EXCEL");
        var malformed = client.send(HttpRequest.newBuilder(endpoint())
                .header("Content-Type", "multipart/form-data")
                .POST(HttpRequest.BodyPublishers.ofString("invalid multipart")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertError(malformed, 400, "INVALID_EXCEL");
        var wrong = client.send(HttpRequest.newBuilder(endpoint()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertError(wrong, 400, "INVALID_EXCEL");
    }

    @Test
    void mapsDecimalPricesWithoutDisplayRounding() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            createCustomerHeader(sheet);
            var price = sheet.createRow(1).createCell(3);
            price.setCellValue(125000.125);
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            price.setCellStyle(style);
            sheet.createRow(2).createCell(3).setCellValue("12345678901234567890.123456789");
            var formula = sheet.createRow(3).createCell(3);
            formula.setCellFormula("1/4");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(formula);

            var response = upload("data.xlsx", bytes(workbook));
            assertEquals(200, response.statusCode(), response.body());
            var json = mapper.readTree(response.body());
            assertTrue(json.at("/0/unitPrice").isNumber());
            assertTrue(response.body().contains("125000.125"), response.body());
            assertTrue(response.body().contains("12345678901234567890.123456789"), response.body());
            assertEquals("0.25", json.at("/2/unitPrice").asText());
        }
    }

    @Test
    void readsPricesEvenWhenNumberFormatHidesTheirDisplay() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            createCustomerHeader(sheet);
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("\"\";\"\";\"\";\"\""));
            var price = sheet.createRow(1).createCell(3);
            price.setCellValue(125000.125);
            price.setCellStyle(style);
            assertEquals("", new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(price));

            var response = upload("data.xlsx", bytes(workbook));
            assertEquals(200, response.statusCode(), response.body());
            var json = mapper.readTree(response.body());
            assertEquals(1, json.size());
            assertEquals("125000.125", json.at("/0/unitPrice").asText());

            price.setCellValue(0);
            response = upload("data.xlsx", bytes(workbook));
            assertEquals(200, response.statusCode(), response.body());
            var zero = mapper.readTree(response.body()).at("/0/unitPrice");
            assertTrue(zero.isNumber());
            assertEquals(0.0, zero.asDouble());
        }
    }

    @Test
    void skipsRowsWithOnlyBlankTextPrice() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            createCustomerHeader(sheet);
            sheet.createRow(1).createCell(3).setCellValue("   ");
            var response = upload("data.xlsx", bytes(workbook));
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(mapper.readTree(response.body()).isEmpty());
        }
    }

    @Test
    void rejectsValuesOutsideSchemaEvenWhenNumberFormatHidesThem() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            createCustomerHeader(sheet);
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("\"\";\"\";\"\";\"\""));
            var extra = sheet.createRow(1).createCell(4);
            extra.setCellValue(100);
            extra.setCellStyle(style);
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
        }
    }

    @Test
    void rejectsInvalidPricesAndUnexpectedCustomerSchema() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            createCustomerHeader(sheet);
            var price = sheet.createRow(1).createCell(3);
            price.setCellValue("not-a-price");
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
            price.setCellErrorValue(FormulaError.DIV0.getCode());
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
            price.setCellValue(true);
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
            price.setBlank();
            sheet.createRow(2).createCell(4).setCellValue("extra");
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
            sheet.removeRow(sheet.getRow(2));
            sheet.getRow(0).getCell(0).setCellValue("product");
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
        }
    }

    private void createCustomerHeader(org.apache.poi.ss.usermodel.Sheet sheet) {
        var header = sheet.createRow(0);
        header.createCell(0).setCellValue("name");
        header.createCell(1).setCellValue("email");
        header.createCell(2).setCellValue("phone");
        header.createCell(3).setCellValue("unitPrice");
    }

    private URI endpoint() {
        return URI.create("http://localhost:" + port + "/api/excel-scans-customer");
    }

    @Test
    void previousCustomerImportUrlIsNotAvailable() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            createCustomerHeader(workbook.createSheet());
            var response = upload(URI.create("http://localhost:" + port + "/api/customers/import"),
                    "data.xlsx", bytes(workbook));
            assertDefaultError(response, 404);
        }
    }

    @Test
    void preservesHttpMethodAndNotFoundSemantics() throws Exception {
        var wrongMethod = client.send(HttpRequest.newBuilder(endpoint()).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertDefaultError(wrongMethod, 405);
        assertTrue(wrongMethod.headers().firstValue("Allow").orElse("").contains("POST"));
        var missing = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/unknown"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertDefaultError(missing, 404);
    }

    private HttpResponse<String> upload(String filename, byte[] content) throws Exception {
        return upload(endpoint(), filename, content);
    }

    private HttpResponse<String> upload(URI uri, String filename, byte[] content) throws Exception {
        var body = new ByteArrayOutputStream();
        body.write(("--scan-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + filename + "\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write("\r\n--scan-boundary--\r\n".getBytes(StandardCharsets.UTF_8));
        return client.send(HttpRequest.newBuilder(uri)
                .header("Content-Type", "multipart/form-data; boundary=scan-boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private byte[] bytes(XSSFWorkbook workbook) throws Exception {
        var output = new ByteArrayOutputStream();
        workbook.write(output);
        return output.toByteArray();
    }

    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        JsonNode json = mapper.readTree(response.body());
        assertEquals(-1, json.path("code").asInt());
        assertEquals(code, json.path("errorCode").asText());
        assertEquals(code, json.path("message").asText());
        String messageDetail = switch (code) {
            case "INVALID_FILE_TYPE" -> "Chỉ nhận file .xlsx.";
            case "INVALID_EXCEL" -> "File Excel không hợp lệ.";
            case "FILE_TOO_LARGE" -> "File tối đa 10 MB.";
            default -> throw new AssertionError("Unexpected error code: " + code);
        };
        assertEquals(messageDetail, json.path("messageDetail").asText());
        assertEquals(4, json.size());
    }

    private void assertDefaultError(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertFalse(mapper.readTree(response.body()).has("code"), "Spring HTTP errors must not use APIErrorResponse");
    }
}
