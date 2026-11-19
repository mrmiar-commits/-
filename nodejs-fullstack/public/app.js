'use strict';

const OFFLINE_QUEUE_KEY = 'zarnegar_offline_voice_queue_v1';

const state = {
  bootstrap: null,
  reportPeriod: 'MONTH',
  recognition: null,
  isListening: false
};

const LABELS = {
  category: {
    NEW_GOLD: 'طلای نو ویترینی',
    USED_NO_WAGE: 'طلای کارکرده بدون اجرت',
    MELTED_GOLD: 'طلای آبشده',
    BULLION: 'شمش ۲۴ عیار',
    USD_CURRENCY: 'دلار و ارز',
    OTHER_GOLD: 'سایر انواع طلا',
    NONE: 'انتقال / نقدی'
  },
  accountType: {
    CASH_REGISTER: 'صندوق نقدی مغازه',
    BANK_POS: 'حساب بانکی / کارتخوان',
    PERSONAL_CARD: 'کارت بانکی شخصی',
    PETTY_CASH: 'تنخواه مغازه',
    GOLD_SAFE: 'گاوصندوق طلا',
    CUSTOM: 'سایر حساب / کارت'
  },
  txType: {
    SALE: 'فروش روزانه',
    PURCHASE: 'خرید',
    TRANSFER_FROM_SHOP: 'انتقال از مغازه',
    TRANSFER_TO_SHOP: 'انتقال به مغازه',
    INVESTOR_DEPOSIT: 'ورود سرمایه',
    INVESTOR_PAYOUT: 'تسویه سرمایه‌گذار'
  },
  expense: {
    RENT: 'اجاره مغازه',
    UTILITIES: 'قبوض',
    SALARY: 'حقوق و پورسانت',
    SUPPLIES: 'ملزومات و پذیرایی',
    SECURITY_REPAIR: 'تعمیرات و امنیت',
    TRANSPORT: 'حمل و نقل و پیک',
    TAX_UNION: 'مالیات و اتحادیه',
    OTHER: 'سایر مخارج'
  }
};

function toFa(v) {
  const d = ['۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'];
  return String(v).replace(/[0-9]/g, (n) => d[Number(n)]);
}

function fmtToman(n, unit = true) {
  const num = Math.round(Number(n) || 0);
  const formatted = toFa(Math.abs(num).toLocaleString('en-US'));
  return `${num < 0 ? '-' : ''}${formatted}${unit ? ' تومان' : ''}`;
}

function fmtGrams(n) {
  const num = Number(n) || 0;
  return `${toFa(Number(num.toFixed(3)))} گرم`;
}

function fmtUsd(n) {
  return `$${(Number(n) || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function showToast(msg) {
  const el = document.getElementById('toast-bar');
  el.textContent = msg;
  el.classList.remove('hidden');
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => el.classList.add('hidden'), 3800);
}

async function api(url, options = {}) {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `خطای سرور (${res.status})`);
  return data;
}

function applyTheme(dark) {
  document.documentElement.classList.toggle('dark', Boolean(dark));
  localStorage.setItem('zarnegar_dark', dark ? '1' : '0');
}

function loadOfflineQueue() {
  try {
    return JSON.parse(localStorage.getItem(OFFLINE_QUEUE_KEY) || '[]');
  } catch {
    return [];
  }
}

function saveOfflineQueue(items) {
  localStorage.setItem(OFFLINE_QUEUE_KEY, JSON.stringify(items));
}

async function flushBrowserOfflineVoiceQueue() {
  const queued = loadOfflineQueue();
  if (!queued.length || !navigator.onLine) return;
  const remaining = [];
  let sent = 0;
  for (const item of queued) {
    try {
      await api('/api/voice', { method: 'POST', body: JSON.stringify(item) });
      sent++;
    } catch {
      remaining.push(item);
    }
  }
  saveOfflineQueue(remaining);
  if (sent > 0) {
    showToast(`${toFa(sent)} پیام صوتی آفلاین پس از اتصال نت ثبت و ارسال شد.`);
    await loadAll();
  }
}

function switchSection(sectionName) {
  document.querySelectorAll('.nav-btn').forEach((b) => b.classList.toggle('active', b.dataset.section === sectionName));
  document.querySelectorAll('.section').forEach((s) => s.classList.toggle('active', s.id === `sec-${sectionName}`));
  if (sectionName === 'reports') loadReport(state.reportPeriod);
}

async function loadAll(forceRefresh = false) {
  try {
    const data = await api(`/api/bootstrap${forceRefresh ? '?refresh=1' : ''}`);
    state.bootstrap = data;
    renderAll();
  } catch (e) {
    showToast(e.message);
  }
}

function renderAll() {
  const b = state.bootstrap;
  if (!b) return;
  const { settings, market, summary, accounts, inventory, sellers, sellerMovements, transactions, expenses, customers, investors, voiceNotes, pricing } = b;

  document.getElementById('shop-name-title').textContent = settings.shopName || 'طلافروشی زرنگار';
  document.getElementById('market-status-line').textContent = `${market.isManualOverride ? 'نرخ دستی' : market.isLiveNetwork ? 'استعلام آنلاین API' : 'حالت آفلاین'} • ${market.lastUpdatedText}`;

  document.getElementById('rate-18k').textContent = fmtToman(market.gold18kGramToman, false);
  document.getElementById('rate-mesghal').textContent = fmtToman(market.meltedMesghalToman, false);
  document.getElementById('rate-24k').textContent = fmtToman(market.gold24kGramToman, false);
  document.getElementById('rate-usd').textContent = fmtToman(market.usdToToman, false);

  const txUnit = document.getElementById('tx-unit-price');
  if (txUnit && !txUnit.value) txUnit.value = market.gold18kGramToman;

  // Offline voice badge
  const localQueueCount = loadOfflineQueue().length;
  const totalPending = (summary.pendingVoiceSyncCount || 0) + localQueueCount;
  const badge = document.getElementById('offline-queue-badge');
  if (totalPending > 0) {
    badge.textContent = `${toFa(totalPending)} در صف شیت`;
    badge.classList.remove('hidden');
  } else {
    badge.classList.add('hidden');
  }

  // Gold Breakdown Card
  const gb = summary.goldBreakdown;
  document.getElementById('dash-usd-pill').textContent = fmtUsd(gb.totalUsdBalance);
  document.getElementById('gold-breakdown-list').innerHTML = [
    ['طلای آبشده', fmtGrams(gb.meltedGrams)],
    ['شمش طلا (۲۴ عیار)', fmtGrams(gb.bullionGrams)],
    ['طلای نو ویترینی', fmtGrams(gb.newDisplayGrams)],
    ['طلای کارکرده بدون اجرت', fmtGrams(gb.usedNoWageGrams)],
    ['سایر انواع طلا', fmtGrams(gb.otherGoldGrams)],
    ['جمع کل طلا', `<strong class="gold">${fmtGrams(gb.totalGoldGrams)}</strong>`]
  ].map(([k, v]) => `<div class="kv-row"><span>${k}</span><span class="mono">${v}</span></div>`).join('');

  // KPIs
  document.getElementById('kpi-today-sales').textContent = fmtToman(summary.todaySalesToman, false);
  document.getElementById('kpi-today-profit').textContent = `سود امروز: ${fmtToman(summary.todayProfitToman, false)}`;
  const netEl = document.getElementById('kpi-net-profit');
  netEl.textContent = fmtToman(summary.netProfitAfterExpensesToman, false);
  netEl.className = `mono ${summary.netProfitAfterExpensesToman >= 0 ? 'profit' : 'loss'}`;
  document.getElementById('kpi-expenses').textContent = `مخارج: ${fmtToman(summary.totalExpensesToman, false)}`;
  document.getElementById('kpi-accounts-toman').textContent = fmtToman(summary.totalCashAccountsToman, false);
  document.getElementById('kpi-accounts-gold').textContent = `معادل طلا: ${fmtGrams(summary.totalAccountsGoldEquivGrams)}`;
  document.getElementById('kpi-inventory-grams').textContent = fmtGrams(summary.totalInventoryGrams);
  document.getElementById('kpi-investor-grams').textContent = `سرمایه‌گذاران: ${fmtGrams(summary.totalInvestorGrams)}`;

  // Recent & All Transactions
  const renderTx = (t) => `
    <div class="item-row">
      <div>
        <strong>${LABELS.txType[t.txType] || t.txType} • ${LABELS.category[t.goldCategory] || t.goldCategory}</strong>
        <div class="muted-xs">${t.invoiceNumber} • ${t.accountName}${t.customerName ? ` • مشتری: ${t.customerName}` : ''}${t.sellerName ? ` • فروشنده: ${t.sellerName}` : ''}</div>
      </div>
      <div class="text-left">
        <strong class="mono ${t.txType === 'SALE' || t.txType === 'TRANSFER_TO_SHOP' ? 'profit' : 'loss'}">${fmtToman(t.totalAmountToman)}</strong>
        <div class="muted-xs mono">${fmtGrams(t.weightGrams)}</div>
      </div>
    </div>`;
  document.getElementById('recent-tx-list').innerHTML = transactions.slice(0, 5).map(renderTx).join('') || '<p class="muted-sm">هنوز تراکنشی ثبت نشده است.</p>';
  document.getElementById('all-tx-list').innerHTML = transactions.map(renderTx).join('') || '<p class="muted-sm">تراکنشی وجود ندارد.</p>';

  // Accounts + Gold Equivalent
  document.getElementById('accounts-sum-toman').textContent = fmtToman(summary.totalCashAccountsToman);
  document.getElementById('accounts-sum-gold').textContent = fmtGrams(summary.totalAccountsGoldEquivGrams);
  document.getElementById('accounts-list').innerHTML = accounts.map((a) => `
    <div class="item-row">
      <div>
        <strong>${a.name}</strong>
        <div class="muted-xs">${LABELS.accountType[a.accountType] || a.accountType}${a.accountNumber ? ` • ${toFa(a.accountNumber)}` : ''}</div>
        <div class="muted-xs">نقدی: <span class="mono">${fmtToman(a.balanceToman)}</span> | دلار: <span class="mono info">${fmtUsd(a.balanceUsd)}</span></div>
      </div>
      <div class="text-left">
        <div class="gold mono"><strong>≈ ${fmtGrams(a.goldEquivalent18kGrams)}</strong></div>
        <button class="btn-danger-sm" data-del-account="${a.id}">حذف</button>
      </div>
    </div>`).join('') || '<p class="muted-sm">حسابی ثبت نشده است.</p>';

  // Expenses
  const expGoldTotal = market.gold18kGramToman > 0 ? summary.totalExpensesToman / market.gold18kGramToman : 0;
  document.getElementById('expenses-sum-toman').textContent = fmtToman(summary.totalExpensesToman);
  document.getElementById('expenses-sum-gold').textContent = fmtGrams(expGoldTotal);
  document.getElementById('expenses-list').innerHTML = expenses.map((e) => `
    <div class="item-row">
      <div>
        <strong>${e.title}</strong>
        <div class="muted-xs">${LABELS.expense[e.category] || e.category} • از ${e.accountName}${e.sellerName ? ` • ${e.sellerName}` : ''}</div>
      </div>
      <div class="text-left">
        <strong class="mono loss">${fmtToman(e.amountToman)}</strong>
        <div class="muted-xs gold mono">${fmtGrams(e.goldEquivalentGrams)}</div>
        <button class="btn-danger-sm" data-del-expense="${e.id}">حذف</button>
      </div>
    </div>`).join('') || '<p class="muted-sm">هزینه‌ای ثبت نشده است.</p>';

  // Inventory
  document.getElementById('inventory-list').innerHTML = inventory.map((i) => `
    <div class="item-row">
      <div>
        <strong>${i.title}</strong>
        <div class="muted-xs">${LABELS.category[i.category] || i.category} • عیار ${toFa(i.karat)}${i.labCode ? ` • انگ: ${i.labCode}` : ''}</div>
      </div>
      <div class="text-left">
        <strong class="mono gold">${i.category === 'USD_CURRENCY' ? fmtUsd(i.quantity) : fmtGrams(i.weightGrams)}</strong>
        <div class="muted-xs">تعداد: ${toFa(i.quantity)}</div>
        <button class="btn-danger-sm" data-del-inv="${i.id}">حذف</button>
      </div>
    </div>`).join('') || '<p class="muted-sm">انبار خالی است.</p>';

  // Sellers (3-5)
  document.getElementById('sellers-count-pill').textContent = `${toFa(sellers.length)} از ۵ فروشنده`;
  document.getElementById('sellers-list').innerHTML = sellers.map((s) => `
    <div class="item-row">
      <div>
        <strong>${s.name}</strong> <span class="muted-xs">(${s.roleTitle} • ${toFa(s.phone)})</span>
        <div class="muted-xs">طلا: <span class="profit">+${fmtGrams(s.goldInGrams)}</span> / <span class="loss">-${fmtGrams(s.goldOutGrams)}</span> | تراز: <strong class="gold">${fmtGrams(s.goldInGrams - s.goldOutGrams)}</strong></div>
        <div class="muted-xs">دلار: <span class="profit">+${fmtUsd(s.usdIn)}</span> / <span class="loss">-${fmtUsd(s.usdOut)}</span> | تراز: <strong class="info">${fmtUsd(s.usdIn - s.usdOut)}</strong></div>
      </div>
      <button class="btn-danger-sm" data-del-seller="${s.id}">حذف</button>
    </div>`).join('') || '<p class="muted-sm">فروشنده‌ای تعریف نشده است (حداکثر ۵ نفر).</p>';

  document.getElementById('seller-movements-list').innerHTML = sellerMovements.slice(0, 15).map((m) => `
    <div class="item-row">
      <div>
        <strong class="${m.direction === 'IN' ? 'profit' : 'loss'}">${m.sellerName} • ${m.direction === 'IN' ? 'ورود به مغازه' : 'خروج از مغازه'}</strong>
        <div class="muted-xs">${LABELS.category[m.goldCategory] || m.goldCategory} • ${m.description}</div>
      </div>
      <div class="text-left mono">
        ${m.weightGrams > 0 ? `<div class="gold">${fmtGrams(m.weightGrams)}</div>` : ''}
        ${m.usdAmount > 0 ? `<div class="info">${fmtUsd(m.usdAmount)}</div>` : ''}
      </div>
    </div>`).join('') || '<p class="muted-sm">هنوز ورود یا خروجی ثبت نشده است.</p>';

  // Customers & Investors
  document.getElementById('customers-list').innerHTML = customers.map((c) => `
    <div class="item-row">
      <div><strong>${c.fullName}</strong><div class="muted-xs">${toFa(c.phone)} • خرید کل: ${fmtToman(c.totalPurchasedToman)}</div></div>
      <div class="row-gap"><span class="gold mono">${fmtGrams(c.goldBalanceGrams)}</span><button class="btn-danger-sm" data-del-customer="${c.id}">حذف</button></div>
    </div>`).join('') || '<p class="muted-sm">مشتری ثبت نشده است.</p>';

  document.getElementById('investors-list').innerHTML = investors.map((inv) => `
    <div class="item-row">
      <div><strong>${inv.fullName}</strong><div class="muted-xs">${LABELS.category[inv.goldCategory] || inv.goldCategory} • سهم سود ${toFa(inv.profitSharePercent)}٪</div></div>
      <div class="text-left">
        <div class="gold mono">${fmtGrams(inv.investedGrams)}</div>
        <div class="profit muted-xs">سود پرداختی: ${fmtToman(inv.realizedProfitToman, false)}</div>
        <button class="btn-danger-sm" data-del-investor="${inv.id}">حذف</button>
      </div>
    </div>`).join('') || '<p class="muted-sm">سرمایه‌گذاری ثبت نشده است.</p>';

  // Voice History
  document.getElementById('voice-pending-info').textContent = totalPending > 0
    ? `${toFa(totalPending)} پیام در صف ارسال به گوگل شیت`
    : 'همه پیام‌ها در گوگل شیت همگام هستند';

  document.getElementById('voice-history-list').innerHTML = voiceNotes.map((v) => `
    <div class="item-row">
      <div>
        <strong>«${v.rawTranscript}»</strong>
        <div class="muted-xs">${v.actionLabelFa || v.actionType} • ${v.categoryLabelFa || v.goldCategory} • طرف حساب: <strong>${v.counterpartyName}</strong></div>
        <div class="muted-xs">${v.recordedDateTimeFa}${v.sellerName ? ` • فروشنده: ${v.sellerName}` : ''}</div>
      </div>
      <div class="text-left">
        <span class="pill ${v.sheetsSynced ? 'profit-pill' : 'amber-pill'}">${v.sheetsSynced ? 'ثبت در شیت' : 'در صف ارسال'}</span>
        <div class="mono gold">${v.weightGrams > 0 ? fmtGrams(v.weightGrams) : v.usdAmount > 0 ? fmtUsd(v.usdAmount) : fmtToman(v.cashToman)}</div>
        <button class="btn-danger-sm" data-del-voice="${v.id}">حذف</button>
      </div>
    </div>`).join('') || '<p class="muted-sm">پیام صوتی ثبت نشده است.</p>';

  // Populate Dropdowns
  const accOpts = accounts.map((a) => `<option value="${a.id}">${a.name}</option>`).join('');
  document.getElementById('tx-account-select').innerHTML = accOpts;
  document.getElementById('expense-account-select').innerHTML = accOpts;

  const sellerOptsWithEmpty = '<option value="">مدیر مغازه / عمومی</option>' + sellers.map((s) => `<option value="${s.id}">${s.name}</option>`).join('');
  const sellerNameOpts = '<option value="">مدیر مغازه</option>' + sellers.map((s) => `<option value="${s.name}">${s.name}</option>`).join('');
  document.getElementById('tx-seller-select').innerHTML = sellerOptsWithEmpty;
  document.getElementById('expense-seller-select').innerHTML = sellerNameOpts;
  document.getElementById('voice-seller-select').innerHTML = sellerNameOpts;
  document.getElementById('movement-seller-select').innerHTML = sellers.map((s) => `<option value="${s.id}">${s.name}</option>`).join('') || '<option value="">ابتدا فروشنده تعریف کنید</option>';

  // Settings & Pricing Form Values
  document.getElementById('sheets-webhook-input').value = settings.sheetsWebhookUrl || '';
  document.getElementById('set-shop-name').value = settings.shopName || '';
  document.getElementById('set-owner-name').value = settings.ownerName || '';
  document.getElementById('set-payment-provider').value = settings.paymentProvider || 'ZARINPAL';
  document.getElementById('set-merchant-id').value = settings.merchantId || '';

  document.getElementById('use-manual-price').checked = Boolean(pricing.useManualPrice);
  document.getElementById('pm-usd').value = pricing.manualUsdToman || market.usdToToman;
  document.getElementById('pm-18k').value = pricing.manualGold18kToman || market.gold18kGramToman;
  document.getElementById('pm-mesghal').value = pricing.manualMeltedMesghalToman || market.meltedMesghalToman;
  document.getElementById('pm-24k').value = pricing.manualBullion24kToman || market.gold24kGramToman;
}

async function loadReport(period) {
  state.reportPeriod = period;
  try {
    const r = await api(`/api/reports?period=${encodeURIComponent(period)}`);
    const container = document.getElementById('report-cards-container');
    const expRows = Object.entries(r.expensesByCategory).map(([k, v]) => `<div class="kv-row"><span>${LABELS.expense[k] || k}</span><span class="mono loss">${fmtToman(v)}</span></div>`).join('') || '<p class="muted-sm">هزینه‌ای در این بازه ثبت نشده است.</p>';
    const sellerRows = r.sellers.map((s) => `<div class="kv-row"><span>${s.name}</span><span class="mono">طلا: ${fmtGrams(s.netGoldGrams)} | دلار: ${fmtUsd(s.netUsd)}</span></div>`).join('') || '<p class="muted-sm">فروشنده‌ای ثبت نشده است.</p>';
    const accRows = r.accounts.map((a) => `<div class="kv-row"><span>${a.name}</span><span class="mono gold">${fmtToman(a.balanceToman, false)} ≈ ${fmtGrams(a.goldEquivalent18kGrams)}</span></div>`).join('');

    container.innerHTML = `
      <div class="card accent-card">
        <h3>تراز سود و زیان (${r.generatedAtFa})</h3>
        <div class="divider"></div>
        <div class="kv-list">
          <div class="kv-row"><span>جمع فروش</span><strong class="mono">${fmtToman(r.salesTotalToman)}</strong></div>
          <div class="kv-row"><span>جمع خرید</span><span class="mono">${fmtToman(r.purchasesTotalToman)}</span></div>
          <div class="kv-row"><span>سود ناخالص فروش</span><strong class="mono profit">${fmtToman(r.grossProfitToman)}</strong></div>
          <div class="kv-row"><span>مخارج مغازه</span><strong class="mono loss">${fmtToman(r.expensesTotalToman)}</strong></div>
          <div class="divider"></div>
          <div class="kv-row"><span>سود خالص پس از مخارج</span><strong class="mono ${r.netProfitToman >= 0 ? 'profit' : 'loss'}">${fmtToman(r.netProfitToman)}</strong></div>
          <div class="kv-row"><span>معادل طلای ۱۸ عیار سود خالص</span><strong class="mono gold">${fmtGrams(r.netProfitGold18kGrams)}</strong></div>
        </div>
      </div>
      <div class="card">
        <h3>تفکیک موجودی طلا و دلار</h3>
        <div class="divider"></div>
        <div class="kv-list">
          <div class="kv-row"><span>طلای آبشده</span><span class="mono">${fmtGrams(r.goldBreakdown.meltedGrams)}</span></div>
          <div class="kv-row"><span>شمش ۲۴ عیار</span><span class="mono">${fmtGrams(r.goldBreakdown.bullionGrams)}</span></div>
          <div class="kv-row"><span>طلای نو ویترینی</span><span class="mono">${fmtGrams(r.goldBreakdown.newDisplayGrams)}</span></div>
          <div class="kv-row"><span>کارکرده بدون اجرت</span><span class="mono">${fmtGrams(r.goldBreakdown.usedNoWageGrams)}</span></div>
          <div class="kv-row"><span>جمع کل طلا</span><strong class="mono gold">${fmtGrams(r.goldBreakdown.totalGoldGrams)}</strong></div>
          <div class="kv-row"><span>موجودی دلار</span><strong class="mono info">${fmtUsd(r.goldBreakdown.totalUsdBalance)}</strong></div>
        </div>
      </div>
      <div class="card">
        <h3>تفکیک مخارج مغازه</h3>
        <div class="divider"></div>
        <div class="kv-list">${expRows}</div>
      </div>
      <div class="card">
        <h3>تراز فروشندگان و معادل طلای حساب‌ها</h3>
        <div class="divider"></div>
        <div class="kv-list">${sellerRows}<div class="divider"></div>${accRows}</div>
      </div>`;
  } catch (e) {
    showToast(e.message);
  }
}

async function previewVoiceParse() {
  const raw = document.getElementById('voice-transcript').value.trim();
  const box = document.getElementById('voice-parsed-preview');
  if (!raw) {
    box.innerHTML = '<p class="muted-sm">با وارد کردن یا گفتن پیام، وزن، نوع طلا، طرف حساب و تاریخ به‌صورت آنی استخراج می‌شود.</p>';
    return;
  }
  try {
    const sellerName = document.getElementById('voice-seller-select').value || null;
    const p = await api('/api/voice/parse', { method: 'POST', body: JSON.stringify({ rawTranscript: raw, sellerName }) });
    box.innerHTML = `
      ${p.weightGrams > 0 ? `<div class="kv-row"><span>وزن طلا</span><strong class="mono gold">${fmtGrams(p.weightGrams)} (عیار ${toFa(p.karat)})</strong></div>` : ''}
      ${p.usdAmount > 0 ? `<div class="kv-row"><span>مقدار دلار</span><strong class="mono info">${fmtUsd(p.usdAmount)}</strong></div>` : ''}
      ${p.cashToman > 0 ? `<div class="kv-row"><span>مبلغ نقدی</span><strong class="mono">${fmtToman(p.cashToman)}</strong></div>` : ''}
      <div class="kv-row"><span>نوع طلا / ارز</span><strong>${p.categoryLabelFa} (${p.pieceLabelFa})</strong></div>
      <div class="kv-row"><span>عملیات</span><strong>${p.actionLabelFa}</strong></div>
      <div class="kv-row"><span>مشتری / همکار</span><strong class="gold">${p.counterpartyName}</strong></div>
      <div class="kv-row"><span>تاریخ و ساعت</span><span class="mono">${p.recordedDateTimeFa}</span></div>`;
  } catch {
    box.innerHTML = '<p class="muted-sm">حالت آفلاین: پیام پس از ذخیره تحلیل و در صف گوگل شیت قرار می‌گیرد.</p>';
  }
}

function setupSpeechRecognition() {
  const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
  const micBtn = document.getElementById('mic-record-btn');
  const statusEl = document.getElementById('mic-status-text');
  if (!SpeechRec) {
    micBtn.addEventListener('click', () => {
      statusEl.textContent = 'مرورگر شما ضبط صوتی مستقیم ندارد؛ متن پیام را در کادر زیر تایپ کنید.';
    });
    return;
  }
  const rec = new SpeechRec();
  rec.lang = 'fa-IR';
  rec.interimResults = true;
  rec.continuous = false;
  rec.onstart = () => {
    state.isListening = true;
    micBtn.classList.add('listening');
    statusEl.textContent = 'در حال گوش دادن... (مثلاً: ۱.۵ گرم آبشده به رضا)';
  };
  rec.onend = () => {
    state.isListening = false;
    micBtn.classList.remove('listening');
    statusEl.textContent = 'آماده دریافت پیام صوتی یا متنی';
    previewVoiceParse();
  };
  rec.onerror = () => {
    state.isListening = false;
    micBtn.classList.remove('listening');
    statusEl.textContent = 'دسترسی میکروفون یا سرویس گفتار در دسترس نبود؛ می‌توانید متن را تایپ کنید.';
  };
  rec.onresult = (ev) => {
    let text = '';
    for (let i = 0; i < ev.results.length; i++) text += ev.results[i][0].transcript;
    document.getElementById('voice-transcript').value = text.trim();
    previewVoiceParse();
  };
  micBtn.addEventListener('click', () => {
    if (state.isListening) rec.stop();
    else rec.start();
  });
}

function updateNetworkBadge() {
  const el = document.getElementById('network-status-pill');
  if (navigator.onLine) {
    el.textContent = 'آنلاین';
    el.className = 'pill profit-pill';
    flushBrowserOfflineVoiceQueue();
  } else {
    el.textContent = 'آفلاین (ذخیره در صف)';
    el.className = 'pill loss-pill';
  }
}

function wireEvents() {
  // Navigation
  document.querySelectorAll('.nav-btn').forEach((btn) => {
    btn.addEventListener('click', () => switchSection(btn.dataset.section));
  });
  document.querySelectorAll('[data-jump]').forEach((btn) => {
    btn.addEventListener('click', () => switchSection(btn.dataset.jump));
  });
  document.getElementById('quick-voice-btn').addEventListener('click', () => switchSection('voice'));

  // Segmented Sub-tabs
  document.querySelectorAll('.segmented').forEach((seg) => {
    const group = seg.dataset.tabs;
    seg.querySelectorAll('.seg-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        seg.querySelectorAll('.seg-btn').forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');
        document.querySelectorAll(`#sec-${group} .subtab`).forEach((st) => st.classList.remove('active'));
        document.getElementById(`${group}-${btn.dataset.subtab}`).classList.add('active');
      });
    });
  });

  // Theme & Refresh
  document.getElementById('theme-toggle-btn').addEventListener('click', async () => {
    const next = !document.documentElement.classList.contains('dark');
    applyTheme(next);
    try { await api('/api/settings', { method: 'PUT', body: JSON.stringify({ darkTheme: next }) }); } catch {}
  });
  document.getElementById('refresh-all-btn').addEventListener('click', async () => {
    await loadAll(true);
    showToast('نرخ طلا و ارز بروزرسانی شد.');
  });

  // Pricing Modal
  const modal = document.getElementById('pricing-modal');
  document.getElementById('open-pricing-btn').addEventListener('click', () => modal.classList.remove('hidden'));
  document.getElementById('close-pricing-modal').addEventListener('click', () => modal.classList.add('hidden'));
  document.getElementById('pm-18k').addEventListener('input', (e) => {
    const v = Number(e.target.value) || 0;
    if (v > 0) {
      document.getElementById('pm-mesghal').value = Math.round(v * 4.3318);
      document.getElementById('pm-24k').value = Math.round(v * (999 / 750));
    }
  });
  document.getElementById('pricing-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
      await api('/api/pricing', {
        method: 'PUT',
        body: JSON.stringify({
          useManualPrice: document.getElementById('use-manual-price').checked,
          manualUsdToman: Number(fd.get('manualUsdToman')) || 0,
          manualGold18kToman: Number(fd.get('manualGold18kToman')) || 0,
          manualMeltedMesghalToman: Number(fd.get('manualMeltedMesghalToman')) || 0,
          manualBullion24kToman: Number(fd.get('manualBullion24kToman')) || 0
        })
      });
      modal.classList.add('hidden');
      await loadAll(true);
      showToast('منبع نرخ طلا و دلار با موفقیت ذخیره شد.');
    } catch (err) { showToast(err.message); }
  });

  // Form Submissions
  document.getElementById('tx-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const body = Object.fromEntries(new FormData(e.target).entries());
    const sel = document.getElementById('tx-seller-select');
    if (sel.value) body.sellerName = sel.options[sel.selectedIndex].text;
    try {
      await api('/api/transactions', { method: 'POST', body: JSON.stringify(body) });
      e.target.reset();
      await loadAll();
      showToast('تراکنش با موفقیت ثبت شد.');
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('account-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      await api('/api/accounts', { method: 'POST', body: JSON.stringify(Object.fromEntries(new FormData(e.target).entries())) });
      e.target.reset();
      await loadAll();
      showToast('حساب / کارت با محاسبه معادل گرمی طلا ثبت شد.');
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('expense-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      await api('/api/expenses', { method: 'POST', body: JSON.stringify(Object.fromEntries(new FormData(e.target).entries())) });
      e.target.reset();
      await loadAll();
      showToast('هزینه مغازه ثبت شد.');
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('inventory-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      await api('/api/inventory', { method: 'POST', body: JSON.stringify(Object.fromEntries(new FormData(e.target).entries())) });
      e.target.reset();
      await loadAll();
      showToast('کالا به انبار اضافه شد.');
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('seller-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      await api('/api/sellers', { method: 'POST', body: JSON.stringify(Object.fromEntries(new FormData(e.target).entries())) });
      e.target.reset();
      await loadAll();
      showToast('فروشنده جدید تعریف شد.');
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('seller-movement-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.target).entries());
    if (!data.sellerId) return showToast('ابتدا یک فروشنده تعریف کنید.');
    try {
      await api(`/api/sellers/${data.sellerId}/movements`, { method: 'POST', body: JSON.stringify(data) });
      e.target.reset();
      await loadAll();
      showToast('ورود / خروج فروشنده ثبت شد.');
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('customer-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      await api('/api/customers', { method: 'POST', body: JSON.stringify(Object.fromEntries(new FormData(e.target).entries())) });
      e.target.reset();
      await loadAll();
      showToast('پرونده مشتری ایجاد شد.');
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('investor-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      await api('/api/investors', { method: 'POST', body: JSON.stringify(Object.fromEntries(new FormData(e.target).entries())) });
      e.target.reset();
      await loadAll();
      showToast('سرمایه‌گذار ثبت شد.');
    } catch (err) { showToast(err.message); }
  });

  // Voice Input & Save
  document.getElementById('voice-transcript').addEventListener('input', previewVoiceParse);
  document.querySelectorAll('[data-sample]').forEach((chip) => {
    chip.addEventListener('click', () => {
      document.getElementById('voice-transcript').value = chip.dataset.sample;
      previewVoiceParse();
    });
  });

  document.getElementById('save-voice-btn').addEventListener('click', async () => {
    const rawTranscript = document.getElementById('voice-transcript').value.trim();
    if (!rawTranscript) return showToast('ابتدا پیام صوتی را ضبط یا تایپ کنید.');
    const payload = {
      rawTranscript,
      sellerName: document.getElementById('voice-seller-select').value || null,
      alsoRecordAsAccountingEntry: document.getElementById('voice-post-accounting').checked,
      recordedAtMillis: Date.now(),
      createdOffline: !navigator.onLine
    };
    if (!navigator.onLine) {
      const q = loadOfflineQueue();
      q.push(payload);
      saveOfflineQueue(q);
      document.getElementById('voice-transcript').value = '';
      renderAll();
      return showToast('آفلاین ذخیره شد؛ به محض اتصال نت خودکار ارسال می‌شود.');
    }
    try {
      const res = await api('/api/voice', { method: 'POST', body: JSON.stringify(payload) });
      document.getElementById('voice-transcript').value = '';
      previewVoiceParse();
      await loadAll();
      showToast(res.syncedNow ? 'پیام صوتی تحلیل و در گوگل شیت ثبت شد.' : 'پیام ذخیره شد و در صف ارسال به گوگل شیت قرار گرفت.');
    } catch {
      const q = loadOfflineQueue();
      q.push({ ...payload, createdOffline: true });
      saveOfflineQueue(q);
      showToast('در صف آفلاین ذخیره شد؛ به محض اتصال مجدد ارسال می‌شود.');
    }
  });

  document.getElementById('flush-voice-btn').addEventListener('click', async () => {
    await flushBrowserOfflineVoiceQueue();
    try {
      const res = await api('/api/voice/flush-sheets', { method: 'POST' });
      await loadAll();
      showToast(res.message);
    } catch (err) { showToast(err.message); }
  });

  // Reports & Sheets
  document.querySelectorAll('#report-period-chips .chip').forEach((chip) => {
    chip.addEventListener('click', () => {
      document.querySelectorAll('#report-period-chips .chip').forEach((c) => c.classList.remove('active'));
      chip.classList.add('active');
      loadReport(chip.dataset.period);
    });
  });
  document.getElementById('print-report-btn').addEventListener('click', () => window.print());

  document.getElementById('copy-script-btn').addEventListener('click', async () => {
    const script = state.bootstrap?.appsScriptTemplate || '';
    try {
      await navigator.clipboard.writeText(script);
      showToast('کد آماده Google Apps Script در کلیپ‌بورد کپی شد.');
    } catch {
      showToast('امکان کپی خودکار نبود.');
    }
  });

  document.getElementById('test-webhook-btn').addEventListener('click', async () => {
    const webhookUrl = document.getElementById('sheets-webhook-input').value.trim();
    try {
      const res = await api('/api/sheets/test', { method: 'POST', body: JSON.stringify({ webhookUrl }) });
      showToast(`اتصال مستقیم به شیت «${res.sheetName}» با موفقیت تایید شد!`);
      await loadAll();
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('sync-sheets-btn').addEventListener('click', async () => {
    const webhookUrl = document.getElementById('sheets-webhook-input').value.trim();
    try {
      const res = await api('/api/sheets/sync', { method: 'POST', body: JSON.stringify({ webhookUrl }) });
      showToast(`همگام‌سازی کامل (${toFa(res.rowCount)} ردیف) بدون خطا انجام شد.`);
      await loadAll();
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('settings-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      await api('/api/settings', { method: 'PUT', body: JSON.stringify(Object.fromEntries(new FormData(e.target).entries())) });
      await loadAll();
      showToast('تنظیمات مغازه ذخیره شد.');
    } catch (err) { showToast(err.message); }
  });

  // Delegated Delete Actions
  document.body.addEventListener('click', async (e) => {
    const map = [
      ['delAccount', '/api/accounts/'],
      ['delExpense', '/api/expenses/'],
      ['delInv', '/api/inventory/'],
      ['delSeller', '/api/sellers/'],
      ['delCustomer', '/api/customers/'],
      ['delInvestor', '/api/investors/'],
      ['delVoice', '/api/voice/']
    ];
    for (const [key, base] of map) {
      if (e.target.dataset && e.target.dataset[key]) {
        try {
          await api(base + e.target.dataset[key], { method: 'DELETE' });
          await loadAll();
          showToast('رکورد حذف شد.');
        } catch (err) { showToast(err.message); }
        return;
      }
    }
  });

  window.addEventListener('online', updateNetworkBadge);
  window.addEventListener('offline', updateNetworkBadge);
}

// Init
applyTheme(localStorage.getItem('zarnegar_dark') === '1');
wireEvents();
setupSpeechRecognition();
updateNetworkBadge();
loadAll();
