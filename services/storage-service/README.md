# Storage Service

Service quản lý lưu trữ file trên MinIO cho hệ thống MockWise. Hai loại tài sản chính:

- **Interview videos** — video user trả lời trong buổi phỏng vấn. Bytes upload/download **đi thẳng giữa browser và MinIO** qua presigned URL — không bao giờ chảy qua JVM của service.
- **Question audio** — audio TTS đọc câu hỏi cho user, do tts-stt-service tạo và push vào đây.

Service không proxy bytes, mà đóng vai **người ký URL** + **lưu metadata** + **thực thi ownership/quota**.

## Mục lục

- [Kiến trúc](#kiến-trúc)
- [Flows](#flows)
- [API Documentation](#api-documentation)
- [Security Model](#security-model)
- [Setup & Run](#setup--run)
- [Error Codes](#error-codes)
- [Database Schema](#database-schema)
- [Tech Stack](#tech-stack)

---

## Kiến trúc

```
            ┌──────────────┐
            │   Browser    │
            │  (FE / SPA)  │
            └──────┬───────┘
                   │ JWT
                   ▼
            ┌──────────────┐
            │ api-gateway  │
            │  (8888)      │  /api/v1/storage/uploads/**
            └──────┬───────┘
                   │ X-User-Id, X-User-Role
                   ▼
            ┌──────────────┐
            │   storage    │  metadata: storage_object (PostgreSQL)
            │   service    │  ─────────────────► sign URLs ─────┐
            │   (8085)     │                                     │
            └──────┬───────┘                                     │
                   │ /internal/**                                ▼
                   │ X-Internal-Auth                       ┌──────────┐
                   │                                       │  MinIO   │
       ┌───────────┴──────────────┐                        │  :9000   │
       │                          │                        └──────────┘
┌─────────────┐          ┌────────────────┐                      ▲
│ interview-  │          │  tts-stt-      │                      │
│  service    │          │   service      │  push audio ─────────┘
└─────────────┘          └────────────────┘  (server-to-server)
   xin GET URL              upload audio
```

### Tách biệt 2 lớp endpoint

| Lớp | Path prefix | Auth | Reachable |
|---|---|---|---|
| **Public** | `/api/v1/storage/uploads/**` | JWT (Bearer) qua gateway | Internet → nginx → gateway → service |
| **Internal** | `/api/v1/storage/internal/**` | `X-Internal-Auth: <secret>` | Chỉ internal-net — gateway trả 404 cho mọi path chứa `/internal/` |

### Bucket layout

| Bucket | Nội dung | Object key pattern |
|---|---|---|
| `interview-videos` | Video phỏng vấn (private) | `{userId}/{sessionId}/{uuid}` |
| `question-audio` | TTS audio cho câu hỏi (private) | `audio/{uuid}` |

Bucket tự được tạo lúc startup nếu chưa có (`MinioClientConfig.@PostConstruct`).

---

## Flows

### 1. Video upload (user-facing)

```
FE                       storage-service                     MinIO
 │  POST /uploads/videos       │                              │
 │  { sessionId, contentType,  │                              │
 │    sizeBytes }              │                              │
 │────────────────────────────►│                              │
 │                             │ INSERT storage_object        │
 │                             │   status=PENDING_UPLOAD      │
 │                             │ presign PUT (TTL 10 min)     │
 │◄────────────────────────────│                              │
 │  { objectId, uploadUrl }    │                              │
 │                                                             │
 │  PUT <uploadUrl>  (bytes thẳng vào MinIO, không qua JVM)   │
 │────────────────────────────────────────────────────────────►│
 │◄────────────────────────────────────────────────────────────│
 │                                                             │
 │  POST /uploads/videos/{objectId}/complete                   │
 │────────────────────────────►│  statObject() — verify size  │
 │                             │────────────────────────────► │
 │                             │◄──────────────────────────── │
 │                             │  status=READY                 │
 │◄────────────────────────────│                              │
```

### 2. Question audio creation (admin → tts-stt → storage)

```
admin tạo question
        │
        ▼
question-bank-service (lưu text)
        │
        │ trigger TTS (Kafka event hoặc gọi trực tiếp)
        ▼
tts-stt-service (sinh audio mp3)
        │
        │ POST /internal/question-audio (multipart, X-Internal-Auth)
        ▼
storage-service
        │ upload to MinIO bucket question-audio
        │ insert storage_object (kind=QUESTION_AUDIO, READY)
        │ return { objectKey: "audio/<uuid>" }
        │
        ▼
tts-stt cập nhật question-bank.<table>.audio_key = "audio/<uuid>"
```

### 3. Question audio playback (user mở câu hỏi)

```
FE                interview-service          question-bank          storage-service          MinIO
 │  GET audio-url       │                          │                       │                  │
 │  for {sid, qid}      │                          │                       │                  │
 │─────────────────────►│ verify ACL:              │                       │                  │
 │                      │  - JWT.sub == owner      │                       │                  │
 │                      │  - session ACTIVE        │                       │                  │
 │                      │  - qid ∈ session         │                       │                  │
 │                      │                          │                       │                  │
 │                      │  GET /questions/{qid}/audio-key                  │                  │
 │                      │─────────────────────────►│                       │                  │
 │                      │◄──── audioKey ───────────│                       │                  │
 │                      │                                                  │                  │
 │                      │  POST /internal/download-url                     │                  │
 │                      │  { kind, objectKey, ttlSeconds: 60 }             │                  │
 │                      │  X-Internal-Auth: <secret>                       │                  │
 │                      │─────────────────────────────────────────────────►│ presign GET      │
 │                      │                                                  │ (cap ≤ 60s)      │
 │                      │◄────────────────────────── url, expiresAt ───────│                  │
 │◄──── { url } ────────│                                                  │                  │
 │                                                                                            │
 │  <audio src={url}>  ───────────────────────────────────────────────────────────────────────►
 │◄──── audio bytes ──────────────────────────────────────────────────────────────────────────│
```

**TTL hết hạn → FE replay lại?** FE bắt event `error` của `<audio>`, **gọi lại interview-service** để re-validate session. Nếu session đã end → 403, audio không play được nữa.

---

## API Documentation

### Base URL

```
Public  : http://localhost:8888/api/v1/storage   (qua gateway)
Direct  : http://localhost:8085/api/v1/storage   (dev only — bắt buộc cho /internal/**)
```

### 1. Health

```
GET /api/v1/storage/health
```

Public, không auth. Trả `{ "code": 1000, "data": null }`.

---

### 2. Reserve video upload (Public)

**Endpoint**: `POST /api/v1/storage/uploads/videos`

**Auth**: `Authorization: Bearer <jwt>` (ROLE_USER hoặc ROLE_ADMIN)

**Request body**:
```json
{
  "sessionId": "01JX9P2K8R3C4FZ8N7H5BWAQ12",
  "contentType": "video/webm",
  "sizeBytes": 12582912
}
```

**Allowed content-types**: `video/webm`, `video/mp4`, `video/x-matroska`, `video/quicktime`

**Limits**: `sizeBytes` ≤ 500 MB

**Response**:
```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "objectId": "0a1b2c3d-4e5f-6789-abcd-ef0123456789",
    "bucket": "interview-videos",
    "objectKey": "u-7f3a/sess-01JX9P/8f1c3a4d-9a2b-4e7f-8c1d-2a4b6c8e0f12",
    "uploadUrl": "http://minio:9000/interview-videos/u-7f3a/...?X-Amz-Signature=...",
    "expiresAt": "2026-04-25T15:30:00Z"
  }
}
```

**FE bước tiếp theo**: `PUT <uploadUrl>` thẳng vào MinIO với body là file bytes và header `Content-Type` đúng như đã khai báo.

---

### 3. Complete video upload (Public)

**Endpoint**: `POST /api/v1/storage/uploads/videos/{objectId}/complete`

**Auth**: `Authorization: Bearer <jwt>` — **chỉ owner** mới complete được.

**Body**: không có.

**Behavior**: stat object trên MinIO, verify size khớp với `sizeBytes` đã khai báo lúc reserve, flip status sang `READY`.

**Response**:
```json
{
  "code": 1000,
  "data": {
    "objectId": "0a1b2c3d-...",
    "kind": "INTERVIEW_VIDEO",
    "bucket": "interview-videos",
    "objectKey": "u-7f3a/sess-01JX9P/8f1c3a4d-...",
    "contentType": "video/webm",
    "sizeBytes": 12582912,
    "status": "READY",
    "sessionId": "01JX9P2K8R3C4FZ8N7H5BWAQ12",
    "createdAt": "2026-04-25T15:20:00Z",
    "completedAt": "2026-04-25T15:25:30Z"
  }
}
```

---

### 4. Upload question audio (Internal)

**Endpoint**: `POST /api/v1/storage/internal/question-audio`

**Auth**: `X-Internal-Auth: <INTERNAL_API_KEY>` — gateway sẽ trả 404 nếu request đến từ public.

**Caller**: tts-stt-service (server-to-server).

**Request**: `multipart/form-data`
- `questionId`: text — id câu hỏi
- `file`: binary — audio file

**Allowed content-types**: `audio/mpeg`, `audio/mp4`, `audio/wav`, `audio/ogg`, `audio/webm`

**Limits**: ≤ 25 MB

**Response**:
```json
{
  "code": 1000,
  "data": {
    "objectId": "ab12cd34-...",
    "bucket": "question-audio",
    "objectKey": "audio/8f1c3a4d-9a2b-4e7f-8c1d-2a4b6c8e0f12",
    "sizeBytes": 184320
  }
}
```

**Caller's job**: persist `objectKey` vào `question-bank` qua `PATCH .../audio-key`.

---

### 5. Issue presigned download URL (Internal)

**Endpoint**: `POST /api/v1/storage/internal/download-url`

**Auth**: `X-Internal-Auth: <INTERNAL_API_KEY>`.

**Caller**: interview-service (sau khi đã verify ACL của user).

**Request body**:
```json
{
  "kind": "QUESTION_AUDIO",
  "objectKey": "audio/8f1c3a4d-...",
  "ttlSeconds": 60
}
```

**TTL caps** (server-side, không thể vượt):
- `QUESTION_AUDIO`: max **60s**
- `INTERVIEW_VIDEO`: max **1800s** (30 phút)

**Response**:
```json
{
  "code": 1000,
  "data": {
    "url": "http://minio:9000/question-audio/audio/8f1c3a4d-...?X-Amz-...",
    "expiresAt": "2026-04-25T15:31:00Z"
  }
}
```

---

## Security Model

### Layer 1 — Gateway

- Strip mọi header `X-User-*` và `X-Internal-Auth` đến từ client public → chống header injection.
- Reject path chứa `/internal/` → trả 404 (không leak sự tồn tại).
- Introspect JWT → inject `X-User-Id/Role/Email` vào internal-net.

### Layer 2 — Service

- `InternalAuthFilter` so sánh `X-Internal-Auth` với `INTERNAL_API_KEY` → seed `ROLE_INTERNAL`.
- `UserContextFilter` đọc `X-User-*` từ gateway → seed `ROLE_USER`/`ROLE_ADMIN`.
- `SecurityConfig` map:
  - `/uploads/**` → `ROLE_USER` hoặc `ROLE_ADMIN`
  - `/internal/**` → `ROLE_INTERNAL`
  - `/health` → public

### Layer 3 — Service logic

- **Object key tự sinh**: client không kiểm soát được path → không enumerate được.
- **Ownership check khi complete**: chỉ user đã reserve mới complete được.
- **Size + content-type whitelist**: validate trước khi ký URL upload.
- **Size verification at completion**: stat MinIO, từ chối nếu size khác khai báo.
- **TTL cap per kind**: mọi presigned URL đều có upper bound thời gian sống.
- **Audit log**: mọi lần ký URL ghi log với object key + caller.

### Layer 4 — MinIO

- Bucket private (chặn anonymous).
- Service account riêng cho storage-service (không xài root).
- (Khuyến nghị) Bật SSE-S3 (đã có sẵn `MINIO_KMS_SECRET_KEY_FILE` trong infra).

---

## Setup & Run

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 14+ (database `storage`)
- MinIO instance accessible
- Một `INTERNAL_API_KEY` shared secret (sinh bằng `openssl rand -hex 32`)

### 1. Tạo database

```sql
CREATE DATABASE storage;
GRANT ALL PRIVILEGES ON DATABASE storage TO mockwise;
```

(Trên VPS hiện tại: `docker exec mockwise-infra-postgres-1 psql -U mockwise -c 'CREATE DATABASE storage;'`)

### 2. Tạo MinIO service account

```bash
mc admin user add local storage-svc <strong-password>
mc admin policy attach local readwrite --user storage-svc
# Hoặc tạo policy custom chỉ cho 2 bucket interview-videos + question-audio
```

### 3. Cấu hình `.env`

```bash
POSTGRES_HOST=your-vps-ip
POSTGRES_PORT=5432
POSTGRES_USER=mockwise
POSTGRES_PASSWORD=...

MINIO_ENDPOINT=http://your-vps-ip:9000
MINIO_PUBLIC_ENDPOINT=http://your-vps-ip:9000  # đổi sang domain khi đặt CDN trước MinIO
MINIO_ACCESS_KEY=storage-svc
MINIO_SECRET_KEY=...

INTERNAL_API_KEY=<openssl rand -hex 32>
```

### 4. Build & Run local

```bash
# từ thư mục services/
SPRING_PROFILES_ACTIVE=local mvn -pl storage-service spring-boot:run
```

Service sẽ chạy ở: `http://localhost:8085/api/v1/storage`

### 5. Build & Run via Docker

```bash
# từ thư mục project root
docker compose -f docker/docker-compose.yml up -d storage-service
```

### 6. Production deploy

CI/CD đã wire sẵn:
- Push `develop` hoặc `main` → workflow `ci.yml` build + test
- Push `main` → workflow `cd.yml` build + push GHCR + SSH deploy

Image: `ghcr.io/<owner>/mockwise_project/storage-service:latest`

---

## Error Codes

| Code | HTTP | Message |
|---|---|---|
| 1000 | 200 | Success |
| 4001 | 400 | Content type %s is not allowed for kind %s |
| 4002 | 400 | Declared size exceeds the maximum allowed for kind |
| 4011 | 401 | Missing or invalid internal API key |
| 4030 | 403 | Caller is not the owner of this storage object |
| 4040 | 404 | Storage object not found |
| 4041 | 404 | Upload was not found in the bucket — client must PUT the file before completing |
| 4090 | 409 | Storage object is already marked as READY |
| 4220 | 422 | Uploaded size does not match declared size |
| 5001 | 502 | Storage backend operation failed |

---

## Database Schema

```sql
CREATE TABLE storage_object (
    id              VARCHAR(36)  PRIMARY KEY,
    kind            VARCHAR(20)  NOT NULL,         -- INTERVIEW_VIDEO | QUESTION_AUDIO
    bucket          VARCHAR(100) NOT NULL,
    object_key      VARCHAR(500) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    size_bytes      BIGINT,
    sha256          VARCHAR(64),
    status          VARCHAR(20)  NOT NULL,         -- PENDING_UPLOAD | READY | FAILED
    owner_user_id   VARCHAR(36),
    session_id      VARCHAR(36),
    question_id     VARCHAR(36),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    UNIQUE (bucket, object_key)
);
```

Migrations chạy tự động bằng Flyway (`src/main/resources/db/migration/`).

---

## Tech Stack

- **Framework**: Spring Boot 3.5.6
- **Language**: Java 21
- **Database**: PostgreSQL 16
- **ORM**: Spring Data JPA + Hibernate 6
- **Migration**: Flyway
- **Object storage**: MinIO Java SDK 8.5.13
- **Security**: Spring Security (JWT propagated by gateway + X-Internal-Auth shared secret)
- **Validation**: Jakarta Validation
- **Build**: Maven (multi-module parent `mockwise-services`)
- **Container**: Docker (multi-stage build, eclipse-temurin:21-jre runtime)

---

## Roadmap

- [ ] Cleanup job: xoá object có `status=PENDING_UPLOAD` quá 1 giờ.
- [ ] MinIO bucket lifecycle rule cho `interview-videos` (auto-delete sau N ngày).
- [ ] Bật SSE-S3 (đã có KMS key trong infra, chưa enable trên bucket).
- [ ] Bucket notification → Kafka topic `storage.object.uploaded` → cho phép virus-scan / transcoding async.
- [ ] Metrics: số object/bucket, dung lượng theo user, presigned URL/min.
