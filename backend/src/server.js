import express from 'express';
import admin from 'firebase-admin';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import {
  PaymentServiceEngine,
  PLAN_CATALOG,
  getPlanOrThrow,
  validateRazorpayModeKey,
  verifyRazorpayWebhookSignature,
} from './paymentCore.js';

const PORT = Number(process.env.PORT || 8080);
const PROJECT_ID = process.env.FIREBASE_PROJECT_ID || 'gen-lang-client-0678944869';
const DATABASE_ID =
  process.env.FIRESTORE_DATABASE_ID ||
  'ai-studio-android-paperpil-f88989c0-7c78-44d2-af9b-c52f2e63432a';
const RAZORPAY_MODE = String(process.env.RAZORPAY_MODE || 'test').trim().toLowerCase();
const RAZORPAY_KEY_ID = process.env.RAZORPAY_KEY_ID || '';
const RAZORPAY_KEY_SECRET = process.env.RAZORPAY_KEY_SECRET || '';
const RAZORPAY_WEBHOOK_SECRET = process.env.RAZORPAY_WEBHOOK_SECRET || '';
const RAZORPAY_MONTHLY_PLAN_ID = process.env.RAZORPAY_MONTHLY_PLAN_ID || '';
const RAZORPAY_YEARLY_PLAN_ID = process.env.RAZORPAY_YEARLY_PLAN_ID || '';
const ADMIN_EMAILS = new Set(
  (process.env.ADMIN_EMAILS || 'milanmoondal@gmail.com')
    .split(',')
    .map((e) => e.trim().toLowerCase())
    .filter(Boolean)
);
const ALLOWED_ORIGINS = new Set(
  (process.env.ALLOWED_ORIGINS || '')
    .split(',')
    .map((o) => o.trim())
    .filter(Boolean)
);

if (!admin.apps.length) {
  admin.initializeApp({ projectId: PROJECT_ID });
}

const db = getFirestore(admin.app(), DATABASE_ID);

/**
 * Firestore repository adapter implementing the 5 required collections:
 * users/, memberships/, payments/, orders/, refunds/
 */
class FirestorePaymentStore {
  async findOrderByIdempotencyKey(userId, idempotencyKey) {
    const snap = await db
      .collection('orders')
      .where('userId', '==', userId)
      .where('idempotencyKey', '==', idempotencyKey)
      .limit(1)
      .get();
    return snap.empty ? null : snap.docs[0].data();
  }

  async saveOrder(order) {
    await db
      .collection('orders')
      .doc(order.orderId)
      .set({
        ...order,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
  }

  async getOrder(orderId) {
    const snap = await db.collection('orders').doc(orderId).get();
    return snap.exists ? snap.data() : null;
  }

  async updateOrderStatus(orderId, status, nowMs) {
    const ref = db.collection('orders').doc(orderId);
    const snap = await ref.get();
    if (snap.exists) {
      await ref.update({ status, updatedAtMs: nowMs, updatedAt: FieldValue.serverTimestamp() });
    }
  }

  async findPaymentByTransactionId(transactionId) {
    const snap = await db
      .collection('payments')
      .where('transactionId', '==', transactionId)
      .limit(1)
      .get();
    return snap.empty ? null : snap.docs[0].data();
  }

  async savePayment(payment) {
    await db
      .collection('payments')
      .doc(payment.paymentId)
      .set({
        ...payment,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
  }

  async getPayment(paymentId) {
    const snap = await db.collection('payments').doc(paymentId).get();
    return snap.exists ? snap.data() : null;
  }

  async updatePaymentStatus(paymentId, status, nowMs) {
    await db.collection('payments').doc(paymentId).update({
      status,
      updatedAtMs: nowMs,
      updatedAt: FieldValue.serverTimestamp(),
    });
  }

  async getMembershipByUser(userId) {
    const snap = await db.collection('memberships').doc(`mem_${userId}`).get();
    return snap.exists ? snap.data() : null;
  }

  async saveMembership(membership) {
    await db
      .collection('memberships')
      .doc(membership.membershipId)
      .set(
        {
          ...membership,
          createdAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
  }

  async syncUserMembershipSummary(userId, membership, nowMs) {
    const isActive =
      (membership.status === 'ACTIVE' || membership.status === 'CANCELLED') &&
      membership.expiryDateMs > nowMs;
    await db
      .collection('users')
      .doc(userId)
      .set(
        {
          userId,
          planTier: isActive ? membership.planTier : 'FREE',
          priceInr: isActive ? membership.amountInr : 0,
          currency: 'INR',
          membershipStatus: membership.status,
          premiumUntilEpochMs: isActive ? membership.expiryDateMs : 0,
          autoRenew: Boolean(membership.autoRenew),
          autoPayEnabled: Boolean(membership.autoPayEnabled),
          autoPayTargetPlan: membership.autoPayTargetPlan || '',
          autoPayMandateId: membership.autoPayMandateId || '',
          nextBillingAmountInr: Number(membership.nextBillingAmountInr || 0),
          isTrial: Boolean(membership.isTrial),
          hasUsedTrial: Boolean(membership.hasUsedTrial),
          paymentProvider: membership.paymentProvider || '',
          lastTransactionId: membership.transactionId || '',
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
  }

  async saveRefund(refund) {
    await db
      .collection('refunds')
      .doc(refund.refundId)
      .set({
        ...refund,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
  }

  async findRefundByPaymentId(paymentId) {
    const snap = await db.collection('refunds').where('paymentId', '==', paymentId).limit(1).get();
    return snap.empty ? null : snap.docs[0].data();
  }

  async listAllUsers() {
    const snap = await db.collection('users').get();
    return snap.docs.map((d) => d.data());
  }

  async listAllMemberships() {
    const snap = await db.collection('memberships').get();
    return snap.docs.map((d) => d.data());
  }

  async listAllPayments() {
    const snap = await db.collection('payments').orderBy('purchaseDateMs', 'desc').get();
    return snap.docs.map((d) => d.data());
  }

  async listPaymentsByUser(userId) {
    const snap = await db
      .collection('payments')
      .where('userId', '==', userId)
      .orderBy('purchaseDateMs', 'desc')
      .get();
    return snap.docs.map((d) => d.data());
  }

  async listRefundsByUser(userId) {
    const snap = await db
      .collection('refunds')
      .where('userId', '==', userId)
      .orderBy('processedAtMs', 'desc')
      .get();
    return snap.docs.map((d) => d.data());
  }

  async listAllRefunds() {
    const snap = await db.collection('refunds').orderBy('processedAtMs', 'desc').get();
    return snap.docs.map((d) => d.data());
  }
}

const store = new FirestorePaymentStore();
const engine = new PaymentServiceEngine(store, {
  razorpayKeySecret: RAZORPAY_KEY_SECRET,
  razorpayWebhookSecret: RAZORPAY_WEBHOOK_SECRET,
});

const app = express();

// Strict CORS (never wildcard '*' on authenticated endpoints)
app.use((req, res, next) => {
  const origin = req.headers.origin;
  if (origin && ALLOWED_ORIGINS.has(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Vary', 'Origin');
    res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  }
  if (req.method === 'OPTIONS') return res.status(204).end();
  next();
});

// Capture rawBody specifically for Razorpay webhook HMAC verification
app.use(
  express.json({
    limit: '256kb',
    verify: (req, _res, buf) => {
      req.rawBody = buf.toString('utf8');
    },
  })
);

async function verifyFirebaseToken(req, res, next) {
  try {
    const authHeader = req.headers.authorization || '';
    if (!authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'Missing Authorization Bearer token.' });
    }
    const idToken = authHeader.slice(7).trim();
    const decoded = await admin.auth().verifyIdToken(idToken);
    req.user = decoded;
    return next();
  } catch (err) {
    return res.status(401).json({ error: 'Invalid or expired Firebase authentication token.' });
  }
}

async function requireAdmin(req, res, next) {
  const email = String(req.user?.email || '').toLowerCase();
  const isClaimAdmin = req.user?.admin === true;
  const isEmailAdmin = req.user?.email_verified === true && ADMIN_EMAILS.has(email);
  if (isClaimAdmin || isEmailAdmin) return next();

  const adminDoc = await db.collection('admins').doc(req.user.uid).get();
  if (adminDoc.exists) return next();

  return res.status(403).json({ error: 'Administrator privileges required.' });
}

app.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    service: 'paperpilot-payment-backend',
    razorpayMode: RAZORPAY_MODE,
    plans: Object.keys(PLAN_CATALOG),
  });
});

// Shared handler for POST /api/payment/create-order (and alias POST /api/orders/create)
async function handleCreateOrder(req, res) {
  try {
    const { planTier, paymentProvider = 'RAZORPAY', idempotencyKey } = req.body || {};
    const plan = getPlanOrThrow(planTier);

    if (paymentProvider === 'RAZORPAY') {
      const modeCheck = validateRazorpayModeKey(RAZORPAY_MODE, RAZORPAY_KEY_ID);
      if (!modeCheck.valid) {
        return res.status(503).json({ error: modeCheck.reason });
      }
      if (!RAZORPAY_KEY_SECRET || RAZORPAY_KEY_SECRET.includes('replace_with_')) {
        return res.status(503).json({ error: 'RAZORPAY_KEY_SECRET is not configured on the backend server.' });
      }
    }

    const result = await engine.createOrder({
      userId: req.user.uid,
      planTier: plan.tierId,
      paymentProvider,
      idempotencyKey,
    });

    let razorpayOrderId = result.order.providerOrderId;
    if (paymentProvider === 'RAZORPAY') {
      const auth = Buffer.from(`${RAZORPAY_KEY_ID}:${RAZORPAY_KEY_SECRET}`).toString('base64');
      const rzpRes = await fetch('https://api.razorpay.com/v1/orders', {
        method: 'POST',
        headers: {
          Authorization: `Basic ${auth}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          amount: plan.amountPaise,
          currency: 'INR',
          receipt: result.order.orderId,
          notes: {
            userId: req.user.uid,
            planTier: plan.tierId,
            razorpayMode: RAZORPAY_MODE,
          },
        }),
      });
      if (!rzpRes.ok) {
        return res.status(502).json({ error: 'Razorpay order creation failed. Verify backend Razorpay API keys.' });
      }
      const rzpData = await rzpRes.json();
      razorpayOrderId = rzpData.id;
      await db.collection('orders').doc(result.order.orderId).update({
        providerOrderId: razorpayOrderId,
        razorpayMode: RAZORPAY_MODE,
        updatedAt: FieldValue.serverTimestamp(),
      });
    }

    return res.status(200).json({
      orderId: result.order.orderId,
      razorpayOrderId,
      razorpayKeyId: RAZORPAY_KEY_ID,
      razorpayMode: RAZORPAY_MODE,
      planTier: plan.tierId,
      amountInr: plan.amountInr,
      amountPaise: plan.amountPaise,
      currency: 'INR',
      duplicate: result.duplicate,
    });
  } catch (err) {
    return res.status(400).json({ error: err.message });
  }
}

app.post('/api/payment/create-order', verifyFirebaseToken, handleCreateOrder);
app.post('/api/orders/create', verifyFirebaseToken, handleCreateOrder);

// Shared handler for POST /api/payment/verify (and alias POST /api/payments/razorpay/verify)
async function handleVerifyRazorpay(req, res) {
  try {
    if (!RAZORPAY_KEY_SECRET || RAZORPAY_KEY_SECRET.includes('replace_with_')) {
      return res.status(503).json({ error: 'RAZORPAY_KEY_SECRET is not configured on the server.' });
    }
    const {
      orderId,
      razorpayOrderId,
      razorpaySubscriptionId,
      razorpayPaymentId,
      razorpaySignature,
      paymentMethod,
    } = req.body || {};
    const outcome = await engine.verifyAndActivateRazorpay({
      userId: req.user.uid,
      orderId,
      razorpayOrderId,
      razorpaySubscriptionId,
      razorpayPaymentId,
      razorpaySignature,
      paymentMethod,
    });
    if (!outcome.verified) {
      return res.status(400).json({ error: 'Payment signature verification failed.', payment: outcome.payment });
    }
    return res.status(200).json(outcome);
  } catch (err) {
    return res.status(400).json({ error: err.message });
  }
}

app.post('/api/payment/verify', verifyFirebaseToken, handleVerifyRazorpay);
app.post('/api/payments/razorpay/verify', verifyFirebaseToken, handleVerifyRazorpay);

// Record cancelled checkout cleanly (keeps user FREE with ads enabled)
app.post('/api/payment/cancel-order', verifyFirebaseToken, async (req, res) => {
  try {
    const { orderId, reason } = req.body || {};
    if (!orderId) return res.status(400).json({ error: 'orderId is required.' });
    const cancelled = await engine.cancelOrder({
      userId: req.user.uid,
      orderId: String(orderId),
      reason: String(reason || 'Cancelled by user').slice(0, 200),
    });
    return res.status(200).json({ cancelled: true, payment: cancelled });
  } catch (err) {
    return res.status(400).json({ error: err.message });
  }
});

// Create recurring Razorpay subscription (when plan IDs are configured) or fallback server order
app.post('/api/subscription/create', verifyFirebaseToken, async (req, res) => {
  try {
    const { planTier, idempotencyKey } = req.body || {};
    const plan = getPlanOrThrow(planTier);
    const modeCheck = validateRazorpayModeKey(RAZORPAY_MODE, RAZORPAY_KEY_ID);
    if (!modeCheck.valid) {
      return res.status(503).json({ error: modeCheck.reason });
    }
    if (!RAZORPAY_KEY_SECRET || RAZORPAY_KEY_SECRET.includes('replace_with_')) {
      return res.status(503).json({ error: 'RAZORPAY_KEY_SECRET is not configured on the backend server.' });
    }

    const rzpPlanId = plan.tierId === 'YEARLY_999' ? RAZORPAY_YEARLY_PLAN_ID : RAZORPAY_MONTHLY_PLAN_ID;
    if (!rzpPlanId) {
      req.body = { ...req.body, paymentProvider: 'RAZORPAY' };
      return handleCreateOrder(req, res);
    }

    const { order } = await engine.createOrder({
      userId: req.user.uid,
      planTier: plan.tierId,
      paymentProvider: 'RAZORPAY',
      idempotencyKey,
    });

    const auth = Buffer.from(`${RAZORPAY_KEY_ID}:${RAZORPAY_KEY_SECRET}`).toString('base64');
    const subRes = await fetch('https://api.razorpay.com/v1/subscriptions', {
      method: 'POST',
      headers: {
        Authorization: `Basic ${auth}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        plan_id: rzpPlanId,
        total_count: plan.tierId === 'YEARLY_999' ? 10 : 120,
        customer_notify: 1,
        notes: {
          userId: req.user.uid,
          planTier: plan.tierId,
          internalOrderId: order.orderId,
          razorpayMode: RAZORPAY_MODE,
        },
      }),
    });
    if (!subRes.ok) {
      return res.status(502).json({ error: 'Razorpay subscription creation failed. Check plan IDs and API keys.' });
    }
    const subData = await subRes.json();
    await db.collection('orders').doc(order.orderId).update({
      providerOrderId: subData.id,
      razorpaySubscriptionId: subData.id,
      razorpayMode: RAZORPAY_MODE,
      updatedAt: FieldValue.serverTimestamp(),
    });

    return res.status(200).json({
      orderId: order.orderId,
      razorpaySubscriptionId: subData.id,
      razorpayKeyId: RAZORPAY_KEY_ID,
      razorpayMode: RAZORPAY_MODE,
      planTier: plan.tierId,
      amountInr: plan.amountInr,
      amountPaise: plan.amountPaise,
      currency: 'INR',
      recurring: true,
    });
  } catch (err) {
    return res.status(400).json({ error: err.message });
  }
});

// ₹1 for 1-Day Trial + UPI AutoPay mandate setup (charges ₹1 today, renews at ₹99/mo or ₹999/yr after 24h)
app.post('/api/subscription/trial-autopay', verifyFirebaseToken, async (req, res) => {
  try {
    const { autoPayTargetPlan = 'MONTHLY_99', idempotencyKey } = req.body || {};
    const modeCheck = validateRazorpayModeKey(RAZORPAY_MODE, RAZORPAY_KEY_ID);
    if (!modeCheck.valid) {
      return res.status(503).json({ error: modeCheck.reason });
    }
    if (!RAZORPAY_KEY_SECRET || RAZORPAY_KEY_SECRET.includes('replace_with_')) {
      return res.status(503).json({ error: 'RAZORPAY_KEY_SECRET is not configured on the backend server.' });
    }

    const nowMs = Date.now();
    const { order, targetPlan, duplicate } = await engine.createTrialAutoPayOrder({
      userId: req.user.uid,
      autoPayTargetPlan,
      idempotencyKey,
      nowMs,
    });

    const auth = Buffer.from(`${RAZORPAY_KEY_ID}:${RAZORPAY_KEY_SECRET}`).toString('base64');
    const rzpPlanId = targetPlan.tierId === 'YEARLY_999' ? RAZORPAY_YEARLY_PLAN_ID : RAZORPAY_MONTHLY_PLAN_ID;
    let razorpayOrderId = order.providerOrderId;
    let razorpaySubscriptionId = '';

    if (rzpPlanId) {
      // Official Razorpay Subscriptions API with ₹1 upfront verification addon & 24-hour deferred start_at
      const startAtUnix = Math.floor((nowMs + 24 * 60 * 60 * 1000) / 1000);
      const subRes = await fetch('https://api.razorpay.com/v1/subscriptions', {
        method: 'POST',
        headers: {
          Authorization: `Basic ${auth}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          plan_id: rzpPlanId,
          total_count: targetPlan.tierId === 'YEARLY_999' ? 10 : 120,
          start_at: startAtUnix,
          customer_notify: 1,
          addons: [
            {
              item: {
                name: 'PaperPilot 1-Day Trial (₹1 AutoPay Verification)',
                amount: 100,
                currency: 'INR',
              },
            },
          ],
          notes: {
            userId: req.user.uid,
            planTier: 'TRIAL_1DAY',
            autoPayTargetPlan: targetPlan.tierId,
            internalOrderId: order.orderId,
            isTrial: 'true',
            razorpayMode: RAZORPAY_MODE,
          },
        }),
      });
      if (!subRes.ok) {
        return res.status(502).json({ error: 'Razorpay AutoPay subscription setup failed. Check plan IDs and API keys.' });
      }
      const subData = await subRes.json();
      razorpaySubscriptionId = subData.id;
      await db.collection('orders').doc(order.orderId).update({
        providerOrderId: razorpaySubscriptionId,
        razorpaySubscriptionId,
        razorpayMode: RAZORPAY_MODE,
        updatedAt: FieldValue.serverTimestamp(),
      });
    } else {
      // Standard ₹1 Razorpay Order fallback when recurring Plan IDs are not yet configured in Razorpay Dashboard
      const rzpRes = await fetch('https://api.razorpay.com/v1/orders', {
        method: 'POST',
        headers: {
          Authorization: `Basic ${auth}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          amount: 100,
          currency: 'INR',
          receipt: order.orderId,
          notes: {
            userId: req.user.uid,
            planTier: 'TRIAL_1DAY',
            autoPayTargetPlan: targetPlan.tierId,
            autopay_requested: 'true',
            razorpayMode: RAZORPAY_MODE,
          },
        }),
      });
      if (!rzpRes.ok) {
        return res.status(502).json({ error: 'Razorpay ₹1 trial order creation failed. Verify backend Razorpay keys.' });
      }
      const rzpData = await rzpRes.json();
      razorpayOrderId = rzpData.id;
      await db.collection('orders').doc(order.orderId).update({
        providerOrderId: razorpayOrderId,
        razorpayMode: RAZORPAY_MODE,
        updatedAt: FieldValue.serverTimestamp(),
      });
    }

    return res.status(200).json({
      orderId: order.orderId,
      razorpayOrderId: razorpaySubscriptionId ? '' : razorpayOrderId,
      razorpaySubscriptionId,
      razorpayKeyId: RAZORPAY_KEY_ID,
      razorpayMode: RAZORPAY_MODE,
      planTier: 'TRIAL_1DAY',
      amountInr: 1,
      amountPaise: 100,
      currency: 'INR',
      autoPayEnabled: true,
      autoPayTargetPlan: targetPlan.tierId,
      nextChargeAmountInr: targetPlan.amountInr,
      nextChargeDateMs: order.nextChargeDateMs,
      duplicate,
    });
  } catch (err) {
    return res.status(400).json({ error: err.message });
  }
});

// Toggle / disable AutoPay mandate while keeping current trial or paid access until expiry
app.post('/api/subscription/autopay/toggle', verifyFirebaseToken, async (req, res) => {
  try {
    const { enabled = false, reason = '' } = req.body || {};
    const existing = await store.getMembershipByUser(req.user.uid);
    if (!existing) {
      return res.status(404).json({ error: 'No active membership or trial found.' });
    }

    // If turning off an active Razorpay subscription mandate, cancel it on Razorpay API
    if (!enabled && existing.autoPayMandateId && RAZORPAY_KEY_ID && RAZORPAY_KEY_SECRET) {
      const auth = Buffer.from(`${RAZORPAY_KEY_ID}:${RAZORPAY_KEY_SECRET}`).toString('base64');
      await fetch(`https://api.razorpay.com/v1/subscriptions/${encodeURIComponent(existing.autoPayMandateId)}/cancel`, {
        method: 'POST',
        headers: {
          Authorization: `Basic ${auth}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ cancel_at_cycle_end: 1 }),
      }).catch(() => null);
    }

    const updated = await engine.setAutoPayStatus({
      userId: req.user.uid,
      enabled: Boolean(enabled),
      reason: String(reason || '').slice(0, 200),
    });
    return res.status(200).json({ membership: updated });
  } catch (err) {
    return res.status(400).json({ error: err.message });
  }
});

// Google Play Billing server-side purchase token verification
app.post('/api/payments/google-play/verify', verifyFirebaseToken, async (req, res) => {
  try {
    const { productId, purchaseToken, orderId } = req.body || {};
    const outcome = await engine.verifyAndActivateGooglePlay({
      userId: req.user.uid,
      productId,
      purchaseToken,
      orderId,
      playVerificationFn: async ({ productId: sku, purchaseToken: token }) => {
        if (!process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON) {
          return { valid: false, reason: 'Google Play Developer API service account not configured on server.' };
        }
        // Production hook: validate via Android Publisher API v3 purchases.subscriptionsv2.get
        const packageName = process.env.GOOGLE_PLAY_PACKAGE_NAME || 'com.aistudio.paperpilot.xkqmvn';
        const credential = admin.credential.cert(JSON.parse(process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON));
        const accessToken = (await credential.getAccessToken()).access_token;
        const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(token)}`;
        const response = await fetch(url, {
          headers: { Authorization: `Bearer ${accessToken}` },
        });
        if (!response.ok) {
          return { valid: false, reason: `Play API returned HTTP ${response.status}` };
        }
        const sub = await response.json();
        const lineItem = sub.lineItems?.find((item) => item.productId === sku);
        const expiryMs = lineItem?.expiryTime ? Date.parse(lineItem.expiryTime) : 0;
        const active = sub.subscriptionState === 'SUBSCRIPTION_STATE_ACTIVE' && expiryMs > Date.now();
        return {
          valid: active,
          expiryTimeMillis: expiryMs,
          autoRenewing: Boolean(lineItem?.autoRenewingPlan?.autoRenewEnabled),
          orderId: sub.latestOrderId || orderId,
          reason: active ? '' : `Subscription state: ${sub.subscriptionState || 'UNKNOWN'}`,
        };
      },
    });
    if (!outcome.verified) {
      return res.status(400).json({ error: outcome.payment?.failureReason || 'Verification failed.' });
    }
    return res.status(200).json(outcome);
  } catch (err) {
    return res.status(400).json({ error: err.message });
  }
});

// Razorpay Webhook (HMAC-SHA256 verified)
app.post('/api/webhooks/razorpay', async (req, res) => {
  try {
    const signature = req.headers['x-razorpay-signature'];
    const valid = verifyRazorpayWebhookSignature({
      rawBody: req.rawBody,
      signature,
      webhookSecret: RAZORPAY_WEBHOOK_SECRET,
    });
    if (!valid) {
      return res.status(401).json({ error: 'Invalid webhook signature.' });
    }

    const event = req.body?.event;
    const paymentEntity = req.body?.payload?.payment?.entity;
    const refundEntity = req.body?.payload?.refund?.entity;

    if (event === 'refund.processed' && refundEntity?.payment_id) {
      const payment = await store.findPaymentByTransactionId(refundEntity.payment_id);
      if (payment) {
        await engine.processRefund({
          paymentId: payment.paymentId,
          reason: 'Razorpay webhook refund.processed',
        });
      }
    } else if (event === 'payment.failed' && paymentEntity?.order_id) {
      const internalOrderId = paymentEntity?.notes?.internal_order_id;
      if (internalOrderId) {
        await store.updateOrderStatus(internalOrderId, 'FAILED', Date.now());
      }
    } else if (
      (event === 'payment.captured' || event === 'subscription.charged') &&
      paymentEntity?.id &&
      paymentEntity?.notes?.userId &&
      paymentEntity?.notes?.planTier
    ) {
      const existing = await store.findPaymentByTransactionId(paymentEntity.id);
      if (!existing) {
        const billedPlanTier =
          event === 'subscription.charged' && paymentEntity.notes.planTier === 'TRIAL_1DAY'
            ? paymentEntity.notes.autoPayTargetPlan || 'MONTHLY_99'
            : paymentEntity.notes.planTier;
        await engine._activateVerifiedPayment({
          userId: paymentEntity.notes.userId,
          orderId: paymentEntity.notes.internalOrderId || paymentEntity.order_id || `wh_${paymentEntity.id}`,
          transactionId: paymentEntity.id,
          planTier: billedPlanTier,
          paymentProvider: 'RAZORPAY',
          paymentMethod: String(paymentEntity.method || 'RAZORPAY').toUpperCase(),
          autoRenew: event === 'subscription.charged',
          autoPayEnabled: event === 'subscription.charged',
          autoPayTargetPlan: paymentEntity.notes.autoPayTargetPlan || billedPlanTier,
        });
      }
    } else if (
      (event === 'subscription.cancelled' || event === 'subscription.halted') &&
      req.body?.payload?.subscription?.entity?.notes?.userId
    ) {
      await engine.setAutoPayStatus({
        userId: req.body.payload.subscription.entity.notes.userId,
        enabled: false,
        reason: `Razorpay webhook ${event}`,
      });
    }
    return res.status(200).json({ received: true, event });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

// Shared handler for POST /api/subscription/cancel (and alias POST /api/subscriptions/cancel)
async function handleCancelSubscription(req, res) {
  try {
    const { reason, immediate } = req.body || {};
    const updated = await engine.cancelSubscription({
      userId: req.user.uid,
      reason: String(reason || 'Cancelled by user').slice(0, 200),
      immediate: Boolean(immediate),
    });
    return res.status(200).json({ membership: updated });
  } catch (err) {
    return res.status(400).json({ error: err.message });
  }
}

app.post('/api/subscription/cancel', verifyFirebaseToken, handleCancelSubscription);
app.post('/api/subscriptions/cancel', verifyFirebaseToken, handleCancelSubscription);

app.post('/api/subscriptions/restore', verifyFirebaseToken, async (req, res) => {
  try {
    const restored = await engine.restoreMembership({ userId: req.user.uid });
    return res.status(200).json(restored);
  } catch (err) {
    return res.status(400).json({ error: err.message });
  }
});

// GET /api/membership/status — evaluates expiry server-side and returns exact AdMob & Premium state
app.get('/api/membership/status', verifyFirebaseToken, async (req, res) => {
  try {
    const { membership, state } = await engine.restoreMembership({ userId: req.user.uid });
    return res.status(200).json({
      userId: req.user.uid,
      isPremiumActive: state.isPremiumActive,
      effectiveStatus: state.effectiveStatus,
      adsEnabled: state.adsEnabled,
      planTier: state.planTier,
      membership,
    });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

// GET /api/payment/history — returns authenticated user's payment receipts & refunds
app.get('/api/payment/history', verifyFirebaseToken, async (req, res) => {
  try {
    const history = await engine.getUserPaymentHistory(req.user.uid);
    return res.status(200).json(history);
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

// Admin-only endpoints (strictly blocked for ordinary users)
app.get('/api/admin/dashboard', verifyFirebaseToken, requireAdmin, async (_req, res) => {
  try {
    const metrics = await engine.computeAdminDashboard();
    return res.status(200).json(metrics);
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

app.get('/api/admin/transactions', verifyFirebaseToken, requireAdmin, async (_req, res) => {
  try {
    const [payments, refunds] = await Promise.all([store.listAllPayments(), store.listAllRefunds()]);
    return res.status(200).json({ payments, refunds });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

app.post('/api/admin/refunds', verifyFirebaseToken, requireAdmin, async (req, res) => {
  try {
    const { paymentId, reason } = req.body || {};
    const result = await engine.processRefund({
      paymentId: String(paymentId || ''),
      reason: String(reason || 'Admin initiated refund').slice(0, 200),
    });
    return res.status(200).json(result);
  } catch (err) {
    return res.status(400).json({ error: err.message });
  }
});

if (process.env.NODE_ENV !== 'test') {
  app.listen(PORT, () => {
    console.log(`PaperPilot payment backend listening on port ${PORT}`);
  });
}

export { app, engine };
