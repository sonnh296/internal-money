# Banking POS Frontend — Dual-portal SPA

Hai cổng hoàn toàn tách biệt, dùng chung backend microservices:

- **`/admin/*`** – Cổng quản trị (ops console, theme dark + amber)
- **`/app/*`** – Cổng khách hàng (consumer app, theme light + teal)
- **`/`** – Landing chọn cổng

Mỗi cổng có:
- Pinia store auth riêng (`adminAuth` ↔ `userAuth`)
- localStorage key riêng (`bp_admin_session` ↔ `bp_user_session`)
- Layout, navigation, theme, login/logout riêng
- HTTP client tự chọn token theo `route.meta.portal`, không bao giờ trộn tokens

## Cấu trúc thư mục

```txt
src/
  api/                 # axios clients + service APIs
  components/          # ToastStack, RawResponseDrawer, StatTile, EmptyState
  composables/         # useApiAction (toast + raw log)
  config/              # env loader
  constants/           # routes, demo accounts, enums
  router/              # split routes + portal guards
  stores/              # adminAuth, userAuth, notify
  utils/               # storage + JWT helpers
  views/
    admin/             # admin portal (10 view)
    app/               # customer portal (8 view)
    LandingView.vue
  assets/main.css      # 2-theme design system
  App.vue / main.ts
```

## Chạy local

```sh
npm install
cp .env.example .env   # hoặc dùng proxy mặc định
npm run dev            # http://localhost:5173
```

`vite.config.js` proxy sẵn tới:
- AuthUser `8094`, CustomerService `8083`, AccountService `8084`,
  PaymentOrchestrator `8086`, BillerService `8088`.

## Cổng khách hàng (`/app/*`)

| Route | Mục đích |
|-------|----------|
| `/app/login` | Đăng nhập (POST `/auth/login`) |
| `/app/register` | Tự đăng ký, tạo customer profile (POST `/customers`) |
| `/app/dashboard` | Hồ sơ + tổng số dư + giao dịch gần đây |
| `/app/accounts` | Danh sách tài khoản |
| `/app/accounts/:id` | Chi tiết: balance / holds / transactions + nạp/rút demo |
| `/app/pay` | Thanh toán hóa đơn (BillPay, idempotency tự động) |
| `/app/billers` | Quản lý nhà cung cấp của riêng customer |
| `/app/profile` | Xem & PATCH hồ sơ với `If-Match` version |

## Cổng quản trị (`/admin/*`)

| Route | Mục đích |
|-------|----------|
| `/admin/login` | Đăng nhập admin (gate: scope `admin:users.write` hoặc customerId `admin-…`) |
| `/admin/dashboard` | Overview accounts, billers, scopes, smoke test |
| `/admin/customers` | CRUD customer + KYC + version + exists checks |
| `/admin/accounts` | Tạo account, đổi status, force credit/debit, hold/release |
| `/admin/billers` | Catalog billers + isActive |
| `/admin/edge-cases` | **Backend Testbench** chạy mọi edge case backend |
| `/admin/playground` | HTTP request tùy ý 5 service |
| `/admin/health` | Ping `/actuator/health` từng service |

## Edge cases được cover trong Testbench

Auth · sai password, unknown user, refresh garbage, secure không token, provision thiếu scope ·
Customer · trùng externalId (replay vs conflict), PATCH thiếu/If-Match sai, KYC status lạ ·
Account · idempotent create, debit insufficient, hold insufficient, hold idempotent, release sai account, ETag mismatch ·
Biller · trùng `(customerId, referenceNumber)`, limit ≤ 0 ·
Payment · executionDate quá khứ, sai format date, currency ≠ CAD, biller inactive, idempotent replay, thiếu Idempotency-Key ·
Pagination · transactions default limit · Health · public whitelist · Provisioning · upsert idempotent.

## Build production

```sh
npm run typecheck
npm run build
npm run preview
```
