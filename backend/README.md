# PaperPilot Secure Payment Backend

Node.js (Express + Firebase Admin SDK) backend powering server-side Razorpay Checkout verification, Google Play Billing verification, webhook validation, membership lifecycle, and Admin Dashboard metrics.

## Pricing Plans (Authoritative Server Catalog)
- **1-Day Trial + UPI AutoPay (`TRIAL_1DAY`)**: ₹1 / 1 day (24 hours, one-time per Google account) — sets up UPI AutoPay renewing to `MONTHLY_99` or `YEARLY_999` unless turned off before trial ends
- **Monthly Premium (`MONTHLY_99`)**: ₹99 / month (30 days) — Play SKU `paperpilot_premium_monthly`
- **Yearly Premium (`YEARLY_999`)**: ₹999 / year (365 days) — Play SKU `paperpilot_premium_yearly`

## API Endpoints

| Method | Path | Auth | Purpose |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/payment/create-order` | Firebase ID Token | Creates an authoritative Razorpay order (`9900` or `99900` paise) with idempotency protection |
| `POST` | `/api/subscription/trial-autopay` | Firebase ID Token | Creates a one-time ₹1 (`100` paise) 1-day trial + UPI AutoPay subscription/order (`MONTHLY_99` or `YEARLY_999` renewal target) |
| `POST` | `/api/subscription/autopay/toggle` | Firebase ID Token | Disables active UPI AutoPay mandate cleanly while preserving remaining trial/paid access |
| `POST` | `/api/payment/verify` | Firebase ID Token | Verifies Razorpay HMAC-SHA256 signature (`order_id\|payment_id` or `payment_id\|subscription_id`), prevents duplicates, activates membership |
| `POST` | `/api/payment/cancel-order` | Firebase ID Token | Records cancelled Checkout sessions (keeps user Free with ads enabled) |
| `POST` | `/api/subscription/create` | Firebase ID Token | Creates a Razorpay recurring subscription (if plan IDs configured) or verified order |
| `POST` | `/api/subscription/cancel` | Firebase ID Token | Cancels active subscription cleanly at period end |
| `GET` | `/api/membership/status` | Firebase ID Token | Evaluates expiry server-side and returns active Premium, AutoPay, trial & AdMob suppression status |
| `GET` | `/api/payment/history` | Firebase ID Token | Returns authenticated user's payment receipts and refunds |
| `POST` | `/api/payments/google-play/verify` | Firebase ID Token | Verifies Google Play Billing subscription tokens via Android Publisher API |
| `POST` | `/api/webhooks/razorpay` | Razorpay HMAC Header | Handles `payment.captured`, `payment.failed`, `subscription.charged`, `subscription.cancelled`, `subscription.halted`, `refund.processed` |
| `GET/POST` | `/api/admin/*` | Verified Admin Only | Dashboard metrics, full transaction ledger, and refund revocation |

## Test Mode Setup (Start Here)

1. Copy `backend/.env.example` to `backend/.env` on your server (never commit `.env` or embed secrets inside the Android APK).
2. Keep `RAZORPAY_MODE=test`.
3. Generate fresh **Test API Keys** in Razorpay Dashboard → Account & Settings → API Keys, and set `RAZORPAY_KEY_ID` (`rzp_test_...`) and `RAZORPAY_KEY_SECRET` **only** in `backend/.env`.
4. Set `RAZORPAY_WEBHOOK_SECRET` in `backend/.env` and register `https://<your-backend-host>/api/webhooks/razorpay` in Razorpay Test Webhooks.
5. Deploy the backend over HTTPS (e.g., Cloud Run / Render / Railway) and set `PAYMENT_BACKEND_BASE_URL` in `app/build.gradle.kts` to your deployed HTTPS URL.

## Live Mode Transition Checklist (Manual — Never Auto-Switched)

Switch to Live Mode **only** after completing every item below:

1. **Merchant KYC Verification**: Complete Razorpay account activation & bank KYC verification.
2. **Generate Live Keys**: Switch Razorpay Dashboard to **Live Mode** and generate a Live Key ID (`rzp_live_...`) and Live Key Secret.
3. **Update Backend Secrets Only**: In `backend/.env`, set:
   - `RAZORPAY_MODE=live`
   - `RAZORPAY_KEY_ID=rzp_live_...`
   - `RAZORPAY_KEY_SECRET=<your_live_secret>` *(server-side ONLY — never in Android code)*
   - `RAZORPAY_WEBHOOK_SECRET=<your_live_webhook_secret>`
4. **Configure Production Webhook**: Point Live Webhooks to `https://<your-production-backend>/api/webhooks/razorpay` for `payment.captured`, `payment.failed`, `subscription.charged`, and `refund.processed`.
5. **Test Real Payment & Verify Settlement**: Complete a live ₹99 test transaction, confirm server signature verification, receipt generation, AdMob removal, and verify settlement in your Razorpay Settlements / bank statement.

## Run Locally & Test
```bash
cd backend
npm install
npm test
npm start
```
