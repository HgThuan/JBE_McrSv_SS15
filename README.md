# Session 15: Kiến Trúc Microservices Security - Identity-Service & Gateway-Service

Dự án triển khai mô hình kiến trúc bảo mật tập trung cho Microservices bao gồm:
1. **Identity-Service (Port 8085)**:
   - Sử dụng **Spring Security 6** và **JJWT 0.11.5**.
   - Xác thực mật khẩu đã băm **BCrypt** trong cơ sở dữ liệu **PostgreSQL**.
   - Cấp **Access Token** ngắn hạn (15 phút, thuật toán **HS256**) với payload chứa đầy đủ standard claim `sub`, private claim `role` (`STUDENT`, `INSTRUCTOR`, `ADMIN`) và bắt buộc claim `jti` (JWT ID dạng UUID).
   - Cấp **Refresh Token** dài hạn (chuỗi ngẫu nhiên UUID, sống 7 ngày) lưu vào bảng `refresh_tokens`.
2. **Gateway-Service (Port 8088)**:
   - Sử dụng **Spring Cloud Gateway** (Reactive WebFlux) và thư viện **JJWT 0.11.5**.
   - Định tuyến `/api/courses/**` xuống **Course-Service** (Port 8087) và `/api/auth/**` xuống **Identity-Service** (Port 8085).
   - **Global Filter** (`JwtAuthenticationGlobalFilter`):
     - Dùng chung Secret Key với Identity-Service để thẩm định JWT từ header `Authorization: Bearer <token>`.
     - Chặn từ sớm với mã **401 Unauthorized** nếu: không có Token, Token đã hết hạn, sai chữ ký, hoặc sai định dạng.
     - Nếu Token hợp lệ: trích xuất claim `role` từ Payload và gắn vào Header mới `X-User-Role` trước khi forward request xuống **Course-Service**.
3. **Course-Service (Port 8087)**:
   - Dịch vụ downstream cung cấp tài nguyên khóa học `/api/courses`.
   - Đọc Header `X-User-Role` và `X-User-Name` do Gateway chuyển tiếp tới để nhận biết vai trò của người dùng.

---

## 1. Cấu Trúc Toàn Bộ Dự Án

```text
SS15/
├── docker-compose.yml              # Khởi động PostgreSQL 16 (port 5433, db identity_db)
├── pom.xml                         # Parent POM quản lý cả 3 services
├── postman_collection.json         # Toàn bộ kịch bản test Identity & Gateway
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
└── course-service/                 # Downstream Service (Port 8087)
    ├── pom.xml
    └── src/
        ├── main/java/com/course/controller/CourseController.java (Đọc X-User-Role)
        └── test/...
```

---

## 2. Các Cổng Dịch Vụ (Service Ports)

| Service | Cổng (Port) | Mô tả |
| :--- | :--- | :--- |
| **PostgreSQL** | `5433` | Cơ sở dữ liệu cho Identity-Service (`identity_db`) |
| **Identity-Service** | `8085` | Xác thực đăng nhập & cấp phát Access/Refresh Token |
| **Course-Service** | `8087` | Dịch vụ khóa học (Downstream) |
| **Gateway-Service** | `8088` | Điểm truy cập API trung tâm & Thẩm định JWT |

---

## 3. Hướng Dẫn Khởi Chạy

### 3.1 Khởi động Database
```bash
docker compose up -d
```

### 3.2 Khởi động các Microservices
Mở 3 terminal riêng hoặc chạy ngầm:

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

### 3.3 Chạy Bộ Test Tự Động Toàn Dự Án
Từ thư mục gốc `SS15`:
```bash
mvn clean test
```
Tất cả các bài kiểm thử của cả 3 services sẽ chạy và đều đạt trạng thái `SUCCESS`.

---

## 4. Hướng Dẫn Kiểm Thử Với Postman

Import file [`postman_collection.json`](postman_collection.json) vào Postman. Collection gồm 2 thư mục:

### Thư mục 1: `1. Identity-Service (Auth)`
- `1.1 Login - Student`: Đăng nhập `student` / `password123`. Trả về `accessToken` và `refreshToken`. Tự động lưu `accessToken` vào biến collection.
- `1.2 Login - Instructor`: Đăng nhập `instructor` / `password123`. Lưu `accessTokenInstructor`.
- `1.3 Login - Sai mật khẩu`: Nhận mã `401 Unauthorized`.
- `1.4 Refresh Token`: Đổi `refreshToken` lấy cặp token mới.

### Thư mục 2: `2. Gateway-Service Security & Routing`
- `2.1 GET /api/courses (Không đính kèm Token)`:
  - **Kết quả mong đợi**: Gateway chặn ngay lập tức với mã `401 Unauthorized`.
  - **Response body**:
    ```json
    {
      "status": 401,
      "error": "Unauthorized",
      "message": "Thiếu hoặc sai định dạng Header Authorization Bearer Token!",
      "path": "/api/courses"
    }
    ```
- `2.2 GET /api/courses (Token đã hết hạn)`:
  - **Kết quả mong đợi**: Gateway chặn ngay với mã `401 Unauthorized` ("Access Token đã hết hạn! Vui lòng đăng nhập lại.").
- `2.3 GET /api/courses (Token sai chữ ký)`:
  - **Kết quả mong đợi**: Gateway chặn ngay với mã `401 Unauthorized` ("Access Token sai chữ ký số!").
- `2.4 GET /api/courses (Student Token hợp lệ)`:
  - Header: `Authorization: Bearer {{accessToken}}`
  - **Kết quả mong đợi**: Mã `200 OK`.
  - **Response body**:
    ```json
    {
      "courses": [ ... ],
      "xUserRole": "STUDENT",
      "xUserName": "student",
      "totalCourses": 3,
      "message": "Lấy danh sách khóa học thành công từ Course-Service!"
    }
    ```
- `2.5 GET /api/courses (Instructor Token hợp lệ)`:
  - Header: `Authorization: Bearer {{accessTokenInstructor}}`
  - **Kết quả mong đợi**: Mã `200 OK`, `xUserRole: "INSTRUCTOR"`.

---

## 5. Kiểm Thử Trực Quan Trên jwt.io

Dán `accessToken` vào [https://jwt.io](https://jwt.io):
- **Header**:
  ```json
  { "alg": "HS256" }
  ```
- **Payload**:
  ```json
  {
    "sub": "student",
    "role": "STUDENT",
    "jti": "3871145f-d08d-4793-84e2-8255f7203538",
    "iat": 1791169212,
    "exp": 1791170112
  }
  ```
- Thời gian sống `exp - iat = 900 giây` (**15 phút**).
- Claim `role` được Gateway đọc và truyền tiếp xuống downstream qua `X-User-Role`.
