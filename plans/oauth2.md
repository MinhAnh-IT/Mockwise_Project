Plan: Đăng nhập bằng Google & GitHub (OAuth) cho MockWise

Context

Hệ thống đã có login đầy đủ: email/password + OTP, JWT (HS256) access token + refresh
token (cookie HttpOnly), introspect tại API Gateway, register tạo cả account + user-profile.
Thứ chưa có là đăng nhập bằng mạng xã hội. Mục tiêu đợt này:

- Thêm "Đăng nhập với Google" và "Đăng nhập với GitHub" bên cạnh form hiện có.
- Tái dùng tối đa hạ tầng token sẵn có — KHÔNG đụng vào introspect/refresh/gateway-auth.
- Lần đầu đăng nhập OAuth (user mới): tạo account rồi bắt hoàn thiện profile
  (track/level/city/experience) vì Google/GitHub không cung cấp các trường này.

Luồng chọn: Authorization-Code + Backend Exchange (thống nhất 2 provider)

GitHub không có ID token (chỉ có code → access token → gọi API user), nên không thể dùng
kiểu "Google ID token verify". Dùng chung một luồng cho cả hai:

1. FE redirect user tới authorize URL của provider (redirect_uri = <FE>/auth/callback, có state chứa provider + chống CSRF).
2. Provider redirect về <FE>/auth/callback?code=...&state=....
3. FE gọi POST /api/v1/iam/auth/oauth/{provider}/exchange { code, redirectUri }.
4. Backend (server-side, dùng client secret) đổi code → token nhà cung cấp → lấy userinfo
   (Google: userinfo endpoint; GitHub: GET /user + GET /user/emails), find-or-create
   user, phát hành access token + set refresh cookie y hệt AuthService.login().
5. Backend trả { accessToken, profileCompleted }. FE: profileCompleted=false → trang
   hoàn thiện profile; ngược lại → vào home.

 ---
Backend — iam-service

1. Entity & migration (Flyway)

entity/User.java + migration mới db/migration/Vx__oauth_users.sql:
- hashPass → nullable (OAuth user không có mật khẩu). Bỏ nullable=false.
- Thêm authProvider (enum LOCAL|GOOGLE|GITHUB, default LOCAL).
- Thêm providerId (subject/id từ provider, nullable). Unique (auth_provider, provider_id).
- Thêm profileCompleted boolean default true (user cũ đã có profile → true; OAuth user mới → false).
- Mới: enums/AuthProvider.java.
- login() cần chặn user authProvider != LOCAL đăng nhập bằng password (báo "dùng nút Google/GitHub").

2. OAuthService + provider clients (mới)

- service/OAuthService.java: exchange(provider, code, redirectUri) →
    - gọi provider client lấy OAuthUserInfo(email, name, picture, providerId);
    - userRepository.findByEmail(email) — chính sách account linking (email từ provider PHẢI đã verified, nếu không thì từ chối):
        - Chưa có → tạo User(email, authProvider, providerId, isVerified=true, hashPass=null, profileCompleted=false).
        - Đã có, account đã verified (TH1) → auto-link: gắn providerId/authProvider vào account hiện có (giữ hashPass, vẫn login được cả password lẫn Google). Idempotent nếu đã
          link.
        - Đã có, account CHƯA verified (TH2 — chống pre-account-hijacking) → chiếm lại cho chủ email thật: set isVerified=true, gắn provider, xoá hashPass + bump tokenVersion (vô
          hiệu hoá mật khẩu do bên thứ ba có thể đã đặt sẵn; muốn dùng password phải "quên mật khẩu").
    - chặn user.isBlocked();
    - phát token bằng đúng tokenService.generateAccessToken/generateRefreshToken + refreshTokenService.saveWithLimit + set cookie (refactor phần này từ AuthService.login ra
      helper dùng chung issueSession(user, response)).
    - trả OAuthLoginResponse(accessToken, user.isProfileCompleted()).
- service/oauth/GoogleOAuthClient.java, GithubOAuthClient.java (RestClient/WebClient):
  token exchange + userinfo. GitHub phải lấy primary verified email từ /user/emails.
- Config trong application.yml (env vars): oauth.google.client-id/secret, oauth.github.client-id/secret + OAuthProperties.

3. Hoàn thiện profile (mới, authed)

- UserController: POST /api/v1/iam/users/me/profile (lấy X-User-Id từ gateway header) →
  UserService.completeProfile(userId, ProfileDraftRequest):
  tái dùng profileService.createProfile(userId, draft), rồi set profileCompleted=true.
  Chặn nếu đã completed.

4. Controller + DTO

- AuthController: POST /auth/oauth/{provider}/exchange → OAuthService.exchange(...).
- DTO mới: OAuthExchangeRequest(code, redirectUri), OAuthLoginResponse(accessToken, profileCompleted).

Files chính (backend)

- iam-service/.../entity/User.java, enums/AuthProvider.java (mới)
- iam-service/.../service/OAuthService.java (mới), service/oauth/*Client.java (mới)
- iam-service/.../service/AuthService.java (tách issueSession, chặn password-login cho OAuth user)
- iam-service/.../service/UserService.java (completeProfile)
- iam-service/.../controller/AuthController.java, controller/UserController.java
- iam-service/.../dto/request|response/* (mới)
- iam-service/.../common/config/OAuthProperties.java + application.yml
- iam-service/.../db/migration/Vx__oauth_users.sql (mới)

Gateway — api-gateway

filter/AuthGatewayFilter.java PUBLIC_ROUTES: thêm
POST /api/v1/iam/auth/oauth/** (exchange là public, vì chưa có token).
POST /users/me/profile KHÔNG thêm — endpoint này authed.

Frontend

- Buttons: thêm "Continue with Google/GitHub" vào pages/auth/LoginPage.tsx và
  RegisterPage.tsx → build authorize URL (client-id từ VITE_GOOGLE_CLIENT_ID /
  VITE_GITHUB_CLIENT_ID, redirect_uri=<origin>/auth/callback, state), window.location.assign.
- Trang callback mới pages/auth/OAuthCallbackPage.tsx (route /auth/callback): đọc
  code/state, gọi exchangeOAuthCode(provider, code, redirectUri), đưa token vào AuthContext;
  profileCompleted=false → /complete-profile, else loadProfile → home.
- Trang hoàn thiện profile mới pages/auth/CompleteProfilePage.tsx (ProtectedRoute): tái
  dùng phần profile-form của RegisterPage (track/level/city/experience/...), submit
  completeProfile(payload), rồi refreshProfile() → home.
- AuthContext (auth/AuthContext.tsx): thêm applyOAuthSession(accessToken) (set token ref +
  decode role; KHÔNG ép loadProfile khi chưa có profile) và tái dùng refreshProfile.
- API (api/auth.ts): exchangeOAuthCode(provider, code, redirectUri),
  completeProfile(payload); types trong types/auth.ts.
- Routes: đăng ký /auth/callback (public) và /complete-profile (protected) trong router.

Cấu hình / hạ tầng

- Tạo OAuth app: Google Cloud Console (Authorized redirect URI = <FE>/auth/callback) và
  GitHub OAuth App (Authorization callback URL giống vậy). Lấy client-id/secret.
- Env: BE OAUTH_GOOGLE_CLIENT_ID/SECRET, OAUTH_GITHUB_CLIENT_ID/SECRET;
  FE VITE_GOOGLE_CLIENT_ID, VITE_GITHUB_CLIENT_ID. Thêm vào --env-file khi deploy VPS
  (xem [[project_vps_deploy_ops]]).

  FE VITE_GOOGLE_CLIENT_ID, VITE_GITHUB_CLIENT_ID. Thêm vào --env-file khi deploy VPS
  (xem [[project_vps_deploy_ops]]).

  Verification (end-to-end)

    1. BE khởi động: mvn spring-boot:run iam-service, kiểm tra migration chạy, app boot OK
       (xem [[feedback_spring_service_runtime_gotchas]] — @Value/ObjectMapper/enum gotchas).
    2. Google mới: FE → nút Google → consent → callback → chuyển /complete-profile →
       nhập track/level/city → vào home; DB có user auth_provider=GOOGLE, profile_completed=true.
    3. Đăng nhập lại Google: vào thẳng home, không hỏi profile.
    4. GitHub mới: tương tự, dùng email primary verified.
    5. Account linking TH1: email LOCAL đã verified → login Google cùng email → auto-link (không tạo trùng), sau đó login được cả password lẫn Google.
       TH2: email LOCAL chưa verified → login Google → account bị chiếm lại (verified=true, hashPass bị xoá, tokenVersion bump); password cũ không dùng được, phải "quên mật
       khẩu".
       Email provider chưa verified → bị từ chối.
    6. Blocked user OAuth → bị chặn (StatusCode.ACCOUNT_BLOCKED).
    7. Token tái dùng: sau OAuth login, gọi 1 API protected (vd /profiles/me) → 200 qua gateway introspect; refresh cookie hoạt động khi access token hết hạn.
    8. grep file build trước deploy để tránh lỗi tag-leak ([[feedback_write_tool_content_tag_leak]]).

  Lưu ý / quyết định mở

    - Provider trả email chưa verify (GitHub) → từ chối, tránh chiếm tài khoản.
    - state chống CSRF: lưu nonce vào sessionStorage, đối chiếu ở callback.
    - Không refactor luồng OTP/local hiện có; chỉ cộng thêm.
