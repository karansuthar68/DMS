# Document Management System (DMS)

A Spring Boot **monolithic** REST API to upload, store, organize, search, view, download and manage documents
(PDF, Word, Excel, PowerPoint and images), secured with **Spring Security + JWT**, role-based access
(**ADMIN / USER**), per-document sharing, and **domain restriction**.

---

## 1. Tech stack

| Area          | Choice                                                         |
|---------------|----------------------------------------------------------------|
| Language      | Java 21 (also runs on newer JDKs)                              |
| Framework     | Spring Boot 4.1 (Spring MVC, Spring Data JPA, Spring Security 7) |
| Auth          | Stateless JWT (jjwt 0.12), BCrypt password hashing             |
| Database      | PostgreSQL (in-memory H2 is used only by the automated tests) |
| File storage  | Local disk (`./uploads`) behind a `FileStorageService` interface |
| Build / tests | Maven wrapper, JUnit 5 + MockMvc integration tests             |

---

## 2. Run it locally

Prerequisites:
- **JDK 21 or newer**. Maven is not needed because the wrapper downloads it.
- **PostgreSQL** running on `localhost:5432` with a database named `dms`. Hibernate creates the tables
  on first start.

```bash
psql -U postgres -c "CREATE DATABASE dms;"   # one-time setup
./mvnw spring-boot:run
```

The default connection is `postgres` / `postgres`. Override it with environment variables if yours differs:

```bash
DB_URL=jdbc:postgresql://localhost:5432/dms DB_USERNAME=myuser DB_PASSWORD=secret ./mvnw spring-boot:run
```

The API starts on **http://localhost:8080**. On first start three demo users are created:

| Username | Password    | Role  |
|----------|-------------|-------|
| `admin`  | `Admin@123` | ADMIN |
| `john`   | `John@1234` | USER  |
| `jane`   | `Jane@1234` | USER  |

Other useful commands:

```bash
./mvnw test        # run the 15 integration tests (in-memory H2, PostgreSQL not needed)
psql -U postgres -d dms -c "\dt"   # list the tables Hibernate created
```

- **Postman:** import `postman/DMS.postman_collection.json`. The login requests save the tokens automatically.
- **Reset everything:** stop the app, run `DROP DATABASE dms; CREATE DATABASE dms;`, and delete the `uploads/` folder.
- **Why tests use H2:** each test run gets a fresh, isolated database, so tests are fast, repeatable and
  never touch the real PostgreSQL data. The data layer is pure JPA, so the same code runs on both.

---

## 3. Project structure

The code is layered, and each package has one responsibility:

```
com.assessment.dms
├── config/        SecurityConfig (filter chain, CORS, password encoder), AppProperties, DataInitializer
├── controller/    REST endpoints only: map HTTP <-> DTOs and delegate to services
├── service/       Business logic and transactions
│     ├── DocumentService            upload / search / view / download / update / delete
│     ├── DocumentAccessService      ALL document authorization rules in one place
│     ├── DocumentPermissionService  share / unshare documents
│     ├── UserService, AuthService
├── storage/       File handling: FileStorageService (interface), LocalFileStorageService,
│                  FileValidator, SupportedFileType (whitelist)
├── security/      JwtService, JwtAuthenticationFilter, DomainRestrictionFilter,
│                  UserPrincipal, CustomUserDetailsService, SecurityErrorResponseWriter
├── repository/    Spring Data JPA repositories
├── entity/        JPA entities: User, Document, DocumentPermission, Role, AccessLevel
├── dto/           Request/response records (entities are never returned directly)
└── exception/     Custom exceptions, GlobalExceptionHandler, ErrorResponse
```

How a request flows through the app:
`DomainRestrictionFilter → CorsFilter → JwtAuthenticationFilter → URL authorization → Controller → Service → Repository / Storage`

---

## 4. Database design

Hibernate creates the tables from the entities (`ddl-auto=update`).

**users** stores user information.

| Column       | Type         | Notes                    |
|--------------|--------------|--------------------------|
| id           | BIGINT PK    | identity                 |
| username     | VARCHAR(50)  | unique, not null         |
| email        | VARCHAR(100) | unique, not null         |
| password     | VARCHAR(255) | BCrypt hash              |
| full_name    | VARCHAR(100) |                          |
| role         | VARCHAR(20)  | `ADMIN` / `USER`         |
| enabled      | BOOLEAN      | disabled users cannot log in |
| created_at / updated_at | TIMESTAMP |                |

**documents** stores document details and file information.

| Column             | Type          | Notes                                         |
|--------------------|---------------|-----------------------------------------------|
| id                 | BIGINT PK     |                                               |
| title              | VARCHAR(150)  | not null (defaults to the file name)          |
| description        | VARCHAR(1000) |                                               |
| category           | VARCHAR(100)  | used to organize documents (HR, Finance, ...) |
| original_file_name | VARCHAR(255)  | shown to users and used for downloads         |
| stored_file_name   | VARCHAR(100)  | unique, UUID name on disk                     |
| content_type       | VARCHAR(150)  | MIME type taken from the whitelist            |
| file_extension     | VARCHAR(10)   |                                               |
| file_size          | BIGINT        | bytes                                         |
| owner_id           | BIGINT FK → users.id | indexed                                |
| created_at / updated_at | TIMESTAMP |                                            |

**document_metadata** stores custom key/value metadata, for example `department=HR` or `version=2`.

| Column      | Type                     |
|-------------|--------------------------|
| document_id | BIGINT FK → documents.id |
| meta_key    | VARCHAR(100)             |
| meta_value  | VARCHAR(500)             |

**document_permissions** records which users a document has been shared with.

| Column       | Type                     | Notes                                 |
|--------------|--------------------------|---------------------------------------|
| id           | BIGINT PK                |                                       |
| document_id  | BIGINT FK → documents.id | ON DELETE CASCADE                     |
| user_id      | BIGINT FK → users.id     | ON DELETE CASCADE, indexed            |
| access_level | VARCHAR(20)              | `VIEW` or `DOWNLOAD`                  |
| granted_at   | TIMESTAMP                |                                       |
|              |                          | UNIQUE (document_id, user_id)         |

---

## 5. Security

### Authentication (JWT)
1. `POST /api/auth/login` checks the credentials through Spring's `AuthenticationManager`, which compares them
   against the BCrypt hash.
2. If they match, `JwtService` issues a signed HS256 token that holds the username, the role and an expiry
   time (60 minutes by default).
3. The client sends the token on every request as `Authorization: Bearer <token>`.
4. `JwtAuthenticationFilter` verifies the signature and expiry, loads the user from the database and puts it
   into the `SecurityContext`. Loading the user on each request means a **disabled user or a role change
   takes effect immediately**, even if the user still holds an old token.

The API is stateless, with no HTTP session or cookies, so CSRF protection is disabled.

### Roles and API permissions

| Endpoint group              | Anonymous | USER | ADMIN |
|-----------------------------|:---------:|:----:|:-----:|
| `/api/auth/**`              | ✅        | ✅   | ✅    |
| `/api/documents/**`         | ❌ 401    | ✅ (subject to document rules below) | ✅ all documents |
| `/api/users/me`             | ❌ 401    | ✅   | ✅    |
| `/api/admin/**` (users, settings) | ❌ 401 | ❌ 403 | ✅  |

Admin endpoints are protected twice: by the URL rule in `SecurityConfig`, and by `@PreAuthorize("hasRole('ADMIN')")`
on the controllers.

### Document access control
`DocumentAccessService` holds every document rule:

| Who                       | View details / preview | Download | Update / replace / delete / share |
|---------------------------|:----:|:----:|:----:|
| ADMIN                     | ✅ | ✅ | ✅ |
| Owner                     | ✅ | ✅ | ✅ |
| User granted `VIEW`       | ✅ | ❌ | ❌ |
| User granted `DOWNLOAD`   | ✅ | ✅ | ❌ |
| Anyone else               | ❌ 403 | ❌ 403 | ❌ 403 |

The list and search endpoint (`GET /api/documents`) filters in the database query itself, so a user only ever
sees documents they own or that were shared with them.

---

## 6. Domain restriction

The approved domains are set in `application.properties`:

```properties
app.domain-restriction.enabled=true
app.domain-restriction.allowed-domains=localhost,127.0.0.1,*.mycompany.com
```

They are enforced at three levels:

1. **Host header check** in `DomainRestrictionFilter`. The request must be addressed to an approved domain.
   This blocks direct access by raw IP or through an unknown domain pointed at the server.
   For example, `curl -H "Host: attacker.example" ...` returns 403.
2. **Origin header check** in the same filter. Browser requests coming from a website that is not approved
   are rejected with 403 before authentication runs.
3. **CORS**, configured in `SecurityConfig`. Browsers on the approved domains, on any port and over http or
   https, may call the API, and preflight requests are answered correctly.

A `*.mycompany.com` entry matches any sub-domain, such as `portal.mycompany.com`.

> **Interview note:** Non-browser clients can forge headers, so in production this filter is one layer.
> It sits alongside network-level controls: a reverse proxy or API gateway (Nginx, AWS ALB) that only routes
> approved hostnames, plus firewall or IP allow-lists. Behind a proxy, set
> `server.forward-headers-strategy=framework` so the original host is used.

---

## 7. File handling

- **Allowed types:** `pdf, doc, docx, xls, xlsx, ppt, pptx, jpg, jpeg, png, gif`, defined in the
  `SupportedFileType` enum.
- **Validation:** `FileValidator` rejects empty files, files without a name and unsupported extensions with
  `400`. The stored MIME type comes from the whitelist, **not from the client's `Content-Type`**.
- **Size limit:** 10 MB, set by `spring.servlet.multipart.max-file-size`. Larger files get a clean `413` JSON
  response.
- **Safe storage:** files are saved as `<UUID>.<ext>`, so user input never decides the path on disk. The
  original name is kept only for display and downloads. The storage service also guards against path traversal.
- **Consistency:** if saving the database row fails, the file just written is deleted. When a file is
  replaced, the old file is removed only after the database points to the new one.
- **Swappable storage:** services depend on the `FileStorageService` interface, so an S3 implementation could
  replace `LocalFileStorageService` without changing them.

---

## 8. Error handling

Every error, whether from controllers, services or security filters, has the same JSON shape:

```json
{
  "timestamp": "2026-10-27T11:42:02.800888",
  "status": 400,
  "error": "Bad Request",
  "message": "File type '.exe' is not supported. Allowed types: pdf, doc, docx, ...",
  "path": "/api/documents"
}
```

| Situation                                        | Status |
|--------------------------------------------------|--------|
| Validation failure (adds a `validationErrors` map) | 400  |
| Invalid / unsupported / empty file, bad sort field, business rule violation | 400 |
| Malformed JSON, wrong parameter type (`/documents/abc`) | 400 |
| Missing / invalid / expired token, wrong password, disabled account | 401 |
| Not allowed (role, document permission, unapproved domain) | 403 |
| Document / user / endpoint not found             | 404    |
| Wrong HTTP method / content type                 | 405 / 415 |
| Duplicate username or email                      | 409    |
| File larger than 10 MB                           | 413    |
| Storage failure / unexpected error (logged, details hidden from client) | 500 |

`GlobalExceptionHandler` (`@RestControllerAdvice`) handles errors raised in controllers.
Errors raised inside security filters never reach the advice, so `SecurityErrorResponseWriter` writes the
same JSON for them.

---

## 9. API reference

| Method | Endpoint                                       | Description                                   |
|--------|------------------------------------------------|-----------------------------------------------|
| POST   | `/api/auth/register`                           | Self-register (always role USER)              |
| POST   | `/api/auth/login`                              | Get a JWT                                     |
| GET    | `/api/users/me`                                | Current user profile                          |
| POST   | `/api/documents` *(multipart)*                 | Upload 1–10 files: `files` (repeat the key per file), `title` (single file only), `description`, `category`. Returns a list; all-or-nothing |
| GET    | `/api/documents?keyword=&page=0&size=10&sort=createdAt,desc` | Search / list accessible documents |
| GET    | `/api/documents/{id}`                          | Document details + metadata                   |
| GET    | `/api/documents/{id}/view`                     | Preview file inline (VIEW)                    |
| GET    | `/api/documents/{id}/download`                 | Download file (DOWNLOAD)                      |
| PUT    | `/api/documents/{id}`                          | Update title, description, category, metadata |
| PUT    | `/api/documents/{id}/file` *(multipart)*       | Replace the file with a new version           |
| DELETE | `/api/documents/{id}`                          | Delete document and file                      |
| GET    | `/api/documents/{id}/permissions`              | Who the document is shared with               |
| POST   | `/api/documents/{id}/permissions`              | Share: `{"username":"jane","accessLevel":"VIEW"}` (also changes level) |
| DELETE | `/api/documents/{id}/permissions/{userId}`     | Revoke access                                 |
| GET    | `/api/admin/users`                             | List users *(ADMIN)*                          |
| GET    | `/api/admin/users/{id}`                        | Get user *(ADMIN)*                            |
| POST   | `/api/admin/users`                             | Create user with any role *(ADMIN)*           |
| PUT    | `/api/admin/users/{id}`                        | Change email, name, role, enable/disable *(ADMIN)* |
| DELETE | `/api/admin/users/{id}`                        | Delete user without documents *(ADMIN)*       |
| GET    | `/api/admin/settings`                          | View application settings *(ADMIN)*           |

Sortable fields: `title, category, originalFileName, fileSize, createdAt, updatedAt`.

---

## 10. Quick demo with curl

```bash
# 1. Log in as john and as jane
JOHN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"john","password":"John@1234"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
JANE=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"jane","password":"Jane@1234"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

# 2. john uploads a document (repeat -F files=@... to upload several at once)
curl -X POST localhost:8080/api/documents -H "Authorization: Bearer $JOHN" \
  -F files=@/path/to/report.pdf -F title="Q3 Report" -F category=Finance

# 3. jane cannot see it yet -> 403
curl -i localhost:8080/api/documents/1 -H "Authorization: Bearer $JANE"

# 4. john shares it with jane (view only)
curl -X POST localhost:8080/api/documents/1/permissions -H "Authorization: Bearer $JOHN" \
  -H 'Content-Type: application/json' -d '{"username":"jane","accessLevel":"VIEW"}'

# 5. jane can view (200) but not download (403)
curl -i localhost:8080/api/documents/1/view     -H "Authorization: Bearer $JANE"
curl -i localhost:8080/api/documents/1/download -H "Authorization: Bearer $JANE"

# 6. Unsupported file type -> 400, request from unapproved domain -> 403
curl -X POST localhost:8080/api/documents -H "Authorization: Bearer $JOHN" -F file=@/path/to/app.exe
curl -i localhost:8080/api/auth/login -H 'Origin: https://evil-site.com' \
  -H 'Content-Type: application/json' -d '{"username":"john","password":"John@1234"}'
```

---

## 11. Design decisions (talking points)

- **Monolith with layered packages:** simple to build, deploy and reason about for this scope. The service
  boundaries (documents, users, storage) could later be split out if needed.
- **DTOs instead of entities in responses:** prevents leaking fields such as the password hash or the stored
  file name, and avoids lazy-loading and JSON recursion problems. `open-in-view` is disabled, so all data is
  loaded inside service transactions.
- **One class for document authorization (`DocumentAccessService`):** the rules are easy to review and test,
  and no controller can forget a check.
- **403 vs 404 for documents you cannot access:** this project returns 403 because it is clearer to explain.
  A stricter system might return 404 so it does not reveal that the document exists.
- **Interface only where swapping is realistic:** `FileStorageService` has an interface because local and
  cloud storage are real alternatives. The other services are concrete classes to avoid empty
  `Service/ServiceImpl` pairs.
- **Users are disabled rather than deleted when they own documents:** this keeps referential integrity. Shared
  permissions are cleaned up automatically by `ON DELETE CASCADE`.
- **N+1 avoided** with `hibernate.default_batch_fetch_size`. Indexes exist on `owner_id` and
  `document_permissions.user_id`, which are the columns the access query filters on.

## 12. Possible improvements

- Content sniffing (Apache Tika) to verify that the file bytes match the extension, plus virus scanning.
- Document versioning history instead of replacing the file.
- Refresh tokens and token revocation.
- Flyway migrations instead of `ddl-auto=update` for production schema changes.
- Audit log of who viewed or downloaded what, and soft delete with a recycle bin.
- OpenAPI / Swagger UI documentation.
