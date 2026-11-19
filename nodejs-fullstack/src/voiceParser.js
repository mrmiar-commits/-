'use strict';

const WORD_NUMBERS = {
  'نیم': 0.5,
  'نصف': 0.5,
  'ربع': 0.25,
  'یک': 1,
  'یه': 1,
  'دو': 2,
  'سه': 3,
  'چهار': 4,
  'پنج': 5,
  'شش': 6,
  'شیش': 6,
  'هفت': 7,
  'هشت': 8,
  'نه': 9,
  'ده': 10,
  'یازده': 11,
  'دوازده': 12,
  'پانزده': 15,
  'بیست': 20,
  'سی': 30,
  'چهل': 40,
  'پنجاه': 50,
  'صد': 100
};

const STOP_WORDS = new Set([
  'مغازه', 'حساب', 'صندوق', 'کارت', 'بانک', 'تومان', 'ریال', 'گرم', 'گرمی',
  'دلار', 'آبشده', 'ابشده', 'شمش', 'طلا', 'طلای', 'نو', 'کارکرده', 'سکه',
  'امروز', 'دیروز', 'ساعت', 'عیار', 'نقد', 'نقدی', 'کارتخوان', 'فروش', 'خرید'
]);

const CATEGORY_LABELS = {
  NEW_GOLD: 'طلای نو ویترینی',
  USED_NO_WAGE: 'طلای کارکرده بدون اجرت',
  MELTED_GOLD: 'طلای آبشده',
  BULLION: 'شمش طلا (۲۴ عیار)',
  USD_CURRENCY: 'دلار و ارز',
  OTHER_GOLD: 'سایر انواع طلا',
  NONE: 'عملیات حساب / نقدی'
};

const PIECE_LABELS = {
  RING: 'انگشتر',
  NECKLACE: 'گردنبند',
  BANGLE: 'النگو',
  BRACELET: 'دستبند',
  EARRING: 'گوشواره',
  SET: 'سرویس و نیم‌ست',
  PENDANT: 'پلاک و آویز',
  COIN: 'سکه',
  BULLION_BAR: 'شمش',
  MELTED_BLOCK: 'قطعه آبشده',
  CURRENCY_UNIT: 'واحد ارز',
  OTHER: 'سایر قطعات'
};

const ACTION_LABELS = {
  SALE_OUT: 'تحویل / فروش به',
  PURCHASE_IN: 'دریافت / خرید از',
  EXPENSE: 'ثبت هزینه مغازه',
  NOTE: 'یادداشت صوتی'
};

function toPersianDigits(input) {
  const digits = ['۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'];
  return String(input).replace(/[0-9]/g, (d) => digits[Number(d)]);
}

function normalizeAsciiDigits(input) {
  return String(input || '')
    .replace(/[۰-۹]/g, (ch) => String(ch.charCodeAt(0) - 0x06f0))
    .replace(/[٠-٩]/g, (ch) => String(ch.charCodeAt(0) - 0x0660))
    .replace(/[٫،]/g, '.');
}

function formatDateTimeFa(millis) {
  const d = new Date(millis || Date.now());
  const pad = (n) => String(n).padStart(2, '0');
  const raw = `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  return toPersianDigits(raw);
}

function normalizeText(input) {
  let text = normalizeAsciiDigits(input)
    .replace(/ي/g, 'ی')
    .replace(/ك/g, 'ک')
    .replace(/\u200c/g, ' ')
    .replace(/\//g, '.')
    .replace(/\s+ممیز\s+/g, '.')
    .replace(/\s+نقطه\s+/g, '.')
    .replace(/\s+/g, ' ')
    .trim();

  text = text.replace(/(یک|یه|دو|سه|چهار|پنج|شش|شیش|هفت|هشت|نه|ده|بیست)\s+و\s+نیم/g, (_, w) => {
    return String((WORD_NUMBERS[w] || 0) + 0.5);
  });
  text = text.replace(/(یک|یه|دو|سه|چهار|پنج|شش|شیش|هفت|هشت|نه|ده)\s+و\s+ربع/g, (_, w) => {
    return String((WORD_NUMBERS[w] || 0) + 0.25);
  });
  text = text.replace(/(\d+)\s+و\s+نیم/g, (_, n) => String(Number(n) + 0.5));

  for (const [word, num] of Object.entries(WORD_NUMBERS)) {
    const re = new RegExp(`(^|\\s)${word}(?=\\s+(گرم|گرمی|مثقال|سوت|دلار|تومان|میلیون))`, 'g');
    text = text.replace(re, `$1${num}`);
  }
  return text;
}

function detectAction(text) {
  if (['خرج', 'هزینه', 'اجاره', 'قبض', 'حقوق', 'پیک', 'پذیرایی', 'مخارج'].some((k) => text.includes(k))) {
    return 'EXPENSE';
  }
  if (['خرید', 'خریدم', 'گرفتم', 'دریافت', 'ورود', 'آورد', 'از '].some((k) => text.includes(k))) {
    return 'PURCHASE_IN';
  }
  if (['فروش', 'فروختم', 'دادم', 'تحویل', 'ارسال', 'خروج', 'به '].some((k) => text.includes(k))) {
    return 'SALE_OUT';
  }
  return 'NOTE';
}

function detectPieceOnly(text, fallback = null) {
  if (text.includes('انگشتر') || text.includes('حلقه')) return 'RING';
  if (text.includes('گردنبند') || text.includes('زنجیر')) return 'NECKLACE';
  if (text.includes('النگو')) return 'BANGLE';
  if (text.includes('دستبند')) return 'BRACELET';
  if (text.includes('گوشواره')) return 'EARRING';
  if (text.includes('سرویس') || text.includes('نیم ست')) return 'SET';
  if (text.includes('پلاک') || text.includes('آویز') || text.includes('اویز')) return 'PENDANT';
  if (text.includes('سکه')) return 'COIN';
  if (text.includes('شمش')) return 'BULLION_BAR';
  return fallback;
}

function detectCategoryAndPiece(text) {
  if (text.includes('دلار') || text.includes('ارز') || text.includes('$')) {
    return { goldCategory: 'USD_CURRENCY', pieceType: 'CURRENCY_UNIT', karat: 0 };
  }
  if (text.includes('آبشده') || text.includes('ابشده') || text.includes('آب شده') || text.includes('اب شده')) {
    return { goldCategory: 'MELTED_GOLD', pieceType: 'MELTED_BLOCK', karat: 750 };
  }
  if (text.includes('شمش') || text.includes('24 عیار') || text.includes('بیست و چهار')) {
    return { goldCategory: 'BULLION', pieceType: 'BULLION_BAR', karat: 999 };
  }
  if (text.includes('کارکرده') || text.includes('بدون اجرت') || text.includes('دست دوم') || text.includes('شکسته')) {
    return { goldCategory: 'USED_NO_WAGE', pieceType: detectPieceOnly(text, 'OTHER') || 'OTHER', karat: 750 };
  }
  if (text.includes('سکه') || text.includes('امامی') || text.includes('بهار آزادی')) {
    return { goldCategory: 'OTHER_GOLD', pieceType: 'COIN', karat: 900 };
  }
  const detectedPiece = detectPieceOnly(text, null);
  if (text.includes('ویترینی') || text.includes('نو') || detectedPiece) {
    return { goldCategory: 'NEW_GOLD', pieceType: detectedPiece || 'RING', karat: 750 };
  }
  return { goldCategory: 'OTHER_GOLD', pieceType: 'OTHER', karat: 750 };
}

function extractAmounts(text, goldCategory, actionType) {
  let weightGrams = 0;
  let usdAmount = 0;
  let cashToman = 0;

  const gramMatch = text.match(/(\d+(?:\.\d+)?)\s*(?:گرم|گرمی|g\b)/i);
  if (gramMatch) weightGrams += Number(gramMatch[1]) || 0;

  const sootMatch = text.match(/(\d+(?:\.\d+)?)\s*سوت/);
  if (sootMatch) weightGrams += (Number(sootMatch[1]) || 0) / 1000;

  const mesghalMatch = text.match(/(\d+(?:\.\d+)?)\s*مثقال/);
  if (mesghalMatch) weightGrams += (Number(mesghalMatch[1]) || 0) * 4.608;

  const usdMatch = text.match(/(\d+(?:\.\d+)?)\s*(?:دلار|\$)/);
  if (usdMatch) usdAmount = Number(usdMatch[1]) || 0;

  const milMatch = text.match(/(\d+(?:\.\d+)?)\s*(?:میلیون|ملیون)/);
  if (milMatch) cashToman = (Number(milMatch[1]) || 0) * 1_000_000;

  if (cashToman === 0) {
    const thouMatch = text.match(/(\d+(?:\.\d+)?)\s*هزار/);
    if (thouMatch) cashToman = (Number(thouMatch[1]) || 0) * 1_000;
  }
  if (cashToman === 0) {
    const tomanMatch = text.match(/(\d+(?:\.\d+)?)\s*تومان/);
    if (tomanMatch) cashToman = Number(tomanMatch[1]) || 0;
  }

  if (weightGrams === 0 && usdAmount === 0 && cashToman === 0) {
    const first = text.match(/(\d+(?:\.\d+)?)/);
    const val = first ? Number(first[1]) || 0 : 0;
    if (val > 0) {
      if (actionType === 'EXPENSE') cashToman = val;
      else if (goldCategory === 'USD_CURRENCY') usdAmount = val;
      else weightGrams = val;
    }
  }

  return { weightGrams, usdAmount, cashToman };
}

function extractKarat(text) {
  const m1 = text.match(/عیار\s*(\d{2,4})/);
  const m2 = text.match(/(\d{2,4})\s*عیار/);
  const raw = m1 ? Number(m1[1]) : m2 ? Number(m2[1]) : null;
  if (!raw) return null;
  const map = { 18: 750, 24: 999, 17: 705, 21: 875, 22: 916 };
  return map[raw] || Math.min(1000, Math.max(0, raw));
}

function extractCounterparty(text) {
  const re = /(?:به|از|برای|تحویل|توسط)\s+(?:آقای\s+|اقای\s+|خانم\s+|حاج\s+|استاد\s+)?([\u0600-\u06FFa-zA-Z]+(?:\s+[\u0600-\u06FFa-zA-Z]+)?)/g;
  const matches = [...text.matchAll(re)];
  for (let i = matches.length - 1; i >= 0; i--) {
    const words = matches[i][1].trim().split(/\s+/).filter((w) => w && !STOP_WORDS.has(w));
    if (words.length > 0) return words.slice(0, 2).join(' ');
  }
  return 'ثبت نشده';
}

function parseVoiceMessage(rawText, options = {}) {
  const timestampMillis = options.timestampMillis || Date.now();
  const cleaned = normalizeText(rawText);
  const actionType = detectAction(cleaned);
  const { goldCategory, pieceType, karat: defaultKarat } = detectCategoryAndPiece(cleaned);
  const { weightGrams, usdAmount, cashToman } = extractAmounts(cleaned, goldCategory, actionType);
  const karat = extractKarat(cleaned) ?? defaultKarat;
  const counterpartyName = extractCounterparty(cleaned);

  return {
    rawTranscript: String(rawText || '').trim(),
    actionType,
    actionLabelFa: ACTION_LABELS[actionType] || actionType,
    goldCategory,
    categoryLabelFa: CATEGORY_LABELS[goldCategory] || goldCategory,
    pieceType,
    pieceLabelFa: PIECE_LABELS[pieceType] || pieceType,
    weightGrams: Number(weightGrams.toFixed(4)),
    karat,
    usdAmount: Number(usdAmount.toFixed(2)),
    cashToman: Math.round(cashToman),
    counterpartyName,
    sellerName: options.sellerName || null,
    recordedAtMillis: timestampMillis,
    recordedDateTimeFa: formatDateTimeFa(timestampMillis),
    createdOffline: Boolean(options.createdOffline),
    sheetsSynced: false
  };
}

module.exports = {
  parseVoiceMessage,
  toPersianDigits,
  normalizeAsciiDigits,
  formatDateTimeFa,
  CATEGORY_LABELS,
  PIECE_LABELS,
  ACTION_LABELS
};
