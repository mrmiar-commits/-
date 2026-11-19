const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_gold_123";
const BOB_UID = "bob_gold_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").set({
      userId: ALICE_UID,
      fullName: "Ali",
      phone: "09121112233",
      goldBalanceGrams: 0,
      cashBalanceToman: 0,
      totalPurchasedToman: 0,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create and update UserSettings, ShopAccount, Inventory, Customer, Investor, Transaction, and PriceAlert", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // 1. UserSettings
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      shopName: "طلافروشی زرنگار",
      ownerName: "علی رضایی",
      totpEnabled: false,
      biometricEnabled: false,
      paymentProvider: "ZARINPAL",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Update UserSettings
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totpEnabled: true,
      spreadsheetId: "sheet_abc_123",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 2. ShopAccount
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("accounts").doc("acc_main").set({
      userId: ALICE_UID,
      name: "صندوق نقدی مغازه",
      accountType: "CASH_REGISTER",
      balanceToman: 500000000,
      balanceUsd: 1200,
      goldGrams18k: 150.5,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 3. InventoryItem
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("inventory").doc("inv_1").set({
      userId: ALICE_UID,
      category: "MELTED_GOLD",
      pieceType: "MELTED_BLOCK",
      title: "آبشده پلمپ اتحاد",
      karat: 750,
      weightGrams: 42.3,
      quantity: 1,
      avgBuyPricePerUnit: 4300000,
      defaultWagePercent: 0,
      labCode: "ET-9921",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Edit InventoryItem pieceType, weightGrams, and karat
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("inventory").doc("inv_1").update({
      category: "NEW_GOLD",
      pieceType: "RING",
      title: "انگشتر تک‌نگین ۱۸ عیار",
      karat: 750,
      weightGrams: 12.8,
      quantity: 3,
      avgBuyPricePerUnit: 4550000,
      defaultWagePercent: 12,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 4. Customer
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").set({
      userId: ALICE_UID,
      fullName: "مریم محمدی",
      phone: "09123456789",
      goldBalanceGrams: 0,
      cashBalanceToman: 0,
      totalPurchasedToman: 120000000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 5. Investor
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("investors").doc("investor_1").set({
      userId: ALICE_UID,
      fullName: "حسین کریمی",
      phone: "09129876543",
      goldCategory: "MELTED_GOLD",
      investedGrams: 100.0,
      investedCashToman: 0,
      profitSharePercent: 50.0,
      realizedProfitToman: 15000000,
      contractStatus: "ACTIVE",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 6. GoldTransaction
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").set({
      userId: ALICE_UID,
      txType: "SALE",
      goldCategory: "NEW_GOLD",
      customerId: "cust_1",
      customerName: "مریم محمدی",
      accountId: "acc_main",
      accountName: "صندوق نقدی مغازه",
      weightGrams: 5.4,
      karat: 750,
      unitPriceToman: 4500000,
      wagePercent: 12.0,
      profitMarginPercent: 7.0,
      taxPercent: 9.0,
      costBasisToman: 24300000,
      totalAmountToman: 29500000,
      calculatedProfitToman: 5200000,
      paymentMethod: "GATEWAY",
      paymentStatus: "PENDING_GATEWAY",
      invoiceNumber: "INV-1001",
      sheetsSynced: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Confirm gateway payment update works while status is PENDING_GATEWAY
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").update({
      paymentStatus: "PAID",
      gatewayRefId: "ZP-883211",
      sheetsSynced: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Subsequent mutation to paymentStatus after terminal PAID must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").update({
      paymentStatus: "CREDIT",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // 7. PriceAlert
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("price_alerts").doc("alert_1").set({
      userId: ALICE_UID,
      assetSymbol: "GOLD_18K",
      assetLabel: "طلای ۱۸ عیار",
      condition: "ABOVE",
      targetPrice: 4800000,
      enabled: true,
      triggered: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // List queries match repository paths
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("customers").get());
});

test("Cross-user isolation and security bounds reject tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").set({
      userId: ALICE_UID,
      fullName: "مشتری الف",
      phone: "09121111111",
      goldBalanceGrams: 1.5,
      cashBalanceToman: 0,
      totalPurchasedToman: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob cannot read or write Alice's data
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("customers").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").update({
      fullName: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost `id` field (@DocumentId parity)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_2").set({
      id: "cust_2",
      userId: ALICE_UID,
      fullName: "Ghost ID",
      phone: "09120000000",
      goldBalanceGrams: 0,
      cashBalanceToman: 0,
      totalPurchasedToman: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject future timestamp tampering
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("price_alerts").doc("alert_future").set({
      userId: ALICE_UID,
      assetSymbol: "USD_RATE",
      assetLabel: "دلار",
      condition: "ABOVE",
      targetPrice: 70000,
      enabled: true,
      triggered: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Inventory rejects invalid pieceType, out-of-range karat, negative weight, and non-owner access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Valid item with pieceType
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("inventory").doc("inv_valid").set({
      userId: ALICE_UID,
      category: "BULLION",
      pieceType: "BULLION_BAR",
      title: "شمش ۱۰ گرمی سوئیسی",
      karat: 999,
      weightGrams: 10.0,
      quantity: 1,
      avgBuyPricePerUnit: 6200000,
      defaultWagePercent: 1.5,
      labCode: "CH-999-1",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject invalid pieceType on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("inventory").doc("inv_bad_piece").set({
      userId: ALICE_UID,
      category: "NEW_GOLD",
      pieceType: "INVALID_PIECE",
      title: "قطعه نامعتبر",
      karat: 750,
      weightGrams: 5.0,
      quantity: 1,
      avgBuyPricePerUnit: 4500000,
      defaultWagePercent: 10,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject karat > 1000 on update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("inventory").doc("inv_valid").update({
      karat: 1200,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject negative weight on update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("inventory").doc("inv_valid").update({
      weightGrams: -1.5,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject non-owner read/update/delete
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("inventory").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("inventory").doc("inv_valid").update({
      weightGrams: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("inventory").doc("inv_valid").delete());
});
