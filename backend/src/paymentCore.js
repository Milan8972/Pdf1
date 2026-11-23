import crypto from 'node:crypto';

export const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * Authoritative PaperPilot Premium pricing catalog.
 * Only Monthly (₹99/month) and Yearly (₹999/year) plans exist.
 * Old ₹100 / ₹1,100 / ₹499 / lifetime plans are explicitly disallowed.
 */
export const PLAN_CATALOG = Object.freeze({
  TRIAL_1DAY: Object.freeze({
    tierId: 'TRIAL_1DAY',
    title: '1-Day Trial + AutoPay',
    amountInr: 1,
    amountPaise: 100,
    currency: 'INR',
    durationDays: 1,
    isTrial: true,
    playProductId: 'paperpilot_premium_monthly',
  }),
  MONTHLY_99: Object.freeze({
    tierId: 'MONTHLY_99',
    title: 'Monthly Premium',
    amountInr: 99,
    amountPaise: 9900,
    currency: 'INR',
    durationDays: 30,
    isTrial: false,
    playProductId: 'paperpilot_premium_monthly',
  }),
  YEARLY_999: Object.freeze({
    tierId: 'YEARLY_999',
    title: 'Yearly Premium',
    amountInr: 999,
    amountPaise: 99900,
    currency: 'INR',
    durationDays: 365,
    isTrial: false,
    playProductId: 'paperpilot_premium_yearly',
  }),
});

export function getPlanOrThrow(planTier) {
  const plan = PLAN_CATALOG[planTier];
  if (!plan) {
    throw new Error(`Unsupported membership plan "${planTier}". Allowed: TRIAL_1DAY (₹1), MONTHLY_99 (₹99), YEARLY_999 (₹999).`);
  }
  return plan;
}

export function getRecurringTargetPlanOrThrow(planTier) {
  if (planTier !== 'MONTHLY_99' && planTier !== 'YEARLY_999') {
    throw new Error(`AutoPay target plan must be MONTHLY_99 (₹99/month) or YEARLY_999 (₹999/year). Received "${planTier}".`);
  }
  return PLAN_CATALOG[planTier];
}

export function getPlanByPlayProductId(productId) {
  return Object.values(PLAN_CATALOG).find((p) => !p.isTrial && p.playProductId === productId) || null;
}

/**
 * Constant-time HMAC-SHA256 verification for Razorpay Checkout payment callback.
 * Spec: HMAC_SHA256(razorpay_order_id + "|" + razorpay_payment_id, RAZORPAY_KEY_SECRET)
 */
export function verifyRazorpayPaymentSignature({ orderId, paymentId, signature, keySecret }) {
  if (!orderId || !paymentId || !signature || !keySecret) return false;
  const expected = crypto
    .createHmac('sha256', String(keySecret))
    .update(`${orderId}|${paymentId}`)
    .digest('hex');

  const expectedBuf = Buffer.from(expected, 'utf8');
  const receivedBuf = Buffer.from(String(signature), 'utf8');
  if (expectedBuf.length !== receivedBuf.length) return false;
  return crypto.timingSafeEqual(expectedBuf, receivedBuf);
}

/**
 * Constant-time HMAC-SHA256 verification for Razorpay Subscription Checkout callback.
 * Spec: HMAC_SHA256(razorpay_payment_id + "|" + razorpay_subscription_id, RAZORPAY_KEY_SECRET)
 */
export function verifyRazorpaySubscriptionSignature({ paymentId, subscriptionId, signature, keySecret }) {
  if (!paymentId || !subscriptionId || !signature || !keySecret) return false;
  const expected = crypto
    .createHmac('sha256', String(keySecret))
    .update(`${paymentId}|${subscriptionId}`)
    .digest('hex');

  const expectedBuf = Buffer.from(expected, 'utf8');
  const receivedBuf = Buffer.from(String(signature), 'utf8');
  if (expectedBuf.length !== receivedBuf.length) return false;
  return crypto.timingSafeEqual(expectedBuf, receivedBuf);
}

/**
 * Prevents accidental Test/Live credential mismatch and blocks automatic switching to Live Mode.
 */
export function validateRazorpayModeKey(mode, keyId) {
  const normalizedMode = String(mode || 'test').trim().toLowerCase();
  const normalizedKey = String(keyId || '').trim();
  if (!normalizedKey) {
    return { valid: false, mode: normalizedMode, reason: 'RAZORPAY_KEY_ID is not configured on the server.' };
  }
  if (normalizedKey.includes('replace_with_') || normalizedKey.includes('replace_me')) {
    return { valid: false, mode: normalizedMode, reason: 'RAZORPAY_KEY_ID still contains a placeholder value.' };
  }
  if (normalizedMode === 'test' && !normalizedKey.startsWith('rzp_test_')) {
    return {
      valid: false,
      mode: 'test',
      reason: 'Server is in RAZORPAY_MODE=test but key is not an rzp_test_ key. Refusing to process live charges in test mode.',
    };
  }
  if (normalizedMode === 'live' && !normalizedKey.startsWith('rzp_live_')) {
    return {
      valid: false,
      mode: 'live',
      reason: 'Server is in RAZORPAY_MODE=live but key is not an rzp_live_ key.',
    };
  }
  if (normalizedMode !== 'test' && normalizedMode !== 'live') {
    return { valid: false, mode: normalizedMode, reason: `Unsupported RAZORPAY_MODE="${normalizedMode}". Use "test" or "live".` };
  }
  return { valid: true, mode: normalizedMode, reason: '' };
}

/**
 * Constant-time HMAC-SHA256 verification for Razorpay Webhook `x-razorpay-signature` header.
 */
export function verifyRazorpayWebhookSignature({ rawBody, signature, webhookSecret }) {
  if (!rawBody || !signature || !webhookSecret) return false;
  const expected = crypto
    .createHmac('sha256', String(webhookSecret))
    .update(rawBody)
    .digest('hex');

  const expectedBuf = Buffer.from(expected, 'utf8');
  const receivedBuf = Buffer.from(String(signature), 'utf8');
  if (expectedBuf.length !== receivedBuf.length) return false;
  return crypto.timingSafeEqual(expectedBuf, receivedBuf);
}

export function generateReceiptNumber(planTier, nowMs = Date.now()) {
  const suffix = crypto.randomBytes(3).toString('hex').toUpperCase();
  const code = planTier === 'YEARLY_999' ? 'YR' : planTier === 'TRIAL_1DAY' ? 'TR' : 'MO';
  return `PP-${code}-${Math.floor(nowMs / 1000)}-${suffix}`;
}

/**
 * Computes the next membership expiry date, cleanly extending active subscriptions on renewal.
 */
export function computeNewExpiryMs(planTier, currentExpiryMs = 0, nowMs = Date.now()) {
  const plan = getPlanOrThrow(planTier);
  const base = Number.isFinite(currentExpiryMs) && currentExpiryMs > nowMs ? currentExpiryMs : nowMs;
  return base + plan.durationDays * DAY_MS;
}

/**
 * Evaluates whether a membership record is currently active, expired, cancelled, or refunded.
 */
export function evaluateMembershipState(membership, nowMs = Date.now()) {
  if (!membership) {
    return {
      isPremiumActive: false,
      effectiveStatus: 'NONE',
      adsEnabled: true,
      planTier: 'FREE',
    };
  }
  if (membership.status === 'REFUNDED' || membership.renewalStatus === 'REVOKED') {
    return {
      isPremiumActive: false,
      effectiveStatus: 'REFUNDED',
      adsEnabled: true,
      planTier: 'FREE',
    };
  }
  const expiry = Number(membership.expiryDateMs || 0);
  if (expiry <= nowMs) {
    return {
      isPremiumActive: false,
      effectiveStatus: 'EXPIRED',
      adsEnabled: true,
      planTier: 'FREE',
    };
  }
  const isActive = membership.status === 'ACTIVE' || membership.status === 'CANCELLED';
  return {
    isPremiumActive: isActive,
    effectiveStatus: membership.status === 'CANCELLED' ? 'CANCELLED' : 'ACTIVE',
    adsEnabled: !isActive,
    planTier: isActive ? membership.planTier : 'FREE',
  };
}

/**
 * In-memory / pluggable repository engine used both by unit tests and Firestore server adapters.
 */
export class PaymentServiceEngine {
  constructor(store, config = {}) {
    this.store = store;
    this.razorpayKeySecret = config.razorpayKeySecret || '';
    this.razorpayWebhookSecret = config.razorpayWebhookSecret || '';
  }

  async createOrder({ userId, planTier, paymentProvider, idempotencyKey, nowMs = Date.now() }) {
    if (!userId) throw new Error('Authenticated userId is required.');
    const plan = getPlanOrThrow(planTier);
    const provider = paymentProvider === 'RAZORPAY' ? 'RAZORPAY' : 'GOOGLE_PLAY';
    const idem = String(idempotencyKey || `${userId}_${plan.tierId}_${Math.floor(nowMs / 10000)}`);

    // Duplicate order protection via idempotency key
    const existingOrder = await this.store.findOrderByIdempotencyKey(userId, idem);
    if (existingOrder && existingOrder.status === 'CREATED') {
      return { order: existingOrder, duplicate: true };
    }

    const orderId = `ord_${crypto.randomBytes(8).toString('hex')}`;
    const order = {
      orderId,
      providerOrderId: provider === 'RAZORPAY' ? `order_${crypto.randomBytes(7).toString('hex')}` : orderId,
      userId,
      planTier: plan.tierId,
      amountInr: plan.amountInr,
      amountPaise: plan.amountPaise,
      currency: plan.currency,
      paymentProvider: provider,
      status: 'CREATED',
      idempotencyKey: idem,
      createdAtMs: nowMs,
      updatedAtMs: nowMs,
    };
    await this.store.saveOrder(order);
    return { order, duplicate: false };
  }

  /**
   * Creates an authoritative ₹1 (100 paise) 1-day trial + AutoPay setup order.
   * Enforces one-time trial eligibility per user account to prevent trial abuse.
   */
  async createTrialAutoPayOrder({
    userId,
    autoPayTargetPlan = 'MONTHLY_99',
    idempotencyKey,
    nowMs = Date.now(),
  }) {
    if (!userId) throw new Error('Authenticated userId is required.');
    const targetPlan = getRecurringTargetPlanOrThrow(autoPayTargetPlan);

    // One-time trial eligibility check per account
    const history = await this.getUserPaymentHistory(userId);
    const alreadyUsedTrial = history.payments.some(
      (p) => p.planTier === 'TRIAL_1DAY' && (p.status === 'SUCCESS' || p.status === 'REFUNDED')
    );
    if (alreadyUsedTrial) {
      throw new Error('This Google account has already used the ₹1 1-day trial. Please select Monthly (₹99) or Yearly (₹999).');
    }

    const trialPlan = PLAN_CATALOG.TRIAL_1DAY;
    const idem = String(idempotencyKey || `${userId}_TRIAL_1DAY_${targetPlan.tierId}_${Math.floor(nowMs / 10000)}`);
    const existingOrder = await this.store.findOrderByIdempotencyKey(userId, idem);
    if (existingOrder && existingOrder.status === 'CREATED') {
      return { order: existingOrder, targetPlan, duplicate: true };
    }

    const orderId = `ord_tr_${crypto.randomBytes(8).toString('hex')}`;
    const order = {
      orderId,
      providerOrderId: `order_${crypto.randomBytes(7).toString('hex')}`,
      userId,
      planTier: trialPlan.tierId,
      amountInr: trialPlan.amountInr,
      amountPaise: trialPlan.amountPaise,
      currency: trialPlan.currency,
      paymentProvider: 'RAZORPAY',
      status: 'CREATED',
      isTrial: true,
      autoPayEnabled: true,
      autoPayTargetPlan: targetPlan.tierId,
      nextChargeAmountInr: targetPlan.amountInr,
      nextChargeDateMs: nowMs + trialPlan.durationDays * DAY_MS,
      idempotencyKey: idem,
      createdAtMs: nowMs,
      updatedAtMs: nowMs,
    };
    await this.store.saveOrder(order);
    return { order, targetPlan, duplicate: false };
  }

  async verifyAndActivateRazorpay({
    userId,
    orderId,
    razorpayOrderId,
    razorpaySubscriptionId = '',
    razorpayPaymentId,
    razorpaySignature,
    paymentMethod = 'UPI',
    nowMs = Date.now(),
  }) {
    if (!userId) throw new Error('Authenticated userId is required.');

    // 1. Duplicate transaction protection
    const duplicatePayment = await this.store.findPaymentByTransactionId(razorpayPaymentId);
    if (duplicatePayment) {
      if (duplicatePayment.userId !== userId) {
        throw new Error('Transaction belongs to a different account.');
      }
      const currentMembership = await this.store.getMembershipByUser(userId);
      return { verified: true, duplicate: true, payment: duplicatePayment, membership: currentMembership };
    }

    // 2. Look up server-created order
    const order = await this.store.getOrder(orderId);
    if (!order || order.userId !== userId) {
      throw new Error('Order not found for this user.');
    }

    // 3. Server-side cryptographic HMAC-SHA256 signature verification (Order OR Subscription flow)
    const signatureValid = razorpaySubscriptionId
      ? verifyRazorpaySubscriptionSignature({
          paymentId: razorpayPaymentId,
          subscriptionId: razorpaySubscriptionId,
          signature: razorpaySignature,
          keySecret: this.razorpayKeySecret,
        })
      : verifyRazorpayPaymentSignature({
          orderId: razorpayOrderId || order.providerOrderId,
          paymentId: razorpayPaymentId,
          signature: razorpaySignature,
          keySecret: this.razorpayKeySecret,
        });

    if (!signatureValid) {
      const failedPayment = {
        paymentId: `pay_fail_${crypto.randomBytes(6).toString('hex')}`,
        userId,
        orderId: order.orderId,
        transactionId: razorpayPaymentId || `invalid_${Date.now()}`,
        planTier: order.planTier,
        amountInr: order.amountInr,
        currency: 'INR',
        paymentProvider: 'RAZORPAY',
        paymentMethod,
        status: 'FAILED',
        purchaseDateMs: nowMs,
        expiryDateMs: 0,
        receiptNumber: '',
        failureReason: 'Signature verification failed',
        createdAtMs: nowMs,
        updatedAtMs: nowMs,
      };
      await this.store.savePayment(failedPayment);
      await this.store.updateOrderStatus(order.orderId, 'FAILED', nowMs);
      return { verified: false, duplicate: false, payment: failedPayment, membership: null };
    }

    // 4. Activate or renew membership ONLY after verified signature
    const isTrialOrder = order.planTier === 'TRIAL_1DAY' || Boolean(order.isTrial);
    const autoPayEnabled = Boolean(order.autoPayEnabled) || Boolean(razorpaySubscriptionId) || isTrialOrder;
    const autoPayTargetPlan = order.autoPayTargetPlan || (order.planTier === 'YEARLY_999' ? 'YEARLY_999' : 'MONTHLY_99');

    return this._activateVerifiedPayment({
      userId,
      orderId: order.orderId,
      transactionId: razorpayPaymentId,
      planTier: order.planTier,
      paymentProvider: 'RAZORPAY',
      paymentMethod,
      autoRenew: autoPayEnabled || order.planTier === 'YEARLY_999',
      autoPayEnabled,
      autoPayTargetPlan,
      autoPayMandateId: razorpaySubscriptionId || order.razorpaySubscriptionId || '',
      nowMs,
    });
  }

  async verifyAndActivateGooglePlay({
    userId,
    productId,
    purchaseToken,
    orderId,
    playVerificationFn,
    nowMs = Date.now(),
  }) {
    if (!userId || !purchaseToken) {
      throw new Error('Authenticated userId and Google Play purchaseToken are required.');
    }
    const plan = getPlanByPlayProductId(productId);
    if (!plan) {
      throw new Error(`Invalid Google Play subscription productId: ${productId}`);
    }

    // Duplicate transaction / purchaseToken protection
    const duplicatePayment = await this.store.findPaymentByTransactionId(purchaseToken);
    if (duplicatePayment) {
      if (duplicatePayment.userId !== userId) {
        throw new Error('Purchase token already linked to another account.');
      }
      const currentMembership = await this.store.getMembershipByUser(userId);
      return { verified: true, duplicate: true, payment: duplicatePayment, membership: currentMembership };
    }

    // Server-side verification via Play Developer API adapter
    if (typeof playVerificationFn !== 'function') {
      throw new Error('Google Play server verification provider is not configured.');
    }
    const playResult = await playVerificationFn({ productId, purchaseToken });
    if (!playResult || !playResult.valid) {
      const failedPayment = {
        paymentId: `pay_gp_fail_${crypto.randomBytes(6).toString('hex')}`,
        userId,
        orderId: orderId || `gp_${Date.now()}`,
        transactionId: purchaseToken,
        planTier: plan.tierId,
        amountInr: plan.amountInr,
        currency: 'INR',
        paymentProvider: 'GOOGLE_PLAY',
        paymentMethod: 'Google Play Billing',
        status: 'FAILED',
        purchaseDateMs: nowMs,
        expiryDateMs: 0,
        receiptNumber: '',
        failureReason: playResult?.reason || 'Google Play purchase verification failed',
        createdAtMs: nowMs,
        updatedAtMs: nowMs,
      };
      await this.store.savePayment(failedPayment);
      return { verified: false, duplicate: false, payment: failedPayment, membership: null };
    }

    return this._activateVerifiedPayment({
      userId,
      orderId: orderId || playResult.orderId || `gp_${crypto.randomBytes(6).toString('hex')}`,
      transactionId: purchaseToken,
      planTier: plan.tierId,
      paymentProvider: 'GOOGLE_PLAY',
      paymentMethod: 'Google Play Billing',
      autoRenew: playResult.autoRenewing !== false,
      explicitExpiryMs: playResult.expiryTimeMillis || 0,
      nowMs,
    });
  }

  async _activateVerifiedPayment({
    userId,
    orderId,
    transactionId,
    planTier,
    paymentProvider,
    paymentMethod,
    autoRenew,
    autoPayEnabled = false,
    autoPayTargetPlan = '',
    autoPayMandateId = '',
    explicitExpiryMs = 0,
    nowMs = Date.now(),
  }) {
    const plan = getPlanOrThrow(planTier);
    const existingMembership = await this.store.getMembershipByUser(userId);
    const expiryDateMs =
      explicitExpiryMs > nowMs
        ? explicitExpiryMs
        : computeNewExpiryMs(plan.tierId, existingMembership?.expiryDateMs || 0, nowMs);

    const targetPlanTier = autoPayTargetPlan || (plan.tierId === 'YEARLY_999' ? 'YEARLY_999' : 'MONTHLY_99');
    const targetPlan = PLAN_CATALOG[targetPlanTier] || PLAN_CATALOG.MONTHLY_99;
    const effectiveAutoPay = Boolean(autoPayEnabled || (autoRenew && paymentProvider === 'RAZORPAY'));

    const receiptNumber = generateReceiptNumber(plan.tierId, nowMs);
    const payment = {
      paymentId: `pay_${crypto.randomBytes(8).toString('hex')}`,
      userId,
      orderId,
      transactionId,
      planTier: plan.tierId,
      amountInr: plan.amountInr,
      currency: 'INR',
      paymentProvider,
      paymentMethod,
      status: 'SUCCESS',
      purchaseDateMs: nowMs,
      expiryDateMs,
      receiptNumber,
      failureReason: '',
      createdAtMs: nowMs,
      updatedAtMs: nowMs,
    };

    const membership = {
      membershipId: `mem_${userId}`,
      userId,
      planTier: plan.tierId,
      amountInr: plan.amountInr,
      currency: 'INR',
      paymentProvider,
      transactionId,
      status: 'ACTIVE',
      isTrial: Boolean(plan.isTrial),
      hasUsedTrial: Boolean(plan.isTrial || existingMembership?.hasUsedTrial),
      purchaseDateMs: nowMs,
      expiryDateMs,
      autoRenew: Boolean(autoRenew || effectiveAutoPay),
      autoPayEnabled: effectiveAutoPay,
      autoPayTargetPlan: effectiveAutoPay ? targetPlan.tierId : '',
      autoPayMandateId: autoPayMandateId || existingMembership?.autoPayMandateId || '',
      nextBillingAmountInr: effectiveAutoPay ? targetPlan.amountInr : 0,
      renewalStatus: autoRenew || effectiveAutoPay ? 'AUTO_RENEW' : 'CANCELLED_AT_PERIOD_END',
      createdAtMs: existingMembership?.createdAtMs || nowMs,
      updatedAtMs: nowMs,
    };

    await this.store.savePayment(payment);
    await this.store.updateOrderStatus(orderId, 'PAID', nowMs);
    await this.store.saveMembership(membership);
    await this.store.syncUserMembershipSummary(userId, membership, nowMs);

    return { verified: true, duplicate: false, payment, membership };
  }

  async cancelOrder({ userId, orderId, reason = 'Cancelled by user', nowMs = Date.now() }) {
    const order = await this.store.getOrder(orderId);
    if (!order || order.userId !== userId) {
      throw new Error('Order not found for user.');
    }
    await this.store.updateOrderStatus(orderId, 'CANCELLED', nowMs);
    const cancelledPayment = {
      paymentId: `pay_can_${crypto.randomBytes(6).toString('hex')}`,
      userId,
      orderId,
      transactionId: `cancelled_${orderId}`,
      planTier: order.planTier,
      amountInr: order.amountInr,
      currency: 'INR',
      paymentProvider: order.paymentProvider,
      paymentMethod: order.paymentProvider,
      status: 'CANCELLED',
      purchaseDateMs: nowMs,
      expiryDateMs: 0,
      receiptNumber: '',
      failureReason: reason,
      createdAtMs: nowMs,
      updatedAtMs: nowMs,
    };
    await this.store.savePayment(cancelledPayment);
    return cancelledPayment;
  }

  async cancelSubscription({ userId, reason = 'User requested cancellation', immediate = false, nowMs = Date.now() }) {
    const existing = await this.store.getMembershipByUser(userId);
    if (!existing) {
      throw new Error('No membership found for user.');
    }
    const updated = {
      ...existing,
      status: immediate ? 'EXPIRED' : 'CANCELLED',
      autoRenew: false,
      autoPayEnabled: false,
      nextBillingAmountInr: 0,
      renewalStatus: immediate ? 'EXPIRED' : 'CANCELLED_AT_PERIOD_END',
      expiryDateMs: immediate ? nowMs : existing.expiryDateMs,
      cancelReason: reason,
      updatedAtMs: nowMs,
    };
    await this.store.saveMembership(updated);
    await this.store.syncUserMembershipSummary(userId, updated, nowMs);
    return updated;
  }

  async setAutoPayStatus({ userId, enabled, reason = '', nowMs = Date.now() }) {
    const existing = await this.store.getMembershipByUser(userId);
    if (!existing) {
      throw new Error('No membership found for user.');
    }
    const targetPlan = PLAN_CATALOG[existing.autoPayTargetPlan] || PLAN_CATALOG.MONTHLY_99;
    const enableAutoPay = Boolean(enabled);
    const updated = {
      ...existing,
      autoRenew: enableAutoPay,
      autoPayEnabled: enableAutoPay,
      nextBillingAmountInr: enableAutoPay ? targetPlan.amountInr : 0,
      renewalStatus: enableAutoPay ? 'AUTO_RENEW' : 'AUTOPAY_DISABLED',
      autoPayUpdatedReason: String(reason || (enableAutoPay ? 'User enabled AutoPay' : 'User disabled AutoPay')).slice(0, 200),
      updatedAtMs: nowMs,
    };
    await this.store.saveMembership(updated);
    await this.store.syncUserMembershipSummary(userId, updated, nowMs);
    return updated;
  }

  async processRefund({ paymentId, reason = 'Refund approved', nowMs = Date.now() }) {
    const payment = await this.store.getPayment(paymentId);
    if (!payment) throw new Error('Payment record not found.');
    if (payment.status === 'REFUNDED') {
      const existingRefund = await this.store.findRefundByPaymentId(paymentId);
      return { duplicate: true, refund: existingRefund };
    }

    const refund = {
      refundId: `rfnd_${crypto.randomBytes(8).toString('hex')}`,
      userId: payment.userId,
      paymentId: payment.paymentId,
      transactionId: payment.transactionId,
      amountInr: payment.amountInr,
      currency: 'INR',
      paymentProvider: payment.paymentProvider,
      reason,
      status: 'PROCESSED',
      processedAtMs: nowMs,
      createdAtMs: nowMs,
      updatedAtMs: nowMs,
    };
    await this.store.saveRefund(refund);
    await this.store.updatePaymentStatus(payment.paymentId, 'REFUNDED', nowMs);

    const existingMembership = await this.store.getMembershipByUser(payment.userId);
    if (existingMembership) {
      const revoked = {
        ...existingMembership,
        status: 'REFUNDED',
        autoRenew: false,
        renewalStatus: 'REVOKED',
        expiryDateMs: nowMs,
        updatedAtMs: nowMs,
      };
      await this.store.saveMembership(revoked);
      await this.store.syncUserMembershipSummary(payment.userId, revoked, nowMs);
    }

    return { duplicate: false, refund };
  }

  async restoreMembership({ userId, nowMs = Date.now() }) {
    const membership = await this.store.getMembershipByUser(userId);
    const state = evaluateMembershipState(membership, nowMs);
    if (membership && state.effectiveStatus === 'EXPIRED' && membership.status !== 'EXPIRED') {
      const expired = {
        ...membership,
        status: 'EXPIRED',
        autoRenew: false,
        renewalStatus: 'EXPIRED',
        updatedAtMs: nowMs,
      };
      await this.store.saveMembership(expired);
      await this.store.syncUserMembershipSummary(userId, expired, nowMs);
      return { membership: expired, state };
    }
    return { membership, state };
  }

  async getUserPaymentHistory(userId) {
    if (!userId) throw new Error('Authenticated userId is required.');
    const [payments, refunds] = await Promise.all([
      typeof this.store.listPaymentsByUser === 'function'
        ? this.store.listPaymentsByUser(userId)
        : (await this.store.listAllPayments()).filter((p) => p.userId === userId),
      typeof this.store.listRefundsByUser === 'function'
        ? this.store.listRefundsByUser(userId)
        : (await this.store.listAllRefunds()).filter((r) => r.userId === userId),
    ]);
    return {
      payments: [...payments].sort((a, b) => Number(b.purchaseDateMs || 0) - Number(a.purchaseDateMs || 0)),
      refunds: [...refunds].sort((a, b) => Number(b.processedAtMs || 0) - Number(a.processedAtMs || 0)),
    };
  }

  async computeAdminDashboard(nowMs = Date.now()) {
    const [users, memberships, payments, refunds] = await Promise.all([
      this.store.listAllUsers(),
      this.store.listAllMemberships(),
      this.store.listAllPayments(),
      this.store.listAllRefunds(),
    ]);

    let activeMonthlyMembers = 0;
    let activeYearlyMembers = 0;
    let expiredMemberships = 0;

    for (const m of memberships) {
      const evalState = evaluateMembershipState(m, nowMs);
      if (evalState.isPremiumActive) {
        if (m.planTier === 'MONTHLY_99') activeMonthlyMembers += 1;
        else if (m.planTier === 'YEARLY_999') activeYearlyMembers += 1;
      } else if (evalState.effectiveStatus === 'EXPIRED') {
        expiredMemberships += 1;
      }
    }

    let successfulPayments = 0;
    let failedTransactions = 0;
    let monthlyRevenueInr = 0;
    let yearlyRevenueInr = 0;

    for (const p of payments) {
      if (p.status === 'SUCCESS') {
        successfulPayments += 1;
        if (p.planTier === 'MONTHLY_99') monthlyRevenueInr += Number(p.amountInr || 0);
        if (p.planTier === 'YEARLY_999') yearlyRevenueInr += Number(p.amountInr || 0);
      } else if (p.status === 'FAILED') {
        failedTransactions += 1;
      }
    }

    return {
      totalRegisteredUsers: users.length,
      totalPremiumMembers: activeMonthlyMembers + activeYearlyMembers,
      activeMonthlyMembers,
      activeYearlyMembers,
      expiredMemberships,
      successfulPayments,
      failedTransactions,
      refundsCount: refunds.length,
      totalGrossRevenueInr: monthlyRevenueInr + yearlyRevenueInr,
      monthlyRevenueInr,
      yearlyRevenueInr,
      currency: 'INR',
    };
  }
}
