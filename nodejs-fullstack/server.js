'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');
const { ZarnegarStore } = require('./src/store');
const { getMarketSnapshot } = require('./src/marketService');
const { parseVoiceMessage } = require('./src/voiceParser');
const sheetsService = require('./src/sheetsService');

const PUBLIC_DIR = path.join(__dirname, 'public');

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon'
};

function sendJson(res, statusCode, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store'
  });
  res.end(body);
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.on('data', (chunk) => {
      raw += chunk;
      if (raw.length > 2 * 1024 * 1024) {
        reject(new Error('حجم درخواست بیش از حد مجاز است.'));
        req.destroy();
      }
    });
    req.on('end', () => {
      if (!raw.trim()) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch {
        reject(new Error('ساختار JSON نامعتبر است.'));
      }
    });
    req.on('error', reject);
  });
}

function serveStatic(req, res, pathname) {
  const safePath = pathname === '/' ? '/index.html' : pathname;
  const resolved = path.normalize(path.join(PUBLIC_DIR, safePath));
  if (!resolved.startsWith(PUBLIC_DIR) || !fs.existsSync(resolved) || fs.statSync(resolved).isDirectory()) {
    const fallback = path.join(PUBLIC_DIR, 'index.html');
    if (fs.existsSync(fallback)) {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      fs.createReadStream(fallback).pipe(res);
      return;
    }
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('Not Found');
    return;
  }
  const ext = path.extname(resolved).toLowerCase();
  res.writeHead(200, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' });
  fs.createReadStream(resolved).pipe(res);
}

function createAppServer(dbFilePath = path.join(__dirname, 'data', 'zarnegar-db.json')) {
  const store = new ZarnegarStore(dbFilePath);

  return http.createServer(async (req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, PATCH, DELETE, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    if (req.method === 'OPTIONS') {
      res.writeHead(204);
      res.end();
      return;
    }

    const parsedUrl = new URL(req.url, 'http://localhost');
    const pathname = parsedUrl.pathname;

    if (!pathname.startsWith('/api/')) {
      serveStatic(req, res, pathname);
      return;
    }

    try {
      const method = req.method;

      // Bootstrap entire state in one fast call
      if (method === 'GET' && pathname === '/api/bootstrap') {
        const force = parsedUrl.searchParams.get('refresh') === '1';
        const market = await getMarketSnapshot(store.data.pricing, force);
        return sendJson(res, 200, {
          settings: store.data.settings,
          pricing: store.data.pricing,
          market,
          summary: store.getDashboardSummary(market),
          accounts: store.listAccountsWithGoldEquiv(market),
          inventory: store.data.inventory,
          sellers: store.data.sellers,
          sellerMovements: store.data.sellerMovements,
          transactions: store.data.transactions,
          expenses: store.data.expenses,
          customers: store.data.customers,
          investors: store.data.investors,
          voiceNotes: store.data.voiceNotes,
          priceAlerts: store.data.priceAlerts,
          appsScriptTemplate: sheetsService.APPS_SCRIPT_TEMPLATE
        });
      }

      // Market & Pricing
      if (method === 'GET' && pathname === '/api/market') {
        const force = parsedUrl.searchParams.get('refresh') === '1';
        const market = await getMarketSnapshot(store.data.pricing, force);
        return sendJson(res, 200, { market, pricing: store.data.pricing });
      }
      if (method === 'PUT' && pathname === '/api/pricing') {
        const body = await readJsonBody(req);
        const pricing = store.updatePricing(body);
        const market = await getMarketSnapshot(pricing, true);
        return sendJson(res, 200, { pricing, market });
      }

      // Settings
      if (method === 'GET' && pathname === '/api/settings') {
        return sendJson(res, 200, store.data.settings);
      }
      if (method === 'PUT' && pathname === '/api/settings') {
        const body = await readJsonBody(req);
        return sendJson(res, 200, store.updateSettings(body));
      }

      // Accounts (Bank, Personal Card, Petty Cash + Gold Gram Equivalent)
      if (method === 'GET' && pathname === '/api/accounts') {
        const market = await getMarketSnapshot(store.data.pricing);
        return sendJson(res, 200, store.listAccountsWithGoldEquiv(market));
      }
      if (method === 'POST' && pathname === '/api/accounts') {
        const body = await readJsonBody(req);
        const item = store.addAccount(body);
        const market = await getMarketSnapshot(store.data.pricing);
        return sendJson(res, 201, {
          ...item,
          goldEquivalent18kGrams: store.goldEquivalent18k(item, market.gold18kGramToman, market.usdToToman)
        });
      }
      if (method === 'DELETE' && pathname.startsWith('/api/accounts/')) {
        store.deleteAccount(pathname.split('/').pop());
        return sendJson(res, 200, { ok: true });
      }

      // Transactions
      if (method === 'GET' && pathname === '/api/transactions') {
        return sendJson(res, 200, store.data.transactions);
      }
      if (method === 'POST' && pathname === '/api/transactions') {
        const body = await readJsonBody(req);
        return sendJson(res, 201, store.recordTransaction(body));
      }

      // Shop Expenses
      if (method === 'GET' && pathname === '/api/expenses') {
        return sendJson(res, 200, store.data.expenses);
      }
      if (method === 'POST' && pathname === '/api/expenses') {
        const body = await readJsonBody(req);
        const market = await getMarketSnapshot(store.data.pricing);
        return sendJson(res, 201, store.addExpense(body, market));
      }
      if (method === 'DELETE' && pathname.startsWith('/api/expenses/')) {
        store.deleteExpense(pathname.split('/').pop());
        return sendJson(res, 200, { ok: true });
      }

      // Inventory
      if (method === 'GET' && pathname === '/api/inventory') {
        return sendJson(res, 200, store.data.inventory);
      }
      if (method === 'POST' && pathname === '/api/inventory') {
        const body = await readJsonBody(req);
        return sendJson(res, 201, store.addInventory(body));
      }
      if (method === 'PUT' && pathname.startsWith('/api/inventory/')) {
        const body = await readJsonBody(req);
        return sendJson(res, 200, store.updateInventory(pathname.split('/').pop(), body));
      }
      if (method === 'DELETE' && pathname.startsWith('/api/inventory/')) {
        store.deleteInventory(pathname.split('/').pop());
        return sendJson(res, 200, { ok: true });
      }

      // Sellers (3-5 shop users + gold/USD movements)
      if (method === 'GET' && pathname === '/api/sellers') {
        return sendJson(res, 200, { sellers: store.data.sellers, movements: store.data.sellerMovements });
      }
      if (method === 'POST' && pathname === '/api/sellers') {
        const body = await readJsonBody(req);
        return sendJson(res, 201, store.addSeller(body));
      }
      if (method === 'POST' && /^\/api\/sellers\/[^/]+\/movements$/.test(pathname)) {
        const sellerId = pathname.split('/')[3];
        const body = await readJsonBody(req);
        return sendJson(res, 201, store.recordSellerMovement(sellerId, body));
      }
      if (method === 'DELETE' && pathname.startsWith('/api/sellers/')) {
        store.deleteSeller(pathname.split('/').pop());
        return sendJson(res, 200, { ok: true });
      }

      // Customers
      if (method === 'GET' && pathname === '/api/customers') {
        return sendJson(res, 200, store.data.customers);
      }
      if (method === 'POST' && pathname === '/api/customers') {
        const body = await readJsonBody(req);
        return sendJson(res, 201, store.addCustomer(body));
      }
      if (method === 'DELETE' && pathname.startsWith('/api/customers/')) {
        store.deleteCustomer(pathname.split('/').pop());
        return sendJson(res, 200, { ok: true });
      }

      // Investors
      if (method === 'GET' && pathname === '/api/investors') {
        return sendJson(res, 200, store.data.investors);
      }
      if (method === 'POST' && pathname === '/api/investors') {
        const body = await readJsonBody(req);
        return sendJson(res, 201, store.addInvestor(body));
      }
      if (method === 'POST' && /^\/api\/investors\/[^/]+\/settle$/.test(pathname)) {
        const invId = pathname.split('/')[3];
        const body = await readJsonBody(req);
        return sendJson(res, 200, store.settleInvestor(invId, body.payoutAmountToman));
      }
      if (method === 'DELETE' && pathname.startsWith('/api/investors/')) {
        store.deleteInvestor(pathname.split('/').pop());
        return sendJson(res, 200, { ok: true });
      }

      // Voice Message Recorder & Parser + Google Sheets Auto-Sync
      if (method === 'POST' && pathname === '/api/voice/parse') {
        const body = await readJsonBody(req);
        return sendJson(res, 200, parseVoiceMessage(body.rawTranscript || '', { sellerName: body.sellerName || null }));
      }
      if (method === 'GET' && pathname === '/api/voice') {
        return sendJson(res, 200, store.data.voiceNotes);
      }
      if (method === 'POST' && pathname === '/api/voice') {
        const body = await readJsonBody(req);
        const market = await getMarketSnapshot(store.data.pricing);
        const note = store.addVoiceNote(body, market);
        let syncedNow = false;
        const webhook = store.data.settings.sheetsWebhookUrl;
        if (webhook) {
          try {
            await sheetsService.appendVoiceNotes(webhook, [note]);
            store.markVoiceNotesSynced([note.id]);
            note.sheetsSynced = true;
            syncedNow = true;
          } catch {
            // Keep queued in DB for auto-retry when online
          }
        }
        return sendJson(res, 201, { note, syncedNow });
      }
      if (method === 'POST' && pathname === '/api/voice/flush-sheets') {
        const webhook = store.data.settings.sheetsWebhookUrl;
        if (!webhook) throw new Error('ابتدا لینک وب‌اپ Google Apps Script را در بخش تنظیمات گوگل شیت ذخیره کنید.');
        const pending = store.data.voiceNotes.filter((v) => !v.sheetsSynced);
        if (!pending.length) return sendJson(res, 200, { synced: 0, message: 'همه پیام‌های صوتی در گوگل شیت ثبت شده‌اند.' });
        const count = await sheetsService.appendVoiceNotes(webhook, pending);
        store.markVoiceNotesSynced(pending.map((v) => v.id));
        return sendJson(res, 200, { synced: count, message: `${count} پیام صوتی با موفقیت به گوگل شیت ارسال شد.` });
      }
      if (method === 'DELETE' && pathname.startsWith('/api/voice/')) {
        store.deleteVoiceNote(pathname.split('/').pop());
        return sendJson(res, 200, { ok: true });
      }

      // Price Alerts
      if (method === 'POST' && pathname === '/api/alerts') {
        const body = await readJsonBody(req);
        return sendJson(res, 201, store.addPriceAlert(body));
      }
      if (method === 'PATCH' && pathname.startsWith('/api/alerts/')) {
        return sendJson(res, 200, store.togglePriceAlert(pathname.split('/').pop()));
      }
      if (method === 'DELETE' && pathname.startsWith('/api/alerts/')) {
        store.deletePriceAlert(pathname.split('/').pop());
        return sendJson(res, 200, { ok: true });
      }

      // Reports
      if (method === 'GET' && pathname === '/api/reports') {
        const period = parsedUrl.searchParams.get('period') || 'MONTH';
        const market = await getMarketSnapshot(store.data.pricing);
        return sendJson(res, 200, store.buildReport(period, market));
      }

      // Google Sheets Direct Bridge & CSV Export
      if (method === 'GET' && pathname === '/api/sheets/script') {
        return sendJson(res, 200, { script: sheetsService.APPS_SCRIPT_TEMPLATE });
      }
      if (method === 'POST' && pathname === '/api/sheets/test') {
        const body = await readJsonBody(req);
        const url = body.webhookUrl || store.data.settings.sheetsWebhookUrl;
        const sheetName = await sheetsService.testWebhookConnection(url);
        store.updateSettings({ sheetsWebhookUrl: url });
        return sendJson(res, 200, { ok: true, sheetName });
      }
      if (method === 'POST' && pathname === '/api/sheets/sync') {
        const body = await readJsonBody(req);
        const url = body.webhookUrl || store.data.settings.sheetsWebhookUrl;
        if (body.webhookUrl) store.updateSettings({ sheetsWebhookUrl: body.webhookUrl });
        if (!url) throw new Error('لطفاً ابتدا لینک وب‌اپ Google Apps Script را وارد کنید.');
        const market = await getMarketSnapshot(store.data.pricing);
        const summary = store.getDashboardSummary(market);
        const rowCount = await sheetsService.syncAllTabs(url, store.data, summary, market);
        store.markAllSynced();
        return sendJson(res, 200, { ok: true, rowCount });
      }
      if (method === 'GET' && pathname === '/api/export/csv') {
        const csv = sheetsService.buildCsvExport(store.data);
        res.writeHead(200, {
          'Content-Type': 'text/csv; charset=utf-8',
          'Content-Disposition': 'attachment; filename="zarnegar-export.csv"'
        });
        res.end(csv);
        return;
      }

      sendJson(res, 404, { error: 'مسیر API یافت نشد.' });
    } catch (err) {
      sendJson(res, 400, { error: err.message || 'خطای غیرمنتظره در سرور' });
    }
  });
}

if (require.main === module) {
  const PORT = Number(process.env.PORT) || 3000;
  const server = createAppServer();
  server.listen(PORT, () => {
    console.log(`Zarnegar Node.js Backend & Frontpage running at http://localhost:${PORT}`);
  });
}

module.exports = { createAppServer };
