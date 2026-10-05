# Session 15: Kiến Trúc Microservices Security - Phân Quyền Đa Tầng (Identity, Gateway & Course-Service)

Dự án triển khai mô hình bảo mật và phân quyền toàn diện cho kiến trúc Microservices gồm 3 dịch vụ:

1. **Identity-Service (Port 8085)**:
   - Sử dụng **Spring Security 6** và **JJWT 0.11.5**.
   - Thẩm định mật khẩu đã băm **BCrypt** trong database **PostgreSQL**.
   - Cấp **Access Token** ngắn hạn (15 phút, thuật toán **HS256**) với payload chứa standard claim `sub`, private claim `role` (`STUDENT`, `INSTRUCTOR`, `ADMIN`) và bắt buộc claim `jti` (UUID duy nhất).
   - Cấp **Refresh Token** dài hạn (chuỗi ngẫu nhiên UUID, sống 7 ngày) lưu trữ trong bảng `refresh_tokens`.

2. **Gateway-Service (Port 8088)**:
   - Sử dụng **Spring Cloud Gateway (Reactive WebFlux)** và **JJWT 0.11.5**.
   - Định tuyến `/api/courses/**` xuống **Course-Service** (Port 8087) và `/api/auth/**` xuống **Identity-Service** (Port 8085).
   - **Global Filter** (`JwtAuthenticationGlobalFilter`):
     - Dùng chung Secret Key với Identity-Service để thẩm định Access Token từ Header `Authorization: Bearer <token>`.
     - **Chặn từ sớm với mã 401 Unauthorized** nếu: không có Token, Token hết hạn, sai chữ ký, hoặc sai định dạng.
     - Nếu Token hợp lệ: trích xuất claim `role` từ Payload và gắn vào Header mới `X-User-Role` trước khi forward request xuống **Course-Service**.

3. **Course-Service (Port 8087)**:
   - **Method Level Security**: Phân quyền dựa trên Header `X-User-Role` do Gateway truyền xuống mà **KHÔNG** cần nhúng thư viện giải mã lại JWT.
   - **Database: KHÔNG**: Quản lý dữ liệu bằng danh sách tĩnh trong bộ nhớ (`Java In-Memory List`).
   - Phân quyền phương thức với `@PreAuthorize`:
     - `GET /api/courses`: `@PreAuthorize("hasAuthority('STUDENT') or hasAuthority('INSTRUCTOR')")` $\rightarrow$ Cho phép cả học viên và giảng viên xem danh sách khóa học.
     - `POST /api/courses`: `@PreAuthorize("hasAuthority('INSTRUCTOR')")` $\rightarrow$ **Chỉ cho phép giảng viên tạo khóa học**.
   - Khi tài khoản `STUDENT` cố gắng gọi `POST /api/courses` qua Gateway $\rightarrow$ Trả về mã lỗi **403 Forbidden**.

---

## 1. Cấu Trúc Toàn Bộ Dự Án

```text
SS15/
├── docker-compose.yml              # Khởi động PostgreSQL 16 (port 5433, db identity_db)
├── pom.xml                         # Parent POM điều phối build cả 3 services
├── postman_collection.json         # Kịch bản kiểm thử Postman tự động cho cả 3 services
├── README.md                       # Hướng dẫn chi tiết
├── identity-service/               # Cấp Token & Quản lý User (Port 8085)
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/identity/
│       │   ├── config/DataInitializer.java         # Nạp sẵn tài khoản student, instructor, admin
│       │   ├── controller/AuthController.java      # POST /api/auth/login & refresh
│       │   ├── model/ (User.java, RefreshToken.java)
│       │   ├── security/jwt/JwtUtils.java          # JJWT 0.11.5 (HS256, 15m, sub, role, jti)
│       │   └── security/WebSecurityConfig.java     # CSRF disabled, Stateless, permitAll
│       └── test/... (5 test cases)
├── gateway-service/                # API Gateway chặn 401 & Forward Header (Port 8088)
│   ├── pom.xml                     # spring-cloud-starter-gateway, jjwt 0.11.5
│   └── src/
│       ├── main/java/com/gateway/
│       │   ├── GatewayServiceApplication.java
│       │   └── filter/JwtAuthenticationGlobalFilter.java  # Global Filter kiểm tra JWT & gán X-User-Role
│       └── test/... (5 test cases)
└── course-service/                 # Downstream Service có Method Security (Port 8087)
    ├── pom.xml                     # spring-boot-starter-web, spring-boot-starter-security
    └── src/
        ├── main/java/com/course/
        │   ├── model/ (Course.java, CourseRequest.java)
        │   ├── security/
        │   │   ├── HeaderAuthenticationFilter.java        # Đọc Header X-User-Role gán Authority
        │   │   ├── CustomAccessDeniedHandler.java         # Bắt lỗi 403 Forbidden trả về JSON
        │   │   └── SecurityConfig.java                    # @EnableMethodSecurity(prePostEnabled=true)
        │   └── controller/CourseController.java           # @PreAuthorize trên GET và POST
        └── test/... (5 test cases)
```

---

## 2. Các Cổng Dịch Vụ (Service Ports)

| Service | Cổng (Port) | Mô tả |
| :--- | :--- | :--- |
| **PostgreSQL** | `5433` | Cơ sở dữ liệu cho Identity-Service (`identity_db`) |
| **Identity-Service** | `8085` | Xác thực đăng nhập & cấp phát Access/Refresh Token |
| **Course-Service** | `8087` | Quản lý khóa học, phân quyền Method Security `@PreAuthorize` |
| **Gateway-Service** | `8088` | Điểm truy cập API tập trung, thẩm định JWT & chuyển tiếp Header |

---

## 3. Hướng Dẫn Khởi Chạy

### 3.1 Khởi động Database
```bash
docker compose up -d
```

### 3.2 Khởi động các Microservices
1. **Khởi chạy Identity-Service**:
   ```bash
   cd identity-service
   mvn spring-boot:run
   ```
2. **Khởi chạy Course-Service**:
   ```bash
   cd course-service
   mvn spring-boot:run
   ```
3. **Khởi chạy Gateway-Service**:
   ```bash
   cd gateway-service
   mvn spring-boot:run
   ```

### 3.3 Chạy Toàn Bộ Test Suite Tự Động
Từ thư mục gốc `SS15`:
```bash
mvn clean test
```
Tất cả **15 test cases** của cả 3 services sẽ chạy và đều đạt trạng thái `SUCCESS`.

---

## 4. Hướng Dẫn Kiểm Thử Với Postman

Import file [`postman_collection.json`](postman_collection.json) vào Postman:

### 4.1 Quy trình kiểm thử lỗi 403 Forbidden theo yêu cầu
1. Chạy request **1.1 Login - Student**:
   - `POST {{gatewayUrl}}/api/auth/login` với `student` / `password123`.
   - Postman sẽ tự động lưu `accessTokenStudent`.
2. Chạy request **2.3 GET /api/courses (STUDENT xem danh sách)**:
   - Header: `Authorization: Bearer {{accessTokenStudent}}`
   - **Kết quả**: Mã `200 OK`, trả về danh sách khóa học.
3. Chạy request **2.4 POST /api/courses (STUDENT tạo khóa học)**:
   - Header: `Authorization: Bearer {{accessTokenStudent}}`
   - Body:
     ```json
     {
       "title": "Khóa học bị chặn của Student",
       "description": "Student không có quyền tạo khóa học này",
       "price": 99.0
     }
     ```
   - **Kết quả thực tế**: Mã lỗi **403 Forbidden**:
     ```json
     {
       "status": 403,
       "error": "Forbidden",
       "message": "Bạn không có quyền thực hiện thao tác này! Thao tác yêu cầu quyền INSTRUCTOR.",
       "path": "/api/courses"
     }
     ```
4. Chạy request **1.2 Login - Instructor**:
   - `POST {{gatewayUrl}}/api/auth/login` với `instructor` / `password123`.
   - Postman tự động lưu `accessTokenInstructor`.
5. Chạy request **2.5 POST /api/courses (INSTRUCTOR tạo khóa học)**:
   - Header: `Authorization: Bearer {{accessTokenInstructor}}`
   - **Kết quả**: Mã **201 Created**, khóa học mới được thêm vào danh sách thành công.
