# User Profile Service

Service quản lý thông tin profile của user bao gồm vị trí công việc (Position), lĩnh vực (Track) và cấp độ (Level).

## Mục lục

- [Kiến trúc](#kiến-trúc)
- [Entity Structure](#entity-structure)
- [API Documentation](#api-documentation)
- [Setup & Run](#setup--run)
- [Testing](#testing)

---

## Kiến trúc

### Database Schema

```
UserProfile (1) -----> (1) Position (N) -----> (1) PositionTrack
                                    (N) -----> (1) PositionLevel
```

### Entities:

1. **UserProfile**: Thông tin profile của user
   - userId (PK): ID của user từ IAM service
   - fullName: Họ tên đầy đủ
   - city: Thành phố
   - experience: Số năm kinh nghiệm
   - positionId (FK): ID của position

2. **Position**: Vị trí công việc (kết hợp Track + Level)
   - positionId (PK): UUID
   - trackId (FK): ID của track
   - levelId (FK): ID của level
   - Unique constraint: (trackId, levelId)

3. **PositionTrack**: Lĩnh vực công việc
   - id (PK): UUID
   - name: Tên track (Backend, Frontend, DevOps...)
   - active: Trạng thái active

4. **PositionLevel**: Cấp độ vị trí
   - id (PK): UUID
   - positionRole: Tên level (JUNIOR, MID, SENIOR, LEAD, MANAGER)
   - active: Trạng thái active

---

## Entity Structure

### Position Logic

Position được tạo từ sự kết hợp của Track và Level:

```
Position = PositionTrack + PositionLevel

Ví dụ:
- Backend Developer + SENIOR = Senior Backend Developer
- Frontend Developer + JUNIOR = Junior Frontend Developer
- DevOps Engineer + LEAD = Lead DevOps Engineer
```

---

## API Documentation

### Base URL
```
http://localhost:8082/api/v1
```

---

## 1. USER PROFILE APIs

### 1.1. Tạo User Profile
Endpoint: POST /profiles/{userId}

Authorization: ROLE_IAM (Được gọi từ IAM Service)

Path Parameters:
- userId (String, required): ID của user

Headers:
- X-User-Id: ID của IAM service
- X-User-Role: IAM
- X-User-Email: Email của IAM service

Request Body:
```json
{
  "fullName": "Nguyễn Văn A",
  "trackId": "uuid-of-backend-track",
  "levelId": "uuid-of-senior-level",
  "city": "Ho Chi Minh",
  "experience": 5
}
```

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "userId": "user-uuid-123",
    "fullName": "Nguyễn Văn A",
    "city": "Ho Chi Minh",
    "experience": 5,
    "position": {
      "positionId": "auto-generated-uuid",
      "trackName": "Backend Developer",
      "levelName": "SENIOR"
    }
  }
}
```

---

### 1.2. Lấy Profile của User hiện tại
Endpoint: GET /profiles

Authorization: ROLE_USER, ROLE_ADMIN

Headers:
- X-User-Id: User ID từ token
- X-User-Role: USER hoặc ADMIN
- X-User-Email: Email của user

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "userId": "user-uuid-123",
    "fullName": "Nguyễn Văn A",
    "city": "Ho Chi Minh",
    "experience": 5,
    "position": {
      "positionId": "position-uuid",
      "trackName": "Backend Developer",
      "levelName": "SENIOR"
    }
  }
}
```

---

### 1.3. Lấy Profile theo User ID (Service-to-Service)
Endpoint: GET /profiles/{userId}

Authorization: ROLE_SERVICE (Được gọi từ Interview Service hoặc các service khác)

Path Parameters:
- userId (String, required): ID của user cần lấy thông tin

Headers:
- X-User-Id: Service ID (e.g., interview-service)
- X-User-Role: SERVICE
- X-User-Email: Service email

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "userId": "user-uuid-123",
    "fullName": "Nguyễn Văn A",
    "city": "Ho Chi Minh",
    "experience": 5,
    "position": {
      "positionId": "position-uuid",
      "trackName": "Backend Developer",
      "levelName": "SENIOR"
    }
  }
}
```

**Use Case:** Interview Service gọi API này để lấy `experience`, `trackName`, `levelName` của user để random câu hỏi phù hợp từ Question Bank.

---

### 1.4. Cập nhật Profile
Endpoint: PATCH /profiles/{userId}

Authorization: ROLE_USER (Chỉ update profile của chính mình)

Path Parameters:
- userId (String, required): ID của user cần update

Headers:
- X-User-Id: User ID từ token (phải trùng với userId trong path)
- X-User-Role: USER
- X-User-Email: Email của user

Request Body: (Tất cả fields đều optional)
```json
{
  "fullName": "Nguyễn Văn B",
  "trackId": "uuid-of-frontend-track",
  "levelId": "uuid-of-mid-level",
  "city": "Ha Noi",
  "experience": 7
}
```

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "userId": "user-uuid-123",
    "fullName": "Nguyễn Văn B",
    "city": "Ha Noi",
    "experience": 7,
    "position": {
      "positionId": "position-uuid",
      "trackName": "Frontend Developer",
      "levelName": "MID"
    }
  }
}
```

---

## 2. POSITION TRACK APIs

### 2.1. Lấy tất cả Position Tracks (Public)
Endpoint: GET /position-tracks

Authorization: Public (Không cần headers)

Note: Public endpoint chỉ trả về các tracks ở trạng thái "active". Các track inactive chỉ hiển thị cho admin.

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": [
    {
      "id": "track-uuid-1",
      "name": "Backend Developer",
      "active": true
    },
    {
      "id": "track-uuid-2",
      "name": "Frontend Developer",
      "active": true
    }
  ]
}
```

---

### 2.2. Lấy Position Track theo ID (Admin)
Endpoint: GET /admin/position-tracks/{trackId}

Authorization: ROLE_ADMIN

Headers:
- X-User-Id: Admin user ID
- X-User-Role: ADMIN
- X-User-Email: Admin email

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "id": "track-uuid",
    "name": "Backend Developer",
    "active": true
  }
}
```

---

### 2.2. Tạo Position Track (Admin)
Endpoint: POST /admin/position-tracks

Authorization: ROLE_ADMIN

Headers:
- X-User-Id: Admin user ID
- X-User-Role: ADMIN
- X-User-Email: Admin email

Request Body:
```json
{
  "name": "Backend Developer"
}
```

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "id": "track-uuid",
    "name": "Backend Developer",
    "active": true
  }
}
```

Examples:
```json
   {"name": "Backend Developer"} 
   {"name": "Frontend Developer"}
   {"name": "DevOps Engineer"}
   {"name": "Mobile Developer"}
   {"name": "Data Engineer"}
   {"name": "QA Engineer"}
```

---

### 2.3. Lấy tất cả Position Tracks cho Admin
Endpoint: GET /admin/position-tracks

Authorization: ROLE_ADMIN

Note: Admin endpoint trả về tất cả tracks kể cả "active" và "inactive".

Headers:
- X-User-Id: Admin user ID
- X-User-Role: ADMIN
- X-User-Email: Admin email

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": [
    {
      "id": "track-uuid-1",
      "name": "Backend Developer",
      "active": true
    },
    {
      "id": "track-uuid-2",
      "name": "Frontend Developer",
      "active": true
    }
  ]
}
```

---

### 2.4. Lấy Position Track theo ID cho Admin
Endpoint: GET /admin/position-tracks/{trackId}

Authorization: ROLE_ADMIN

Headers:
- X-User-Id: Admin user ID
- X-User-Role: ADMIN
- X-User-Email: Admin email

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "id": "track-uuid",
    "name": "Backend Developer",
    "active": true
  }
}
```

---

### 2.5. Cập nhật Position Track (Admin)
Endpoint: PUT /admin/position-tracks/{trackId}

Authorization: ROLE_ADMIN

Headers:
- X-User-Id: Admin user ID
- X-User-Role: ADMIN
- X-User-Email: Admin email

Request Body:
```json
{
  "name": "Full Stack Developer",
  "active": true
}
```

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "id": "track-uuid",
    "name": "Full Stack Developer",
    "active": true
  }
}
```

---

### 2.6. Toggle Active Position Track (Admin)
Endpoint: PATCH /admin/position-tracks/{trackId}/toggle

Authorization: ROLE_ADMIN

Headers:
- X-User-Id: Admin user ID
- X-User-Role: ADMIN
- X-User-Email: Admin email

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "id": "track-uuid",
    "name": "Backend Developer",
    "active": false
  }
}
```

---

### 2.7. Xóa Position Track (Admin)
Endpoint: DELETE /admin/position-tracks/{trackId}

Authorization: ROLE_ADMIN

Headers:
- X-User-Id: Admin user ID
- X-User-Role: ADMIN
- X-User-Email: Admin email

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": null
}
```

Lưu ý: Không thể xóa track đang được sử dụng bởi position

---

## 3. POSITION LEVEL APIs

### 3.1. Lấy tất cả Position Levels (Public)
Endpoint: GET /position-levels

Authorization: Public (Không cần headers)

Note: Public endpoint chỉ trả về các levels ở trạng thái "active". Các level inactive chỉ hiển thị cho admin.

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": [
    {
      "id": "level-uuid-1",
      "positionRole": "JUNIOR",
      "active": true
    },
    {
      "id": "level-uuid-2",
      "positionRole": "SENIOR",
      "active": true
    }
  ]
}
```

---

### 3.2. Lấy Position Level theo ID (Admin)
Endpoint: GET /admin/position-levels/{levelId}

Authorization: ROLE_ADMIN

Headers:
- X-User-Id: Admin user ID
- X-User-Role: ADMIN
- X-User-Email: Admin email

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "id": "level-uuid",
    "positionRole": "SENIOR",
    "active": true
  }
}
```

---

### 3.2. Tạo Position Level (Admin)
Endpoint: POST /admin/position-levels

Authorization: ROLE_ADMIN

Headers:
- X-User-Id: Admin user ID
- X-User-Role: ADMIN
- X-User-Email: Admin email

Request Body:
```json
{
  "positionRole": "SENIOR"
}
```

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "id": "level-uuid",
    "positionRole": "SENIOR",
    "active": true
  }
}
```

Các level thông dụng:
```json
{"positionRole": "INTERN"}
{"positionRole": "JUNIOR"}
{"positionRole": "MID"}
{"positionRole": "SENIOR"}
{"positionRole": "LEAD"}
{"positionRole": "MANAGER"}
{"positionRole": "DIRECTOR"}
```

---

### 3.3. Lấy tất cả Position Levels cho Admin
Endpoint: GET /admin/position-levels

Authorization: ROLE_ADMIN

Note: Admin endpoint trả về tất cả levels kể cả "active" và "inactive".

Headers:
- X-User-Id: Admin user ID
- X-User-Role: ADMIN
- X-User-Email: Admin email

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": [
    {
      "id": "level-uuid-1",
      "positionRole": "JUNIOR",
      "active": true
    },
    {
      "id": "level-uuid-2",
      "positionRole": "SENIOR",
      "active": true
    }
  ]
}
```

---

### 3.4. Lấy Position Level theo ID (Admin)
Endpoint: GET /admin/position-levels/{levelId}

Authorization: ROLE_ADMIN

Headers:
- X-User-Id: Admin user ID
- X-User-Role: ADMIN
- X-User-Email: Admin email

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "id": "level-uuid",
    "positionRole": "SENIOR",
    "active": true
  }
}
```

---

### 3.5. Cập nhật Position Level (Admin)
Endpoint: PUT /admin/position-levels/{levelId}

Authorization: ROLE_ADMIN

Headers:
- X-User-Id: Admin user ID
- X-User-Role: ADMIN
- X-User-Email: Admin email

Request Body:
```json
{
  "positionRole": "STAFF",
  "active": true
}
```

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "id": "level-uuid",
    "positionRole": "STAFF",
    "active": true
  }
}
```

---

### 3.6. Toggle Active Position Level (Admin)
Endpoint: PATCH /admin/position-levels/{levelId}/toggle

Authorization: ROLE_ADMIN

Headers:
- X-User-Id: Admin user ID
- X-User-Role: ADMIN
- X-User-Email: Admin email

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "id": "level-uuid",
    "positionRole": "SENIOR",
    "active": false
  }
}
```

---

### 3.7. Xóa Position Level (Admin)
Endpoint: DELETE /admin/position-levels/{levelId}

Authorization: ROLE_ADMIN

Headers:
- X-User-Id: Admin user ID
- X-User-Role: ADMIN
- X-User-Email: Admin email

Response:
```json
{
  "code": 1000,
  "message": "Success",
  "data": null
}
```

Lưu ý: Không thể xóa level đang được sử dụng bởi position

---

## 4. Position Entity (Internal Only)

**Position không có API riêng.** Position được tạo tự động khi user chọn track + level trong profile.

### Cách hoạt động:
1. User chọn `trackId` và `levelId` khi tạo/cập nhật profile
2. Hệ thống tự động kiểm tra và tạo Position nếu chưa tồn tại
3. Position = Track + Level (combination)
4. Position được validate: cả Track và Level phải ở trạng thái `active`

### Internal Service Methods:
- `getPositionByTrackAndLevel(trackId, levelId)`: Tìm hoặc tạo Position
- `isActivePosition(position)`: Validate Position active

**Lưu ý:** Admin quản lý Position gián tiếp thông qua PositionTrack và PositionLevel APIs.

---

## Testing

### Luồng test đầy đủ

#### Bước 1: Tạo Position Tracks
```bash
curl -X POST http://localhost:8082/api/v1/admin/position-tracks \
  -H "Content-Type: application/json" \
  -H "X-User-Id: admin-uuid" \
  -H "X-User-Role: ADMIN" \
  -H "X-User-Email: admin@mockwise.dev" \
  -d '{"name": "Backend Developer"}'
```

#### Bước 2: Tạo Position Levels
```bash
curl -X POST http://localhost:8082/api/v1/admin/position-levels \
  -H "Content-Type: application/json" \
  -H "X-User-Id: admin-uuid" \
  -H "X-User-Role: ADMIN" \
  -H "X-User-Email: admin@mockwise.dev" \
  -d '{"positionRole": "SENIOR"}'
```

#### Bước 3: Tạo User Profile (từ IAM Service)
```bash
curl -X POST http://localhost:8082/api/v1/profiles/user-uuid-123 \
  -H "Content-Type: application/json" \
  -H "X-User-Id: iam-service" \
  -H "X-User-Role: IAM" \
  -H "X-User-Email: iam@mockwise.dev" \
  -d '{
    "fullName": "Nguyễn Văn A",
    "trackId": "backend-track-uuid",
    "levelId": "senior-level-uuid",
    "city": "Ho Chi Minh",
    "experience": 5
  }'
```

#### Bước 4: Kiểm tra Profile
```bash
curl -X GET http://localhost:8082/api/v1/profiles \
  -H "X-User-Id: user-uuid-123" \
  -H "X-User-Role: USER" \
  -H "X-User-Email: user@mockwise.dev"
```

#### Bước 5: Cập nhật Profile
```bash
curl -X PATCH http://localhost:8082/api/v1/profiles/user-uuid-123 \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user-uuid-123" \
  -H "X-User-Role: USER" \
  -H "X-User-Email: user@mockwise.dev" \
  -d '{
    "fullName": "Nguyễn Văn B",
    "city": "Ha Noi",
    "experience": 7
  }'
```

---

## Setup & Run

### Prerequisites:
- Java 17+
- Maven 3.8+
- PostgreSQL 14+

### 1. Clone repository
```bash
git clone https://github.com/MinhAnh-IT/MockWise.git
cd MockWise/services/user-profile-service
```

### 2. Configure database
```sql
CREATE DATABASE user_profile_db;
```

### 3. Update application.yml
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/user_profile_db
    username: your_username
    password: your_password
```

### 4. Build & Run
```bash
mvn clean install
mvn spring-boot:run
```

Service sẽ chạy tại: http://localhost:8082

---

## Security

### Authorization Rules

| Endpoint | Method | Required Role | Note |
|----------|--------|---------------|------|
| /profiles/{userId} | POST | ROLE_IAM | Chỉ IAM service được tạo profile |
| /profiles | GET | ROLE_USER, ROLE_ADMIN | User xem profile của mình |
| /profiles/{userId} | GET | ROLE_USER, ROLE_ADMIN, ROLE_SERVICE | User/Admin/Service xem profile theo userId |
| /profiles/{userId} | PATCH | ROLE_USER | User chỉ update profile của mình |
| /position-tracks/** | GET | Public | User xem để chọn track (chỉ active) |
| /position-levels/** | GET | Public | User xem để chọn level (chỉ active) |
| /admin/position-tracks/** | POST, PUT, DELETE, PATCH, GET | ROLE_ADMIN | Admin quản lý tracks (tất cả) |
| /admin/position-levels/** | POST, PUT, DELETE, PATCH, GET | ROLE_ADMIN | Admin quản lý levels (tất cả) |

### Headers từ Gateway
- X-User-Id: User ID từ JWT token (hoặc Service ID cho service-to-service calls)
- X-User-Role: Role của user/service (IAM, USER, ADMIN, SERVICE)
- X-User-Email: Email của user/service

---

## Error Codes

| Code | Message |
|------|---------|
| 1000 | Success |
| 4000 | Bad Request |
| 4300 | Forbidden |
| 4400 | Not Found |
| 5001 | Position with code not found |
| 5002 | Position is not active |
| 5003 | Position already exists |
| 5010 | Position track not found |
| 5011 | Position track is not active |
| 5012 | Position track already exists |
| 5020 | Position level not found |
| 5021 | Position level is not active |
| 5022 | Position level already exists |
| 9000 | Internal Server Error |

---

## Validation Rules

### UserProfileRequest
- fullName: Not blank, max 100 characters
- trackId: Not blank, must exist and be active
- levelId: Not blank, must exist and be active
- city: Not blank
- experience: >= 0

### PositionTrackCreateRequest
- name: Not blank, max 100 characters, unique

### PositionLevelCreateRequest
- positionRole: Not blank, max 64 characters, unique

### PositionCreateRequest
- trackId: Not blank, must exist
- levelId: Not blank, must exist
- Combination (trackId, levelId): Must be unique

---

## Database Migration

```sql
CREATE TABLE position_tracks (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE position_levels (
    id VARCHAR(36) PRIMARY KEY,
    position_role VARCHAR(64) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE positions (
    position_id VARCHAR(36) PRIMARY KEY,
    track_id VARCHAR(36) NOT NULL,
    level_id VARCHAR(36) NOT NULL,
    FOREIGN KEY (track_id) REFERENCES position_tracks(id),
    FOREIGN KEY (level_id) REFERENCES position_levels(id),
    CONSTRAINT uk_position_track_level UNIQUE (track_id, level_id)
);

CREATE TABLE user_profiles (
    user_id VARCHAR(36) PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL,
    experience INTEGER NOT NULL,
    position_id VARCHAR(36) NOT NULL,
    FOREIGN KEY (position_id) REFERENCES positions(position_id)
);
```

---

## Tech Stack

- Framework: Spring Boot 3.x
- Language: Java 17
- Database: PostgreSQL
- ORM: Spring Data JPA + Hibernate
- Mapper: MapStruct
- Validation: Jakarta Validation
- Security: Spring Security
- Build Tool: Maven

---

## Contributors

Huỳnh Minh Anh

---

## License

MIT License
