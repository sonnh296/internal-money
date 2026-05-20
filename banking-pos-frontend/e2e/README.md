# Playwright E2E — Banking POS Frontend

## Chuẩn bị

1. Backend tối thiểu cho E2E UI:
   - **AuthUser** `8094`
   - **AccountService** `8084`
   - **CustomerService** `8083` (dashboard, profile)
   - Tùy chọn: **PaymentOrchestrator** `8086`, **BillerService** `8088`, Kafka (bill pay `BP-02`)
   - Vite proxy trỏ thẳng các service (không bắt buộc ApiGateway `8080`)

2. Tạo file credentials (không commit):

```bash
cp .env.e2e.example .env.e2e
# Điền E2E_USER1_PASSWORD và E2E_USER2_PASSWORD
```

3. Cài dependency & browser:

```bash
npm install
npx playwright install chromium
```

## Chạy test

```bash
npm run test:e2e          # headless
npm run test:e2e:ui       # Playwright UI
npm run test:e2e:report   # mở HTML report
```

## Mapping test case

File `docs/test-cases.csv` — cột `Spec_File` trỏ tới spec tương ứng. Annotation `tc-id` trên mỗi test trong report.

## Ghi chú

- `global-setup` login U1/U2 qua API, cache `e2e/.cache/users.json`.
- Transfer tests chạy **serial** và thay đổi số dư thật — dùng tài khoản test.
- `BP-02` skip nếu không có hóa đơn PENDING hoặc Kafka chưa chạy.
