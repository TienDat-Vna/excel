package com.example.excelimport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
    void scansCustomerIdsAndPreservesPhoneFormatting() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Customers");
            createCustomerHeader(sheet);
            sheet.getRow(0).getCell(0).setCellValue(" id ");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue("Nguyễn Văn A");
            row.createCell(2).setCellValue("a@example.com");
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("0000000000"));
            row.createCell(3).setCellValue(900000001);
            row.getCell(3).setCellStyle(style);
            sheet.createRow(2);
            sheet.createRow(3).createCell(0).setCellValue(2);
            workbook.createSheet("Ignored").createRow(0).createCell(0).setCellValue("other");

            var response = upload("data.xlsx", bytes(workbook));
            assertEquals(200, response.statusCode(), response.body());
            var json = mapper.readTree(response.body());
            assertTrue(json.isArray());
            assertEquals(2, json.size());
            assertTrue(json.at("/0/id").isIntegralNumber());
            assertEquals(1, json.at("/0/id").asLong());
            assertEquals("Nguyễn Văn A", json.at("/0/name").asText());
            assertEquals("a@example.com", json.at("/0/email").asText());
            assertEquals("0900000001", json.at("/0/phone").asText());
            assertEquals(4, json.at("/0").size());
            assertEquals(2, json.at("/1/id").asLong());
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

            header.getCell(1).setCellValue("name");
            header.getCell(2).setBlank();
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");

            header.getCell(2).setCellValue("email");
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
            sheet.createRow(1).createCell(0).setCellValue(1);
            var cell = sheet.getRow(1).createCell(1);
            cell.setCellFormula("1+1");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(cell);
            sheet.createRow(2).createCell(0).setCellValue(2);
            sheet.getRow(2).createCell(1).setCellErrorValue(FormulaError.DIV0.getCode());
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
    void readsExactIdsWithoutDisplayFormatting() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            createCustomerHeader(sheet);
            var id = sheet.createRow(1).createCell(0);
            id.setCellValue(125000);
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            id.setCellStyle(style);
            sheet.createRow(2).createCell(0).setCellValue("9223372036854775807");
            var formula = sheet.createRow(3).createCell(0);
            formula.setCellFormula("1+1");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(formula);

            var response = upload("data.xlsx", bytes(workbook));
            assertEquals(200, response.statusCode(), response.body());
            var json = mapper.readTree(response.body());
            assertTrue(json.at("/0/id").isNumber());
            assertTrue(response.body().contains("125000"), response.body());
            assertTrue(response.body().contains("9223372036854775807"), response.body());
            assertEquals("2", json.at("/2/id").asText());
        }
    }

    @Test
    void readsIdsEvenWhenNumberFormatHidesTheirDisplay() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            createCustomerHeader(sheet);
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("\"\";\"\";\"\";\"\""));
            var id = sheet.createRow(1).createCell(0);
            id.setCellValue(125000);
            id.setCellStyle(style);
            assertEquals("", new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(id));

            var response = upload("data.xlsx", bytes(workbook));
            assertEquals(200, response.statusCode(), response.body());
            var json = mapper.readTree(response.body());
            assertEquals(1, json.size());
            assertEquals("125000", json.at("/0/id").asText());

            id.setCellValue(1);
            response = upload("data.xlsx", bytes(workbook));
            assertEquals(200, response.statusCode(), response.body());
            var secondId = mapper.readTree(response.body()).at("/0/id");
            assertTrue(secondId.isNumber());
            assertEquals(1, secondId.asLong());
        }
    }

    @Test
    void skipsRowsWithOnlyBlankTextId() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            createCustomerHeader(sheet);
            sheet.createRow(1).createCell(0).setCellValue("   ");
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
    void rejectsInvalidIdsAndUnexpectedCustomerSchema() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            createCustomerHeader(sheet);
            var id = sheet.createRow(1).createCell(0);
            id.setCellValue("not-an-id");
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
            id.setCellErrorValue(FormulaError.DIV0.getCode());
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
            id.setCellValue(true);
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
            id.setBlank();
            sheet.createRow(2).createCell(4).setCellValue("extra");
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
            sheet.removeRow(sheet.getRow(2));
            sheet.getRow(0).getCell(0).setCellValue("product");
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
        }
    }

    @Test
    void rejectsMissingFractionalNonPositiveAndOutOfRangeIds() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            createCustomerHeader(sheet);
            var row = sheet.createRow(1);
            row.createCell(1).setCellValue("A");
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");

            var idCell = row.createCell(0);
            for (double invalidId : new double[]{0, -1, 1.5}) {
                idCell.setCellValue(invalidId);
                assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
            }
            for (String invalidId : List.of("0", "-1", "1.5", "9223372036854775808", "1e3")) {
                idCell.setCellValue(invalidId);
                assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
            }
        }
    }

    private void createCustomerHeader(org.apache.poi.ss.usermodel.Sheet sheet) {
        var header = sheet.createRow(0);
        header.createCell(0).setCellValue("id");
        header.createCell(1).setCellValue("name");
        header.createCell(2).setCellValue("email");
        header.createCell(3).setCellValue("phone");
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
