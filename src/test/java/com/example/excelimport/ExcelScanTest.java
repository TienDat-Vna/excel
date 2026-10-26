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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
    void scansDynamicHeadersAndFormattedValuesFromFirstSheet() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Products");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue(" product ");
            header.createCell(1).setCellValue("quantity");
            header.createCell(2).setCellValue("reference");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Sản phẩm A");
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
            assertEquals("SCAN_SUCCESS", json.path("code").asText());
            assertEquals(2, json.path("data").size());
            assertEquals("Sản phẩm A", json.at("/data/0/product").asText());
            assertEquals("12", json.at("/data/0/quantity").asText());
            assertEquals("000123", json.at("/data/0/reference").asText());
            assertEquals("", json.at("/data/1/quantity").asText());
        }
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsOneSummaryAfterSuccessfulRead(CapturedOutput output) throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("private-header");
            sheet.createRow(1).createCell(0).setCellValue("private-cell");
            int logStart = output.getAll().length();

            var response = upload("private-file.xlsx", bytes(workbook));

            assertEquals(200, response.statusCode());
            List<String> logs = scanLogs(output, logStart);
            assertEquals(1, logs.size(), logs.toString());
            assertTrue(logs.getFirst().contains("result=SUCCESS"));
            assertTrue(logs.getFirst().contains("rowCount=1 columnCount=1 readDurationMs="));
            assertFalse(logs.getFirst().contains("private-"));
            assertFalse(output.getAll().substring(logStart).contains("private-cell"),
                    "Framework logs must not print Excel response data");
        }
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsOneFailureWithCoordinatesWithoutHeaderOrFilename(CapturedOutput output) throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var header = workbook.createSheet().createRow(0);
            header.createCell(0).setCellValue("private-header");
            header.createCell(1).setCellValue("private-header");
            int logStart = output.getAll().length();

            var response = upload("private-file.xlsx", bytes(workbook));

            assertError(response, 400, "INVALID_EXCEL");
            List<String> logs = scanLogs(output, logStart);
            assertEquals(1, logs.size(), logs.toString());
            assertTrue(logs.getFirst().contains("reason=DUPLICATE_COLUMN_NAME row=1 column=2 firstColumn=1"));
            assertFalse(logs.getFirst().contains("private-"));
            assertFalse(logs.getFirst().contains("result=SUCCESS"));
        }
    }

    private List<String> scanLogs(CapturedOutput output, int start) {
        return output.getAll().substring(start).lines()
                .filter(line -> line.contains("[EXCEL_SCAN]["))
                .toList();
    }

    @Test
    void headerOnlyReturnsEmptyArray() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            workbook.createSheet().createRow(0).createCell(0).setCellValue("field");
            var response = upload("data.xlsx", bytes(workbook));
            assertEquals(200, response.statusCode());
            assertTrue(mapper.readTree(response.body()).path("data").isEmpty());
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
    void rejectsMissingDuplicateAndUnmappedHeaders() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("id");
            header.createCell(1).setCellValue(" id ");
            assertError(upload("data.xlsx", bytes(workbook)), 400, "INVALID_EXCEL");
            header.getCell(1).setBlank();
            sheet.createRow(1).createCell(1).setCellValue("unmapped");
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
            sheet.createRow(0).createCell(0).setCellValue("value");
            var cell = sheet.createRow(1).createCell(0);
            cell.setCellFormula("1+1");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(cell);
            sheet.createRow(2).createCell(0).setCellErrorValue(FormulaError.DIV0.getCode());
            var response = upload("data.xlsx", bytes(workbook));
            assertEquals(200, response.statusCode());
            var json = mapper.readTree(response.body());
            assertEquals("2", json.at("/data/0/value").asText());
            assertEquals("#DIV/0!", json.at("/data/1/value").asText());
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

    private URI endpoint() {
        return URI.create("http://localhost:" + port + "/api/excel-scans");
    }

    @Test
    void previousCustomerImportUrlIsNotAvailable() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            workbook.createSheet().createRow(0).createCell(0).setCellValue("field");
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
        assertEquals(code, json.path("code").asText());
        assertTrue(json.path("data").isNull());
    }

    private void assertDefaultError(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertFalse(mapper.readTree(response.body()).has("code"), "Spring HTTP errors must not use ExcelDataResponse");
    }
}
