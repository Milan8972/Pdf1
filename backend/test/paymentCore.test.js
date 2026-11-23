import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  DAY_MS,
  PaymentServiceEngine,
  PLAN_CATALOG,
  evaluateMembershipState,
  validateRazorpayModeKey,
  verifyRazorpaySubscriptionSignature,
  verifyRazorpayWebhookSignature,
} from '../src/paymentCore.js';

const TEST_KEY_SECRET = 'rzp_test_unit_secret_only_for_tests';
const TEST_WEBHOOK_SECRET = 'whsec_test_unit_secret_only_for_tests';

function createMemoryStore() {
  const users = new Map();
  const orders = new Map();
  const payments = new Map();
  const memberships = new Map();
  const refunds = new Map();

  return {
    users,
    orders,
    payments,
    memberships,
    refunds,
    seedUser(userId, email) {
      users.set(userId, { userId, email, planTier: 'FREE', membershipStatus: 'NONE' });
    },
    async findOrderByIdempotencyKey(userId, idempotencyKey) {
      return [...orders.values()].find((o) => o.userId === userId && o.idempotencyKey === idempotencyKey) || null;
    },
    async saveOrder(order) {
      orders.set(order.orderId, { ...order });
    },
    async getOrder(orderId) {
      return orders.get(orderId) || null;
    },
    async updateOrderStatus(orderId, status, nowMs) {
      const o = orders.get(orderId);
      if (o) orders.set(orderId, { ...o, status, updatedAtMs: nowMs });
    },
    async findPaymentByTransactionId(txId) {
      return [...payments.values()].find((p) => p.transactionId === txId) || null;
    },
    async savePayment(p) {
      payments.set(p.paymentId, { ...p });
    },
    async getPayment(paymentId) {
      return payments.get(paymentId) || null;
    },
    async updatePaymentStatus(paymentId, status, nowMs) {
      const p = payments.get(paymentId);
      if (p) payments.set(paymentId, { ...p, status, updatedAtMs: nowMs });
    },
    async getMembershipByUser(userId) {
      return memberships.get(`mem_${userId}`) || null;
    },
    async saveMembership(m) {
      memberships.set(m.membershipId, { ...m });
    },
    async syncUserMembershipSummary(userId, m, nowMs) {
      const prev = users.get(userId) || { userId };
      const active = (m.status === 'ACTIVE' || m.status === 'CANCELLED') && m.expiryDateMs > nowMs;
      users.set(userId, {
        ...prev,
        planTier: active ? m.planTier : 'FREE',
        priceInr: active ? m.amountInr : 0,
        membershipStatus: m.status,
        premiumUntilEpochMs: active ? m.expiryDateMs : 0,
      });
    },
    async saveRefund(r) {
      refunds.set(r.refundId, { ...r });
    },
    async findRefundByPaymentId(paymentId) {
      return [...refunds.values()].find((r) => r.paymentId === paymentId) || null;
    },
    async listAllUsers() {
      return [...users.values()];
    },
    async listAllMemberships() {
      return [...memberships.values()];
    },
    async listAllPayments() {
      return [...payments.values()];
    },
    async listAllRefunds() {
      return [...refunds.values()];
    },
  };
}

function signRazorpay(orderId, paymentId) {
  return crypto.createHmac('sha256', TEST_KEY_SECRET).update(`${orderId}|${paymentId}`).digest('hex');
}

test('1 & 3 & 11. Monthly ₹99 purchase succeeds only after HMAC signature verification and disables ads', async () => {
  const store = createMemoryStore();
  store.seedUser('u_monthly', 'monthly@gmail.com');
  const engine = new PaymentServiceEngine(store, { razorpayKeySecret: TEST_KEY_SECRET });
  const now = 1_750_000_000_000;

  assert.equal(PLAN_CATALOG.TRIAL_1DAY.amountInr, 1);
  assert.equal(PLAN_CATALOG.TRIAL_1DAY.amountPaise, 100);
  assert.equal(PLAN_CATALOG.MONTHLY_99.amountInr, 99);
  assert.equal(PLAN_CATALOG.YEARLY_999.amountInr, 999);
  assert.equal(Object.keys(PLAN_CATALOG).length, 3);

  const { order } = await engine.createOrder({
    userId: 'u_monthly',
    planTier: 'MONTHLY_99',
    paymentProvider: 'RAZORPAY',
    idempotencyKey: 'idem_mo_1',
    nowMs: now,
  });
  assert.equal(order.amountInr, 99);
  assert.equal(order.amountPaise, 9900);

  const paymentId = 'pay_rzp_mo_001';
  const sig = signRazorpay(order.providerOrderId, paymentId);
  const res = await engine.verifyAndActivateRazorpay({
    userId: 'u_monthly',
    orderId: order.orderId,
    razorpayOrderId: order.providerOrderId,
    razorpayPaymentId: paymentId,
    razorpaySignature: sig,
    paymentMethod: 'UPI (Google Pay)',
    nowMs: now,
  });

  assert.equal(res.verified, true);
  assert.equal(res.payment.status, 'SUCCESS');
  assert.equal(res.payment.amountInr, 99);
  assert.ok(res.payment.receiptNumber.startsWith('PP-MO-'));

  const evalState = evaluateMembershipState(res.membership, now + 1000);
  assert.equal(evalState.isPremiumActive, true);
  assert.equal(evalState.adsEnabled, false);
});

test('2. Yearly ₹999 Google Play Billing purchase verifies server-side and grants 365 days', async () => {
  const store = createMemoryStore();
  store.seedUser('u_yearly', 'yearly@gmail.com');
  const engine = new PaymentServiceEngine(store);
  const now = 1_750_000_000_000;

  const res = await engine.verifyAndActivateGooglePlay({
    userId: 'u_yearly',
    productId: 'paperpilot_premium_yearly',
    purchaseToken: 'gp_token_yearly_001',
    orderId: 'GPA.1234-5678-9012',
    playVerificationFn: async () => ({
      valid: true,
      expiryTimeMillis: now + 365 * DAY_MS,
      autoRenewing: true,
      orderId: 'GPA.1234-5678-9012',
    }),
    nowMs: now,
  });

  assert.equal(res.verified, true);
  assert.equal(res.payment.amountInr, 999);
  assert.equal(res.membership.planTier, 'YEARLY_999');
  assert.equal(res.membership.autoRenew, true);
  assert.equal(res.membership.expiryDateMs, now + 365 * DAY_MS);
});

test('4. Failed / tampered payment signature is rejected and NEVER activates membership', async () => {
  const store = createMemoryStore();
  store.seedUser('u_fail', 'fail@gmail.com');
  const engine = new PaymentServiceEngine(store, { razorpayKeySecret: TEST_KEY_SECRET });
  const { order } = await engine.createOrder({
    userId: 'u_fail',
    planTier: 'MONTHLY_99',
    paymentProvider: 'RAZORPAY',
    idempotencyKey: 'idem_fail_1',
  });

  const res = await engine.verifyAndActivateRazorpay({
    userId: 'u_fail',
    orderId: order.orderId,
    razorpayOrderId: order.providerOrderId,
    razorpayPaymentId: 'pay_tampered_001',
    razorpaySignature: '0'.repeat(64),
  });

  assert.equal(res.verified, false);
  assert.equal(res.payment.status, 'FAILED');
  assert.equal(res.membership, null);
  assert.equal(await store.getMembershipByUser('u_fail'), null);
});

test('5. Cancelled payment records CANCELLED status and keeps user FREE with ads enabled', async () => {
  const store = createMemoryStore();
  store.seedUser('u_cancel', 'cancel@gmail.com');
  const engine = new PaymentServiceEngine(store);
  const { order } = await engine.createOrder({
    userId: 'u_cancel',
    planTier: 'YEARLY_999',
    paymentProvider: 'RAZORPAY',
    idempotencyKey: 'idem_can_1',
  });

  const cancelled = await engine.cancelOrder({
    userId: 'u_cancel',
    orderId: order.orderId,
    reason: 'User dismissed checkout sheet',
  });

  assert.equal(cancelled.status, 'CANCELLED');
  assert.equal(evaluateMembershipState(await store.getMembershipByUser('u_cancel')).adsEnabled, true);
});

test('6. Duplicate order & duplicate transaction replay are prevented cleanly', async () => {
  const store = createMemoryStore();
  store.seedUser('u_dup', 'dup@gmail.com');
  const engine = new PaymentServiceEngine(store, { razorpayKeySecret: TEST_KEY_SECRET });
  const now = 1_750_000_000_000;

  const first = await engine.createOrder({
    userId: 'u_dup',
    planTier: 'MONTHLY_99',
    paymentProvider: 'RAZORPAY',
    idempotencyKey: 'same_key',
    nowMs: now,
  });
  const second = await engine.createOrder({
    userId: 'u_dup',
    planTier: 'MONTHLY_99',
    paymentProvider: 'RAZORPAY',
    idempotencyKey: 'same_key',
    nowMs: now + 500,
  });
  assert.equal(second.duplicate, true);
  assert.equal(second.order.orderId, first.order.orderId);

  const txId = 'pay_rzp_dup_1';
  const sig = signRazorpay(first.order.providerOrderId, txId);
  const pay1 = await engine.verifyAndActivateRazorpay({
    userId: 'u_dup',
    orderId: first.order.orderId,
    razorpayOrderId: first.order.providerOrderId,
    razorpayPaymentId: txId,
    razorpaySignature: sig,
    nowMs: now,
  });
  const pay2 = await engine.verifyAndActivateRazorpay({
    userId: 'u_dup',
    orderId: first.order.orderId,
    razorpayOrderId: first.order.providerOrderId,
    razorpayPaymentId: txId,
    razorpaySignature: sig,
    nowMs: now + 5000,
  });

  assert.equal(pay1.duplicate, false);
  assert.equal(pay2.duplicate, true);
  assert.equal((await store.listAllPayments()).length, 1);
});

test('7 & 8 & 9 & 10 & 12. Renewal extends expiry, expiry & refund restore ads, restore reconciles, and admin metrics tally accurately', async () => {
  const store = createMemoryStore();
  store.seedUser('u1', 'u1@gmail.com');
  store.seedUser('u2', 'u2@gmail.com');
  const engine = new PaymentServiceEngine(store, {
    razorpayKeySecret: TEST_KEY_SECRET,
    razorpayWebhookSecret: TEST_WEBHOOK_SECRET,
  });
  const t0 = 1_750_000_000_000;

  // Initial monthly purchase for u1
  const { order: o1 } = await engine.createOrder({
    userId: 'u1',
    planTier: 'MONTHLY_99',
    paymentProvider: 'RAZORPAY',
    idempotencyKey: 'u1_1',
    nowMs: t0,
  });
  const p1 = await engine.verifyAndActivateRazorpay({
    userId: 'u1',
    orderId: o1.orderId,
    razorpayOrderId: o1.providerOrderId,
    razorpayPaymentId: 'tx_u1_1',
    razorpaySignature: signRazorpay(o1.providerOrderId, 'tx_u1_1'),
    nowMs: t0,
  });

  // Renewal before expiry extends by +30 days
  const { order: o2 } = await engine.createOrder({
    userId: 'u1',
    planTier: 'MONTHLY_99',
    paymentProvider: 'RAZORPAY',
    idempotencyKey: 'u1_2',
    nowMs: t0 + 10 * DAY_MS,
  });
  const p2 = await engine.verifyAndActivateRazorpay({
    userId: 'u1',
    orderId: o2.orderId,
    razorpayOrderId: o2.providerOrderId,
    razorpayPaymentId: 'tx_u1_2',
    razorpaySignature: signRazorpay(o2.providerOrderId, 'tx_u1_2'),
    nowMs: t0 + 10 * DAY_MS,
  });
  assert.equal(p2.membership.expiryDateMs, t0 + 60 * DAY_MS);

  // Restore while active stays active
  const restoredActive = await engine.restoreMembership({ userId: 'u1', nowMs: t0 + 15 * DAY_MS });
  assert.equal(restoredActive.state.isPremiumActive, true);
  assert.equal(restoredActive.state.adsEnabled, false);

  // Expiry after 61 days returns user to FREE and restores ads
  const restoredExpired = await engine.restoreMembership({ userId: 'u1', nowMs: t0 + 61 * DAY_MS });
  assert.equal(restoredExpired.state.isPremiumActive, false);
  assert.equal(restoredExpired.state.effectiveStatus, 'EXPIRED');
  assert.equal(restoredExpired.state.adsEnabled, true);

  // Yearly purchase for u2, then Refund revokes membership and restores ads
  const pYearly = await engine.verifyAndActivateGooglePlay({
    userId: 'u2',
    productId: 'paperpilot_premium_yearly',
    purchaseToken: 'gp_u2_yr',
    playVerificationFn: async () => ({ valid: true, expiryTimeMillis: t0 + 365 * DAY_MS, autoRenewing: true }),
    nowMs: t0,
  });
  assert.equal(evaluateMembershipState(pYearly.membership, t0 + DAY_MS).adsEnabled, false);

  const refundRes = await engine.processRefund({
    paymentId: pYearly.payment.paymentId,
    reason: 'Customer requested refund within 48h',
    nowMs: t0 + 2 * DAY_MS,
  });
  assert.equal(refundRes.duplicate, false);
  const afterRefund = evaluateMembershipState(await store.getMembershipByUser('u2'), t0 + 3 * DAY_MS);
  assert.equal(afterRefund.isPremiumActive, false);
  assert.equal(afterRefund.effectiveStatus, 'REFUNDED');
  assert.equal(afterRefund.adsEnabled, true);

  // Webhook signature verification
  const rawPayload = JSON.stringify({ event: 'refund.processed', payload: {} });
  const whSig = crypto.createHmac('sha256', TEST_WEBHOOK_SECRET).update(rawPayload).digest('hex');
  assert.equal(verifyRazorpayWebhookSignature({ rawBody: rawPayload, signature: whSig, webhookSecret: TEST_WEBHOOK_SECRET }), true);
  assert.equal(verifyRazorpayWebhookSignature({ rawBody: rawPayload, signature: 'bad', webhookSecret: TEST_WEBHOOK_SECRET }), false);

  // Admin Dashboard metrics
  const metrics = await engine.computeAdminDashboard(t0 + 61 * DAY_MS);
  assert.equal(metrics.totalRegisteredUsers, 2);
  assert.equal(metrics.expiredMemberships, 1);
  assert.equal(metrics.successfulPayments, 2); // u1 x2 (u2 was refunded)
  assert.equal(metrics.refundsCount, 1);
  assert.equal(metrics.monthlyRevenueInr, 198);
  assert.equal(metrics.totalGrossRevenueInr, 198);
  assert.ok(p1.payment.paymentId);
});

test('13. Test Mode vs Live Mode key guard blocks mismatched keys and never auto-switches to Live Mode', () => {
  assert.equal(validateRazorpayModeKey('test', 'rzp_test_sample123').valid, true);
  assert.equal(validateRazorpayModeKey('test', 'rzp_live_sample123').valid, false);
  assert.equal(validateRazorpayModeKey('live', 'rzp_test_sample123').valid, false);
  assert.equal(validateRazorpayModeKey('live', 'rzp_live_sample123').valid, true);
  assert.equal(validateRazorpayModeKey('test', 'rzp_test_replace_with_your_key_id').valid, false);
  assert.equal(validateRazorpayModeKey('test', '').valid, false);
});

test('14. Per-user payment history & Razorpay recurring subscription signature verification work securely', async () => {
  const store = createMemoryStore();
  store.seedUser('u_sub', 'sub@gmail.com');
  store.seedUser('u_other', 'other@gmail.com');
  const engine = new PaymentServiceEngine(store, { razorpayKeySecret: TEST_KEY_SECRET });
  const t0 = 1_750_000_000_000;

  const { order } = await engine.createOrder({
    userId: 'u_sub',
    planTier: 'YEARLY_999',
    paymentProvider: 'RAZORPAY',
    idempotencyKey: 'idem_sub_1',
    nowMs: t0,
  });

  const subId = 'sub_test_yearly_001';
  const payId = 'pay_sub_yearly_001';
  const subSig = crypto
    .createHmac('sha256', TEST_KEY_SECRET)
    .update(`${payId}|${subId}`)
    .digest('hex');

  assert.equal(
    verifyRazorpaySubscriptionSignature({
      paymentId: payId,
      subscriptionId: subId,
      signature: subSig,
      keySecret: TEST_KEY_SECRET,
    }),
    true
  );

  const verified = await engine.verifyAndActivateRazorpay({
    userId: 'u_sub',
    orderId: order.orderId,
    razorpaySubscriptionId: subId,
    razorpayPaymentId: payId,
    razorpaySignature: subSig,
    paymentMethod: 'UPI',
    nowMs: t0,
  });

  assert.equal(verified.verified, true);
  assert.equal(verified.membership.autoRenew, true);

  const uSubHistory = await engine.getUserPaymentHistory('u_sub');
  const uOtherHistory = await engine.getUserPaymentHistory('u_other');
  assert.equal(uSubHistory.payments.length, 1);
  assert.equal(uSubHistory.payments[0].transactionId, payId);
  assert.equal(uOtherHistory.payments.length, 0);
});

test('15. ₹1 1-Day Trial + AutoPay activates 24h ad-free access, prevents repeat trial abuse, supports disabling AutoPay, and restores ads on expiry', async () => {
  const store = createMemoryStore();
  store.seedUser('u_trial', 'trial@gmail.com');
  const engine = new PaymentServiceEngine(store, { razorpayKeySecret: TEST_KEY_SECRET });
  const t0 = 1_750_000_000_000;

  const { order, targetPlan } = await engine.createTrialAutoPayOrder({
    userId: 'u_trial',
    autoPayTargetPlan: 'MONTHLY_99',
    idempotencyKey: 'idem_trial_1',
    nowMs: t0,
  });
  assert.equal(order.planTier, 'TRIAL_1DAY');
  assert.equal(order.amountInr, 1);
  assert.equal(order.amountPaise, 100);
  assert.equal(order.autoPayEnabled, true);
  assert.equal(targetPlan.tierId, 'MONTHLY_99');

  const subId = 'sub_trial_autopay_001';
  const payId = 'pay_trial_001';
  const sig = crypto.createHmac('sha256', TEST_KEY_SECRET).update(`${payId}|${subId}`).digest('hex');

  const activated = await engine.verifyAndActivateRazorpay({
    userId: 'u_trial',
    orderId: order.orderId,
    razorpaySubscriptionId: subId,
    razorpayPaymentId: payId,
    razorpaySignature: sig,
    paymentMethod: 'UPI AutoPay',
    nowMs: t0,
  });

  assert.equal(activated.verified, true);
  assert.equal(activated.payment.amountInr, 1);
  assert.ok(activated.payment.receiptNumber.startsWith('PP-TR-'));
  assert.equal(activated.membership.isTrial, true);
  assert.equal(activated.membership.autoPayEnabled, true);
  assert.equal(activated.membership.autoPayTargetPlan, 'MONTHLY_99');
  assert.equal(activated.membership.nextBillingAmountInr, 99);
  assert.equal(activated.membership.expiryDateMs, t0 + DAY_MS);

  // Active during 24-hour window -> ads disabled
  const duringTrial = evaluateMembershipState(activated.membership, t0 + 12 * 60 * 60 * 1000);
  assert.equal(duringTrial.isPremiumActive, true);
  assert.equal(duringTrial.adsEnabled, false);

  // Prevent repeat ₹1 trial abuse on same account
  await assert.rejects(
    () =>
      engine.createTrialAutoPayOrder({
        userId: 'u_trial',
        autoPayTargetPlan: 'MONTHLY_99',
        idempotencyKey: 'idem_trial_2',
        nowMs: t0 + 1000,
      }),
    /already used the ₹1 1-day trial/i
  );

  // Turning off AutoPay keeps remaining 24h trial active until expiry
  const disabledAutoPay = await engine.setAutoPayStatus({
    userId: 'u_trial',
    enabled: false,
    reason: 'User turned off AutoPay before renewal',
    nowMs: t0 + 10 * 60 * 60 * 1000,
  });
  assert.equal(disabledAutoPay.autoPayEnabled, false);
  assert.equal(disabledAutoPay.autoRenew, false);
  assert.equal(evaluateMembershipState(disabledAutoPay, t0 + 12 * 60 * 60 * 1000).isPremiumActive, true);

  // After 24h trial expires -> user returns to FREE and ads restore
  const afterExpiry = await engine.restoreMembership({ userId: 'u_trial', nowMs: t0 + DAY_MS + 60_000 });
  assert.equal(afterExpiry.state.isPremiumActive, false);
  assert.equal(afterExpiry.state.effectiveStatus, 'EXPIRED');
  assert.equal(afterExpiry.state.adsEnabled, true);
});
