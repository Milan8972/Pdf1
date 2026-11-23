const fs = require('node:fs');
const { test, before, after } = require('node:test');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

const PROJECT_ID = 'gen-lang-client-0678944869';
const DATABASE_ID = 'ai-studio-android-paperpil-f88989c0-7c78-44d2-af9b-c52f2e63432a';
const ADMIN_EMAIL = 'milanmoondal@gmail.com';

let testEnv;

function serverTs() {
  return new Date(Date.now() - 2000);
}

function validFreeUserDoc(uid, email = 'user@gmail.com', displayName = 'Test User') {
  return {
    userId: uid,
    email,
    displayName,
    planTier: 'FREE',
    priceInr: 0,
    currency: 'INR',
    membershipStatus: 'NONE',
    premiumUntilEpochMs: 0,
    autoRenew: false,
    paymentProvider: '',
    lastTransactionId: '',
    createdAt: serverTs(),
    updatedAt: serverTs(),
  };
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync('./firestore.rules', 'utf8'),
      host: '127.0.0.1',
      port: 8085,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

test('1. Unauthenticated clients are denied across all 5 collections', async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection('users').doc('u1').get());
  await assertFails(unauthDb.collection('memberships').doc('m1').get());
  await assertFails(unauthDb.collection('payments').doc('p1').get());
  await assertFails(unauthDb.collection('orders').doc('o1').get());
  await assertFails(unauthDb.collection('refunds').doc('r1').get());
});

test('2. Owner can create FREE profile and update email/displayName, but CANNOT self-upgrade to Premium', async () => {
  await testEnv.clearFirestore();
  const aliceDb = testEnv.authenticatedContext('alice_uid', {
    email: 'alice@gmail.com',
    email_verified: true,
  }).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection('users').doc('alice_uid').set(validFreeUserDoc('alice_uid', 'alice@gmail.com', 'Alice'))
  );

  await assertSucceeds(
    aliceDb.collection('users').doc('alice_uid').update({
      displayName: 'Alice Updated',
      updatedAt: serverTs(),
    })
  );

  await assertFails(
    aliceDb.collection('users').doc('alice_uid').update({
      planTier: 'MONTHLY_99',
      priceInr: 99,
      membershipStatus: 'ACTIVE',
      premiumUntilEpochMs: Date.now() + 86400000 * 30,
      updatedAt: serverTs(),
    })
  );

  const bobDb = testEnv.authenticatedContext('bob_uid', {
    email: 'bob@gmail.com',
    email_verified: true,
  }).firestore(DATABASE_ID);

  await assertFails(
    bobDb.collection('users').doc('bob_uid').set({
      ...validFreeUserDoc('bob_uid', 'bob@gmail.com', 'Bob'),
      planTier: 'YEARLY_999',
      priceInr: 999,
      membershipStatus: 'ACTIVE',
      premiumUntilEpochMs: Date.now() + 86400000 * 365,
    })
  );
});

test('3. Clients CANNOT write to memberships, payments, orders, or refunds directly', async () => {
  await testEnv.clearFirestore();
  const aliceDb = testEnv.authenticatedContext('alice_uid', {
    email: 'alice@gmail.com',
    email_verified: true,
  }).firestore(DATABASE_ID);

  await assertFails(aliceDb.collection('memberships').doc('m_fake').set({ userId: 'alice_uid', status: 'ACTIVE' }));
  await assertFails(aliceDb.collection('payments').doc('p_fake').set({ userId: 'alice_uid', status: 'SUCCESS', amountInr: 99 }));
  await assertFails(aliceDb.collection('orders').doc('o_fake').set({ userId: 'alice_uid', status: 'PAID' }));
  await assertFails(aliceDb.collection('refunds').doc('r_fake').set({ userId: 'alice_uid', status: 'PROCESSED' }));
});

test('4. Users can read ONLY their own payments/memberships and NEVER other users data', async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore(DATABASE_ID);
    await adminDb.collection('payments').doc('pay_alice').set({ userId: 'alice_uid', amountInr: 99, status: 'SUCCESS' });
    await adminDb.collection('payments').doc('pay_bob').set({ userId: 'bob_uid', amountInr: 999, status: 'SUCCESS' });
    await adminDb.collection('memberships').doc('mem_alice').set({ userId: 'alice_uid', planTier: 'MONTHLY_99', status: 'ACTIVE' });
  });

  const aliceDb = testEnv.authenticatedContext('alice_uid', {
    email: 'alice@gmail.com',
    email_verified: true,
  }).firestore(DATABASE_ID);

  await assertSucceeds(aliceDb.collection('payments').doc('pay_alice').get());
  await assertSucceeds(aliceDb.collection('payments').where('userId', '==', 'alice_uid').get());
  await assertFails(aliceDb.collection('payments').doc('pay_bob').get());
  await assertFails(aliceDb.collection('payments').get());
  await assertFails(aliceDb.collection('users').get());
});

test('5. Authorized admin can list users/payments/memberships/refunds for Admin Dashboard, while ordinary users are blocked', async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore(DATABASE_ID);
    await adminDb.collection('users').doc('alice_uid').set({ userId: 'alice_uid', planTier: 'MONTHLY_99', membershipStatus: 'ACTIVE' });
    await adminDb.collection('payments').doc('pay_1').set({ userId: 'alice_uid', amountInr: 99, status: 'SUCCESS' });
    await adminDb.collection('refunds').doc('ref_1').set({ userId: 'alice_uid', amountInr: 99, status: 'PROCESSED' });
  });

  const adminCtxDb = testEnv.authenticatedContext('owner_uid', {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore(DATABASE_ID);

  await assertSucceeds(adminCtxDb.collection('users').get());
  await assertSucceeds(adminCtxDb.collection('payments').get());
  await assertSucceeds(adminCtxDb.collection('memberships').get());
  await assertSucceeds(adminCtxDb.collection('refunds').get());

  const ordinaryUserDb = testEnv.authenticatedContext('random_uid', {
    email: 'random@gmail.com',
    email_verified: true,
  }).firestore(DATABASE_ID);

  await assertFails(ordinaryUserDb.collection('users').get());
  await assertFails(ordinaryUserDb.collection('payments').get());
  await assertFails(ordinaryUserDb.collection('refunds').get());
});
