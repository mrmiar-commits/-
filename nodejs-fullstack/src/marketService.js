'use strict';

const { formatDateTimeFa } = require('./voiceParser');

let cachedOnlineSnapshot = null;
let lastFetchAt = 0;
const CACHE_TTL_MS = 45_000;

async function fetchJsonWithTimeout(url, timeoutMs = 6500) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, { signal: controller.signal });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } finally {
    clearTimeout(timer);
  }
}

async function getMarketSnapshot(pricingConfig = {}, forceRefresh = false) {
  const now = Date.now();
  const calibratedUsd = Number(pricingConfig.manualUsdToman) > 1000
    ? Number(pricingConfig.manualUsdToman)
    : 85_000;

  // Manual override mode
  if (pricingConfig.useManualPrice) {
    const gold18k = Number(pricingConfig.manualGold18kToman) > 0 ? Number(pricingConfig.manualGold18kToman) : 6_850_000;
    const mesghal = Number(pricingConfig.manualMeltedMesghalToman) > 0
      ? Number(pricingConfig.manualMeltedMesghalToman)
      : Math.round(gold18k * 4.3318);
    const gold24k = Number(pricingConfig.manualBullion24kToman) > 0
      ? Number(pricingConfig.manualBullion24kToman)
      : Math.round(gold18k * (999 / 750));

    return {
      xauOunceUsd: Number(((gold24k * 31.1035) / calibratedUsd).toFixed(2)),
      usdToToman: calibratedUsd,
      gold24kGramToman: Math.round(gold24k),
      gold18kGramToman: Math.round(gold18k),
      meltedMesghalToman: Math.round(mesghal),
      lastUpdatedText: formatDateTimeFa(now),
      isLiveNetwork: false,
      isManualOverride: true,
      sourceNote: 'نرخ دستی ثبت‌شده توسط مدیر مغازه'
    };
  }

  // Online API mode with 45s cache
  if (!forceRefresh && cachedOnlineSnapshot && (now - lastFetchAt) < CACHE_TTL_MS) {
    return { ...cachedOnlineSnapshot, usdToToman: calibratedUsd };
  }

  let xauOunceUsd = 3020;
  let isLiveNetwork = false;

  try {
    const goldData = await fetchJsonWithTimeout('https://api.gold-api.com/price/XAU');
    if (goldData && Number(goldData.price) > 1000) {
      xauOunceUsd = Number(goldData.price);
      isLiveNetwork = true;
    }
  } catch {
    // Graceful fallback when offline or blocked
  }

  const gold24kGramToman = Math.round((xauOunceUsd / 31.1034768) * calibratedUsd);
  const gold18kGramToman = Math.round(gold24kGramToman * 0.75);
  const meltedMesghalToman = Math.round(gold18kGramToman * 4.3318);

  const snap = {
    xauOunceUsd: Number(xauOunceUsd.toFixed(2)),
    usdToToman: calibratedUsd,
    gold24kGramToman,
    gold18kGramToman,
    meltedMesghalToman,
    lastUpdatedText: formatDateTimeFa(now),
    isLiveNetwork,
    isManualOverride: false,
    sourceNote: isLiveNetwork ? 'استعلام زنده از API انس جهانی طلا' : 'محاسبه آفلاین بر اساس نرخ کالیبره دلار'
  };

  cachedOnlineSnapshot = snap;
  lastFetchAt = now;
  return snap;
}

module.exports = { getMarketSnapshot };
