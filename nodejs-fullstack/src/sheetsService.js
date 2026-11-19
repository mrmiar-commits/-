'use strict';

const { CATEGORY_LABELS, PIECE_LABELS, ACTION_LABELS, formatDateTimeFa } = require('./voiceParser');

const APPS_SCRIPT_TEMPLATE = `function doPost(e) {
  try {
    var payload = JSON.parse(e.postData.contents);
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    if (payload.mode === 'ping') {
      return ContentService.createTextOutput(JSON.stringify({ ok: true, sheet: ss.getName() }))
        .setMimeType(ContentService.MimeType.JSON);
    }
    if (payload.mode === 'append_voice' && payload.rows) {
      var sh = ss.getSheetByName('Voice_Messages') || ss.insertSheet('Voice_Messages');
      if (sh.getLastRow() === 0) {
        sh.appendRow(['شناسه','تاریخ و ساعت','متن پیام صوتی','عملیات','نوع طلا / ارز','نوع قطعه','وزن (گرم)','عیار','دلار','مبلغ نقدی','مشتری / همکار','فروشنده','ثبت آفلاین']);
      }
      payload.rows.forEach(function(r) { sh.appendRow(r); });
      return ContentService.createTextOutput(JSON.stringify({ ok: true, added: payload.rows.length }))
        .setMimeType(ContentService.MimeType.JSON);
    }
    if (payload.tabs) {
      Object.keys(payload.tabs).forEach(function(name) {
        var rows = payload.tabs[name];
        var sh = ss.getSheetByName(name) || ss.insertSheet(name);
        sh.clearContents();
        if (rows && rows.length > 0) {
          sh.getRange(1, 1, rows.length, rows[0].length).setValues(rows);
        }
      });
    }
    return ContentService.createTextOutput(JSON.stringify({ ok: true }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ ok: false, error: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}`;

function validateWebhookUrl(url) {
  const clean = String(url || '').trim();
  if (!clean.startsWith('https://script.google.com/macros/s/')) {
    throw new Error('لینک وب‌اپ باید با https://script.google.com/macros/s/ شروع شود.');
  }
  return clean;
}

async function postToWebhook(webhookUrl, payload) {
  const url = validateWebhookUrl(webhookUrl);
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(payload),
    redirect: 'follow'
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`کد خطای گوگل شیت: ${res.status}`);
  }
  if (text.toLowerCase().includes('<!doctype html') || text.includes('accounts.google.com')) {
    throw new Error('دسترسی وب‌اپ Google Apps Script باید روی Anyone تنظیم شود.');
  }
  const parsed = JSON.parse(text);
  if (!parsed.ok) {
    throw new Error(parsed.error || 'پاسخ نامعتبر از اسکریپت گوگل شیت');
  }
  return parsed;
}

async function testWebhookConnection(webhookUrl) {
  const res = await postToWebhook(webhookUrl, { mode: 'ping' });
  return res.sheet || 'گوگل شیت زرنگار';
}

function voiceNoteToRow(v) {
  return [
    v.id,
    v.recordedDateTimeFa || formatDateTimeFa(v.recordedAtMillis),
    v.rawTranscript,
    ACTION_LABELS[v.actionType] || v.actionType,
    CATEGORY_LABELS[v.goldCategory] || v.goldCategory,
    PIECE_LABELS[v.pieceType] || v.pieceType,
    v.weightGrams || 0,
    v.karat || 750,
    v.usdAmount || 0,
    v.cashToman || 0,
    v.counterpartyName || 'ثبت نشده',
    v.sellerName || '-',
    v.createdOffline ? 'بله (آفلاین)' : 'خیر (آنلاین)'
  ];
}

async function appendVoiceNotes(webhookUrl, notes) {
  if (!notes.length) return 0;
  const rows = notes.map(voiceNoteToRow);
  const res = await postToWebhook(webhookUrl, { mode: 'append_voice', rows });
  return res.added ?? notes.length;
}

async function syncAllTabs(webhookUrl, db, summary, market) {
  const goldRate = Math.max(1, Number(market.gold18kGramToman) || 1);
  const usdRate = Number(market.usdToToman) || 0;

  const tabs = {
    Summary_Stats: [
      ['شاخص', 'مقدار'],
      ['جمع فروش کل (تومان)', summary.totalSalesToman],
      ['سود ناخالص فروش (تومان)', summary.totalProfitToman],
      ['جمع مخارج مغازه (تومان)', summary.totalExpensesToman],
      ['سود خالص پس از مخارج (تومان)', summary.netProfitAfterExpensesToman],
      ['مجموع طلا (گرم)', summary.goldBreakdown.totalGoldGrams],
      ['طلای آبشده (گرم)', summary.goldBreakdown.meltedGrams],
      ['شمش ۲۴ عیار (گرم)', summary.goldBreakdown.bullionGrams],
      ['طلای نو ویترینی (گرم)', summary.goldBreakdown.newDisplayGrams],
      ['طلای کارکرده بدون اجرت (گرم)', summary.goldBreakdown.usedNoWageGrams],
      ['موجودی کل دلار', summary.goldBreakdown.totalUsdBalance],
      ['معادل طلای ۱۸ عیار کل حساب‌ها (گرم)', Number(summary.totalAccountsGoldEquivGrams.toFixed(3))]
    ],
    Voice_Messages: [
      ['شناسه', 'تاریخ و ساعت', 'متن پیام صوتی', 'عملیات', 'نوع طلا / ارز', 'نوع قطعه', 'وزن (گرم)', 'عیار', 'دلار', 'مبلغ نقدی', 'مشتری / همکار', 'فروشنده', 'ثبت آفلاین'],
      ...db.voiceNotes.map(voiceNoteToRow)
    ],
    Transactions: [
      ['شماره فاکتور', 'تاریخ', 'نوع تراکنش', 'دسته', 'مشتری', 'فروشنده', 'حساب', 'وزن', 'عیار', 'مبلغ کل', 'سود'],
      ...db.transactions.map((t) => [
        t.invoiceNumber,
        formatDateTimeFa(t.timestampMillis),
        t.txType,
        CATEGORY_LABELS[t.goldCategory] || t.goldCategory,
        t.customerName || 'حضوری',
        t.sellerName || '-',
        t.accountName,
        t.weightGrams,
        t.karat,
        t.totalAmountToman,
        t.calculatedProfitToman
      ])
    ],
    Accounts: [
      ['نام حساب / کارت', 'نوع', 'شماره کارت/حساب', 'مانده تومان', 'مانده دلار', 'طلای مستقیم (گرم)', 'معادل طلای ۱۸ عیار (گرم)'],
      ...db.accounts.map((a) => {
        const eq = (a.goldGrams18k || 0) + ((a.balanceToman || 0) / goldRate) + (usdRate > 0 ? ((a.balanceUsd || 0) * usdRate) / goldRate : 0);
        return [a.name, a.accountType, a.accountNumber || '-', a.balanceToman, a.balanceUsd, a.goldGrams18k, Number(eq.toFixed(3))];
      })
    ],
    Sellers: [
      ['نام فروشنده', 'سمت', 'تلفن', 'طلای ورودی', 'طلای خروجی', 'تراز طلا', 'دلار ورودی', 'دلار خروجی', 'تراز دلار'],
      ...db.sellers.map((s) => [s.name, s.roleTitle, s.phone, s.goldInGrams, s.goldOutGrams, Number((s.goldInGrams - s.goldOutGrams).toFixed(3)), s.usdIn, s.usdOut, Number((s.usdIn - s.usdOut).toFixed(2))])
    ],
    Seller_Movements: [
      ['تاریخ', 'فروشنده', 'جهت', 'دسته', 'وزن (گرم)', 'عیار', 'دلار', 'تومان', 'شرح'],
      ...db.sellerMovements.map((m) => [formatDateTimeFa(m.timestampMillis), m.sellerName, m.direction, CATEGORY_LABELS[m.goldCategory] || m.goldCategory, m.weightGrams, m.karat, m.usdAmount, m.cashToman, m.description])
    ],
    Expenses: [
      ['تاریخ', 'عنوان هزینه', 'دسته‌بندی', 'مبلغ (تومان)', 'معادل طلا (گرم)', 'حساب', 'فروشنده', 'توضیحات'],
      ...db.expenses.map((e) => [formatDateTimeFa(e.timestampMillis), e.title, e.category, e.amountToman, Number((e.goldEquivalentGrams || 0).toFixed(3)), e.accountName, e.sellerName || '-', e.notes || '-'])
    ],
    Inventory: [
      ['عنوان', 'نوع طلا', 'نوع قطعه', 'وزن (گرم)', 'عیار', 'تعداد', 'میانگین خرید', 'کد ری‌گیری'],
      ...db.inventory.map((i) => [i.title, CATEGORY_LABELS[i.category] || i.category, PIECE_LABELS[i.pieceType] || i.pieceType, i.weightGrams, i.karat, i.quantity, i.avgBuyPricePerUnit, i.labCode || '-'])
    ],
    Customers: [
      ['نام مشتری', 'تلفن', 'جمع خرید', 'تراز طلا', 'توضیحات'],
      ...db.customers.map((c) => [c.fullName, c.phone, c.totalPurchasedToman, c.goldBalanceGrams, c.notes || '-'])
    ],
    Investors: [
      ['نام سرمایه‌گذار', 'تلفن', 'نوع طلا', 'سرمایه طلایی', 'سرمایه نقدی', 'سهم سود٪', 'سود پرداختی'],
      ...db.investors.map((inv) => [inv.fullName, inv.phone, CATEGORY_LABELS[inv.goldCategory] || inv.goldCategory, inv.investedGrams, inv.investedCashToman, inv.profitSharePercent, inv.realizedProfitToman])
    ]
  };

  await postToWebhook(webhookUrl, { mode: 'sync_all', tabs });
  return Object.values(tabs).reduce((sum, rows) => sum + Math.max(0, rows.length - 1), 0);
}

function buildCsvExport(db) {
  const esc = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
  const lines = [];
  lines.push(['بخش', 'تاریخ', 'شرح', 'دسته', 'طرف حساب / فروشنده', 'وزن (گرم)', 'دلار', 'مبلغ (تومان)'].map(esc).join(','));
  for (const t of db.transactions) {
    lines.push(['تراکنش', formatDateTimeFa(t.timestampMillis), t.invoiceNumber, CATEGORY_LABELS[t.goldCategory] || t.goldCategory, t.customerName || t.sellerName || '-', t.weightGrams, 0, t.totalAmountToman].map(esc).join(','));
  }
  for (const e of db.expenses) {
    lines.push(['مخارج', formatDateTimeFa(e.timestampMillis), e.title, e.category, e.accountName, e.goldEquivalentGrams, 0, -e.amountToman].map(esc).join(','));
  }
  for (const v of db.voiceNotes) {
    lines.push(['پیام صوتی', v.recordedDateTimeFa, v.rawTranscript, CATEGORY_LABELS[v.goldCategory] || v.goldCategory, v.counterpartyName, v.weightGrams, v.usdAmount, v.cashToman].map(esc).join(','));
  }
  return '\uFEFF' + lines.join('\n');
}

module.exports = {
  APPS_SCRIPT_TEMPLATE,
  testWebhookConnection,
  appendVoiceNotes,
  syncAllTabs,
  buildCsvExport
};
