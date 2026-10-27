# Excel → JSON API

API nhận một file `.xlsx` tối đa 10 MB, đọc sheet đầu tiên và trả dữ liệu JSON.
Dòng 1 gồm `name`, `email`, `phone`, `unitPrice` theo đúng thứ tự; mỗi dòng bên dưới map sang `CustomerData`. Không dùng database.

## Chạy ứng dụng

Yêu cầu JDK 25 trở lên và Maven Wrapper có sẵn trong repo. Cấu hình `JAVA_HOME`
trỏ đến JDK trước khi chạy; lần build đầu cần mạng để tải dependency.

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Hoặc chạy JAR sau khi build:

```powershell
java -jar target/excel-import-starter-0.0.1-SNAPSHOT.jar
```

Port mặc định 8080; đổi bằng biến môi trường `SERVER_PORT`.

## Upload bằng Postman

1. Import `postman/excel-import-starter.postman_collection.json`.
2. Mở request **01 - Scan Excel**, chọn **Body → form-data**.
3. Chọn file `.xlsx` ở key `file` (kiểu File), rồi gửi request.
4. Để Postman tự tạo Content-Type và multipart boundary. API local chưa cấu hình xác thực.

Endpoint: `POST /api/excel-scans-customer`.
Response thành công: HTTP 200, body là mảng `CustomerData` trực tiếp, không có lớp bọc code/message/data/errors.
`name`, `email`, `phone` trả dạng chuỗi; ô trống là `""`. `unitPrice` là số thập phân (`BigDecimal`), ô trống là `null`. Bỏ qua dòng trống.
File chỉ có header trả `[]`.

Giới hạn 10 MB/file, 11 MB/request. Từ chối file sai định dạng, header thiếu/trùng,
cột dữ liệu thiếu header hoặc ô gộp trên sheet được đọc.
Công thức dùng kết quả đã lưu, không tính lại. Ô lỗi ở trường chuỗi giữ nguyên; ô lỗi hoặc giá trị không phải số tại `unitPrice` trả `INVALID_EXCEL`.
Đặt số điện thoại/mã có số 0 đầu ở dạng Text trong Excel để giữ nguyên dữ liệu.

Response lỗi dùng `code = -1`, `errorCode` là mã lỗi; `message` là khóa thông báo và `messageDetail` là chi tiết tiếng Việt. Client cần đọc `errorCode` cho lỗi; khi thành công, đọc mảng trực tiếp từ body.

Contract và ví dụ JSON: [docs/api-contract.md](docs/api-contract.md).

## Cấu trúc chính

- `controller/ExcelScanController.java`: nhận multipart, gọi service và trả JSON.
- `dto/CustomerData.java`: DTO cố định gồm name/email/phone/unitPrice.
- `service/CustomerService.java`: mở/đóng workbook, đọc từng dòng và map DTO khách hàng.
- `service/ExcelScanService.java`: chỉ validate file, workbook, header và cấu trúc dòng.
- `constant/FileConstant.java`: hằng số dùng cho xử lý file, như giới hạn dung lượng.
- `exception/APIExceptionHandler.java`: chuyển lỗi sang HTTP status và JSON.
- `exception/ErrorCodeDetail.java`: tập trung HTTP status, mã lỗi và thông báo.
- `exception/APIException.java`: giữ code=-1, status/errorCode/message/messageDetail từ enum, cho phép chi tiết riêng.
- `dto/response/APIErrorResponse.java`: response lỗi riêng, gồm code/errorCode/message/messageDetail.
- `src/main/resources/application.yml`: port và giới hạn upload.
- `src/test/java/com/example/excelimport/ExcelScanTest.java`: test tích hợp qua HTTP thật.

Các đường dẫn Java chính nằm dưới `src/main/java/com/example/excelimport/`.
Stack: Spring MVC, Apache POI; không cần JDBC, H2 hay database bên ngoài.

## Quy ước đặt tên và cách đọc code

Tham chiếu quy ước tại `partner-portal-api/.agents/rules/code-conventions.md`,
`partner-account-api/AGENTS.md` và tên hàm trong `partner-data-ingestion/ExcelTemplate.groovy`.
Áp dụng cách đặt tên vào dự án này; giữ endpoint và cấu trúc JSON theo contract Excel.

- Package dùng chữ thường; class có hậu tố theo vai trò: `Controller`, `Service`, `Exception`, `Handler`.
- Class dùng `PascalCase`; biến/hàm dùng `camelCase`; hằng số và giá trị enum dùng `UPPER_SNAKE_CASE`.
- Hàm dùng động từ và đối tượng rõ ràng: `scanCustomers`, `validateFile`.
- Biến thể hiện dữ liệu cụ thể: `excelFile`, `headerRow`, `columnIndex`, `columnName`, `cellValue`, `rowData`.
- Khai báo kiểu rõ ràng cho dữ liệu Excel; dùng vòng lặp thông thường để dễ theo dõi.
- Viết các bước xử lý tuần tự; không vừa kiểm tra vừa thay đổi collection trong một điều kiện.
- Tách kiểm tra thiếu, trống và trùng thành các nhánh rõ ràng; dùng biến trung gian khi giúp hiểu luồng dữ liệu.
- Mỗi handler nhận một loại exception cụ thể; không gom lỗi rồi dùng `instanceof` để phân loại lại.
- Controller dùng `@RequestMapping` cho đường dẫn chung, `@Autowired` để inject service và `ResponseEntity` cho response.
- Trình bày controller theo từng khối: nhận tham số → lấy kết quả service → tạo biến `response` → trả response. Với khai báo nhiều dòng, đặt dấu đóng `)` ở dòng riêng; dùng dòng trống phân biệt các bước.
- Dùng Lombok cho getter và constructor enum; giữ constructor có xử lý riêng trong `APIException`.
- Mô tả hàm bằng Javadoc `/** ... */` với `@param`, `@return`, `@throws` khi phù hợp để IDE hiển thị khi rê chuột vào hàm. Comment bên trong hàm giải thích lý do xử lý; không thêm lớp hoặc interface chỉ để chia nhỏ code.

Người mới bắt đầu từ `ExcelScanController.scanCustomers` → `CustomerService.scanCustomers`.
Service khách hàng gọi validator, mở workbook, validate cấu trúc rồi đọc và map DTO;
workbook luôn được đóng bằng try-with-resources.
`ExcelScanService` không mở file, đọc danh sách bản ghi hoặc map DTO.
Header cố định và đúng thứ tự; bỏ khoảng trắng hai đầu tên cột.
`DataFormatter` giữ định dạng hiển thị cho các trường chuỗi.
`unitPrice` lấy giá trị số gốc, không lấy chuỗi hiển thị đã làm tròn hoặc có phân cách hàng nghìn.
Giá dạng text phải chuyển trực tiếp được sang `BigDecimal`, dùng dấu chấm thập phân, không chứa ký hiệu tiền/phân cách hàng nghìn.
Khi gặp lỗi, theo tiếp `APIException` → `ErrorCodeDetail` → `APIExceptionHandler`.

## Giới hạn hiện tại

Chỉ đọc sheet đầu tiên, dạng bảng đơn giản. Workbook và kết quả JSON được giữ trong RAM;
giới hạn file nén 10 MB không đồng nghĩa với giới hạn bộ nhớ khi giải nén.
Chưa kiểm thử tải lớn hoặc nhiều request đồng thời.
