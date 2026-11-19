'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createAppServer } = require('./server');
const { parseVoiceMessage } = require('./src/voiceParser');

test('VoiceMessageParser parses "1.5 گرم آبشده به رضا" accurately', () => {
  const parsed = parseVoiceMessage('1.5 گرم آبشده به رضا');
  assert.equal(parsed.weightGrams, 1.5);
  assert.equal(parsed.goldCategory, 'MELTED_GOLD');
  assert.equal(parsed.actionType, 'SALE_OUT');
  assert.equal(parsed.counterpartyName, 'رضا');
  assert.ok(parsed.recordedDateTimeFa.length > 0);
});

test('Node.js REST API end-to-end: accounts gold equivalent, sellers limit, expenses, voice, reports, and frontpage', async () => {
  const tmpDb = path.join(os.tmpdir(), `zarnegar-test-${Date.now()}.json`);
  const server = createAppServer(tmpDb);

  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  const baseUrl = `http://127.0.0.1:${port}`;

  try {
    // 1. Frontpage HTML serves cleanly
    const htmlRes = await fetch(`${baseUrl}/`);
    assert.equal(htmlRes.status, 200);
    const html = await htmlRes.text();
    assert.ok(html.includes('طلافروشی زرنگار'));

    // 2. Manual gold price update
    const priceRes = await fetch(`${baseUrl}/api/pricing`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        useManualPrice: true,
        manualGold18kToman: 7_000_000,
        manualUsdToman: 70_000
      })
    });
    assert.equal(priceRes.status, 200);

    // 3. Add personal bank card and verify 18K gold gram equivalent
    const accRes = await fetch(`${baseUrl}/api/accounts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: 'کارت شخصی ملت',
        accountType: 'PERSONAL_CARD',
        balanceToman: 14_000_000,
        balanceUsd: 100,
        goldGrams18k: 1
      })
    });
    assert.equal(accRes.status, 201);
    const acc = await accRes.json();
    // 14M / 7M = 2g + (100 * 70k / 7M = 1g) + 1g = 4.0g
    assert.equal(acc.goldEquivalent18kGrams, 4);

    // 4. Add 5 sellers and verify 6th seller is rejected
    let firstSellerId = '';
    for (let i = 1; i <= 5; i++) {
      const sRes = await fetch(`${baseUrl}/api/sellers`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: `فروشنده ${i}` })
      });
      assert.equal(sRes.status, 201);
      const s = await sRes.json();
      if (i === 1) firstSellerId = s.id;
    }
    const sixthRes = await fetch(`${baseUrl}/api/sellers`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'فروشنده ۶' })
    });
    assert.equal(sixthRes.status, 400);

    // 5. Record seller gold & USD movement
    const mvRes = await fetch(`${baseUrl}/api/sellers/${firstSellerId}/movements`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        direction: 'IN',
        goldCategory: 'MELTED_GOLD',
        weightGrams: 5.5,
        usdAmount: 300,
        description: 'تحویل آبشده و دلار'
      })
    });
    assert.equal(mvRes.status, 201);

    // 6. Record shop expense & verify net profit impact in report
    const expRes = await fetch(`${baseUrl}/api/expenses`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: 'قبض برق مغازه',
        category: 'UTILITIES',
        amountToman: 700_000,
        accountId: acc.id
      })
    });
    assert.equal(expRes.status, 201);
    const exp = await expRes.json();
    assert.equal(exp.goldEquivalentGrams, 0.1);

    // 7. Save voice note & verify keyword extraction
    const vRes = await fetch(`${baseUrl}/api/voice`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ rawTranscript: '1.5 گرم آبشده به رضا' })
    });
    assert.equal(vRes.status, 201);
    const vData = await vRes.json();
    assert.equal(vData.note.weightGrams, 1.5);
    assert.equal(vData.note.counterpartyName, 'رضا');

    // 8. Financial report endpoint
    const repRes = await fetch(`${baseUrl}/api/reports?period=ALL`);
    assert.equal(repRes.status, 200);
    const report = await repRes.json();
    assert.equal(report.expensesTotalToman, 700_000);
    assert.equal(report.sellers.length, 5);
  } finally {
    server.close();
    if (fs.existsSync(tmpDb)) fs.unlinkSync(tmpDb);
  }
});
