# Session 15: Xác Thực Người Dùng với Spring Security & JJWT (Identity-Service)

Dự án triển khai dịch vụ **Identity-Service** áp dụng **Spring Security 6**, thư viện **JJWT 0.11.5**, và cơ sở dữ liệu **PostgreSQL** để:
- Kiểm chứng danh tính người dùng bằng thuật toán băm mật khẩu **BCrypt**.
- Cấp **Access Token** ngắn hạn (15 phút, thuật toán **HS256**) với payload chứa các claim:
  - Standard claim `sub`: Username của người dùng.
  - Private claim `role`: Vai trò của người dùng (ví dụ: `STUDENT`, `INSTRUCTOR`, `ADMIN`).
  - Bắt buộc claim `jti`: JWT ID - mã định danh duy nhất của token (chuỗi UUID).
  - Standard claim `iat` (thời điểm cấp) và `exp` (thời điểm hết hạn sau 15 phút).
- Cấp **Refresh Token** dài hạn (chuỗi ngẫu nhiên UUID, sống 7 ngày) và lưu trữ thông tin vào bảng `refresh_tokens` trong database PostgreSQL.
- Quản lý phiên **Stateless** và tắt **CSRF**, mở quyền truy cập `permitAll()` cho endpoint đăng nhập `/api/auth/login`.

---

## 1. Cấu Trúc Dự Án (Project Structure)

```text
SS15/
├── docker-compose.yml              # Khởi động PostgreSQL 16 (port 5433, db identity_db)
├── pom.xml                         # Parent POM điều phối toàn bộ build
├── postman_collection.json         # Postman Collection với kịch bản test và test script tự động
├── README.md                       # Hướng dẫn chi tiết vận hành và kiểm thử
└── identity-service/
    ├── pom.xml                     # Web, JPA, Security, PostgreSQL, JJWT 0.11.5
    └── src/
        ├── main/
        │   ├── java/com/identity/
        │   │   ├── IdentityServiceApplication.java
        │   │   ├── config/
        │   │   │   └── DataInitializer.java        # Tự động nạp sẵn tài khoản mẫu student, instructor
        │   │   ├── controller/
        │   │   │   └── AuthController.java         # Endpoint POST /api/auth/login & refresh
        │   │   ├── dto/
        │   │   │   ├── request/LoginRequest.java
        │   │   │   ├── request/TokenRefreshRequest.java
        │   │   │   └── response/TokenResponse.java
        │   │   ├── exception/
        │   │   │   └── GlobalExceptionHandler.java # Bắt lỗi 401 Unauthorized, 400 Bad Request
        │   │   ├── model/
        │   │   │   ├── User.java                   # Entity users & user_roles
        │   │   │   └── RefreshToken.java           # Entity refresh_tokens
        │   │   ├── repository/
        │   │   │   ├── UserRepository.java
        │   │   │   └── RefreshTokenRepository.java
        │   │   └── security/
        │   │       ├── WebSecurityConfig.java      # Tắt CSRF, Stateless, permitAll /api/auth/**
        │   │       ├── jwt/
        │   │       │   ├── JwtUtils.java           # Sinh & thẩm định JWT HS256, sub, role, jti
        │   │       │   └── AuthEntryPointJwt.java  # Xử lý phản hồi lỗi xác thực 401 JSON
        │   │       └── services/
        │   │           ├── RefreshTokenService.java # Sinh Refresh Token 7 ngày & lưu DB
        │   │           ├── UserDetailsImpl.java
        │   │           └── UserDetailsServiceImpl.java
        │   └── resources/
        │       └── application.yml
        └── test/
            ├── java/com/identity/
            │   └── IdentityServiceApplicationTests.java # 5 bài kiểm thử tự động
            └── resources/
                └── application-test.yml
```

---

## 2. Các Tài Khoản Mẫu Được Khởi Tạo Tự Động

Khi khởi động service, `DataInitializer` sẽ kiểm tra và khởi tạo sẵn các tài khoản sau (mật khẩu đã băm bằng **BCrypt**):

| Username | Password | Email | Role trong JWT (`role`) |
| :--- | :--- | :--- | :--- |
| `student` | `password123` | `student@example.com` | `STUDENT` |
| `instructor` | `password123` | `instructor@example.com` | `INSTRUCTOR` |
| `admin` | `password123` | `admin@example.com` | `ADMIN` |
| `user` | `password123` | `user@example.com` | `STUDENT` |

---

## 3. Hướng Dẫn Cài Đặt & Khởi Chạy

### 3.1 Khởi động Database PostgreSQL
Sử dụng Docker Compose để khởi chạy container PostgreSQL:
```bash
docker compose up -d
```
Cơ sở dữ liệu sẽ chạy trên cổng: `localhost:5433` (tên DB: `identity_db`, user: `postgres`, password: `postgrespassword`).

### 3.2 Khởi chạy Identity-Service
Chạy trực tiếp qua Maven:
```bash
cd identity-service
mvn spring-boot:run
```
Ứng dụng sẽ khởi động trên cổng: `http://localhost:8085`.

### 3.3 Chạy Bộ Test Tự Động (Automated Tests)
Để kiểm tra toàn diện luồng đăng nhập, sinh token, kiểm tra claims `sub`, `role`, `jti`, thời hạn token và lưu trữ database:
```bash
mvn clean test
```

---

## 4. Hướng Dẫn Kiểm Thử Với Postman

File collection đã được chuẩn bị sẵn tại: [`postman_collection.json`](postman_collection.json).

### Các bước thực hiện:
1. Mở **Postman** -> Chọn **Import** -> Chọn file `postman_collection.json`.
2. Kiểm tra biến `baseUrl` mặc định là `http://localhost:8085`.
3. Chạy lần lượt các request:

#### Request 1: `POST /api/auth/login` - Tài khoản Student
- **Body**:
  ```json
  {
      "username": "student",
      "password": "password123"
  }
  ```
- **Response mẫu (200 OK)**:
  ```json
  {
      "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiU1RVREVOVCIsImlkIjozLCJqdGkiOiIzODcxMTQ1Zi1kMDhkLTQ3OTMtODRlMi04MjU1ZjcyMDM1MzgiLCJlbWFpbCI6InN0dWRlbnRAZXhhbXBsZS5jb20iLCJzdWIiOiJzdHVkZW50IiwiaWF0IjoxNzkxMTY5MjEyLCJleHAiOjE3OTExNzAxMTJ9.mgF2SLT1W4T6VdnCoaHus_-NrcOSpSdf-lBR9N7NNno",
      "refreshToken": "d8b129b0-2ff6-4aef-841d-64d2f724686c",
      "tokenType": "Bearer",
      "expiresIn": 900,
      "username": "student",
      "role": "STUDENT"
  }
  ```

#### Request 2: `POST /api/auth/login` - Tài khoản Instructor
- **Body**:
  ```json
  {
      "username": "instructor",
      "password": "password123"
  }
  ```
- **Response**: Trả về `accessToken` với claim `role = INSTRUCTOR`.

#### Request 3: `POST /api/auth/login` - Thử Đăng Nhập Sai Mật Khẩu
- **Body**:
  ```json
  {
      "username": "student",
      "password": "saiMatKhau123"
  }
  ```
- **Response (401 Unauthorized)**:
  ```json
  {
      "error": "Unauthorized",
      "message": "Sai tên đăng nhập hoặc mật khẩu!",
      "status": 401
  }
  ```

#### Request 4: `POST /api/auth/refresh` - Cấp Lại Token Mới
- **Body**:
  ```json
  {
      "refreshToken": "{{refreshToken}}"
  }
  ```
- **Response (200 OK)**: Trả về cặp Access Token và Refresh Token mới.

---

## 5. Kiểm Thử Trực Quan Trên jwt.io

1. Copy giá trị của `accessToken` từ kết quả đăng nhập ở Postman.
2. Mở trình duyệt và truy cập trang web [https://jwt.io](https://jwt.io).
3. Dán token vào ô **Encoded**.
4. Quan sát phần **Decoded**:

### Header:
```json
{
  "alg": "HS256"
}
```

### Payload:
```json
{
  "role": "STUDENT",
  "id": 3,
  "jti": "3871145f-d08d-4793-84e2-8255f7203538",
  "email": "student@example.com",
  "sub": "student",
  "iat": 1791169212,
  "exp": 1791170112
}
```

**Đối chiếu với yêu cầu kỹ thuật**:
- `sub`: `"student"` (Standard Claim chứa username người dùng) - **ĐẠT**
- `role`: `"STUDENT"` (hoặc `"INSTRUCTOR"`, Private Claim chứa vai trò) - **ĐẠT**
- `jti`: `"3871145f-d08d-4793-84e2-8255f7203538"` (JWT ID - mã định danh duy nhất của token) - **ĐẠT**
- `exp - iat`: `1791170112 - 1791169212 = 900 giây (15 phút)` - **ĐẠT**

---

## 6. Kiểm Tra Dữ Liệu Bảng `refresh_tokens` Trong PostgreSQL

Mỗi khi người dùng đăng nhập thành công, một Refresh Token (chuỗi UUID ngẫu nhiên) với thời hạn sống 7 ngày sẽ được lưu vào bảng `refresh_tokens`.

Kiểm tra bằng lệnh `docker exec`:
```bash
docker exec -it identity-postgres psql -U postgres -d identity_db -c "
SELECT rt.id, rt.token, rt.expiry_date, u.username 
FROM refresh_tokens rt 
JOIN users u ON rt.user_id = u.id;
"
```

**Kết quả thực tế trong database**:
```text
 id |                token                 |         expiry_date          |  username  
----+--------------------------------------+------------------------------+------------
  7 | 930177ac-d5c2-470a-aae2-0b48403d1193 | 2026-10-12 03:00:39.73943+00 | student
  8 | cdeb696c-693d-4c31-96e5-9662a9696b1d | 2026-10-12 03:00:26.96349+00 | instructor
```

Nhận xét:
- `token`: Chuỗi UUID ngẫu nhiên.
- `expiry_date`: Đúng 7 ngày sau thời điểm đăng nhập.
- `username`: Liên kết chính xác với người dùng qua khóa ngoại `user_id`.
