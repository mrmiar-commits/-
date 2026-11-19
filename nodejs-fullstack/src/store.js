'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { parseVoiceMessage, formatDateTimeFa } = require('./voiceParser');

function newId(prefix) {
  return `${prefix}_${crypto.randomBytes(6).toString('hex')}`;
}

function createInitialData() {
  const now = Date.now();
  return {
    settings: {
      shopName: 'طلافروشی زرنگار',
      ownerName: 'مدیر مغازه',
      sheetsWebhookUrl: '',
      paymentProvider: 'ZARINPAL',
      merchantId: '',
      darkTheme: false
    },
    pricing: {
      useManualPrice: false,
      manualGold18kToman: 0,
      manualMeltedMesghalToman: 0,
      manualBullion24kToman: 0,
      manualUsdToman: 85_000
    },
    accounts: [
      { id: 'acc_cash_main', name: 'صندوق نقدی مغازه', accountType: 'CASH_REGISTER', balanceToman: 0, balanceUsd: 0, goldGrams18k: 0, accountNumber: null, createdAtMillis: now },
      { id: 'acc_bank_pos', name: 'حساب بانکی و کارتخوان', accountType: 'BANK_POS', balanceToman: 0, balanceUsd: 0, goldGrams18k: 0, accountNumber: null, createdAtMillis: now + 1 },
      { id: 'acc_petty_cash', name: 'تنخواه روزانه مغازه', accountType: 'PETTY_CASH', balanceToman: 0, balanceUsd: 0, goldGrams18k: 0, accountNumber: null, createdAtMillis: now + 2 },
      { id: 'acc_gold_safe', name: 'گاوصندوق طلا', accountType: 'GOLD_SAFE', balanceToman: 0, balanceUsd: 0, goldGrams18k: 0, accountNumber: null, createdAtMillis: now + 3 }
    ],
    inventory: [],
    customers: [],
    investors: [],
    sellers: [],
    sellerMovements: [],
    transactions: [],
    expenses: [],
    voiceNotes: [],
    priceAlerts: []
  };
}

class ZarnegarStore {
  constructor(filePath) {
    this.filePath = filePath;
    fs.mkdirSync(path.dirname(filePath), { recursive: true });
    if (fs.existsSync(filePath)) {
      try {
        const parsed = JSON.parse(fs.readFileSync(filePath, 'utf8'));
        this.data = { ...createInitialData(), ...parsed };
      } catch {
        this.data = createInitialData();
        this.save();
      }
    } else {
      this.data = createInitialData();
      this.save();
    }
  }

  save() {
    const tmp = `${this.filePath}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(this.data, null, 2), 'utf8');
    fs.renameSync(tmp, this.filePath);
  }

  goldEquivalent18k(account, gold18kRate, usdRate) {
    const rate = Math.max(1, Number(gold18kRate) || 1);
    const cashEq = (Number(account.balanceToman) || 0) / rate;
    const usdEq = Number(usdRate) > 0 ? ((Number(account.balanceUsd) || 0) * Number(usdRate)) / rate : 0;
    return Number(((Number(account.goldGrams18k) || 0) + cashEq + usdEq).toFixed(4));
  }

  calculateSaleBreakdown(input) {
    const category = input.goldCategory || 'NEW_GOLD';
    const qty = Math.max(0, Number(input.weightGrams) || 0);
    const karat = Number(input.karat) || 750;
    const unitPrice = Math.max(0, Number(input.unitPriceToman) || 0);
    const wagePct = Math.max(0, Number(input.wagePercent) || 0);
    const profitPct = Math.max(0, Number(input.profitMarginPercent) || 0);
    const taxPct = Math.max(0, Number(input.taxPercent) || 0);
    const costPerUnit = Math.max(0, Number(input.costBasisPerUnitToman) || 0);

    if (category === 'USD_CURRENCY') {
      const total = qty * unitPrice;
      const cost = qty * (costPerUnit > 0 ? costPerUnit : unitPrice * 0.985);
      return {
        goldBaseValueToman: Math.round(total),
        wageAmountToman: 0,
        profitMarginAmountToman: Math.round(total - cost),
        taxAmountToman: 0,
        totalAmountToman: Math.round(total),
        costBasisToman: Math.round(cost),
        netProfitToman: Math.round(total - cost)
      };
    }

    const effectiveRate = karat > 0 && karat !== 750 ? unitPrice * (karat / 750) : unitPrice;
    const baseGold = qty * effectiveRate;
    const wage = baseGold * (wagePct / 100);
    const profit = (baseGold + wage) * (profitPct / 100);
    const tax = (wage + profit) * (taxPct / 100);
    const total = baseGold + wage + profit + tax;
    const costBasis = qty * (costPerUnit > 0 ? costPerUnit : effectiveRate);
    const netProfit = total - tax - costBasis;

    return {
      goldBaseValueToman: Math.round(baseGold),
      wageAmountToman: Math.round(wage),
      profitMarginAmountToman: Math.round(profit),
      taxAmountToman: Math.round(tax),
      totalAmountToman: Math.round(total),
      costBasisToman: Math.round(costBasis),
      netProfitToman: Math.round(netProfit)
    };
  }

  getDashboardSummary(market) {
    const goldRate = Math.max(1, Number(market.gold18kGramToman) || 1);
    const usdRate = Number(market.usdToToman) || 0;
    const startOfDay = new Date();
    startOfDay.setHours(0, 0, 0, 0);
    const dayStartMs = startOfDay.getTime();

    const sales = this.data.transactions.filter((t) => t.txType === 'SALE');
    const todaySales = sales.filter((t) => (t.timestampMillis || 0) >= dayStartMs);

    const stock = this.data.inventory;
    const meltedGrams = stock.filter((i) => i.category === 'MELTED_GOLD').reduce((s, i) => s + (i.weightGrams || 0), 0);
    const bullionGrams = stock.filter((i) => i.category === 'BULLION').reduce((s, i) => s + (i.weightGrams || 0), 0);
    const newDisplayGrams = stock.filter((i) => i.category === 'NEW_GOLD').reduce((s, i) => s + (i.weightGrams || 0), 0);
    const usedNoWageGrams = stock.filter((i) => i.category === 'USED_NO_WAGE').reduce((s, i) => s + (i.weightGrams || 0), 0);
    const otherGoldGrams = stock.filter((i) => i.category === 'OTHER_GOLD').reduce((s, i) => s + (i.weightGrams || 0), 0);
    const stockUsd = stock.filter((i) => i.category === 'USD_CURRENCY').reduce((s, i) => s + (i.quantity || 0), 0);
    const accountUsd = this.data.accounts.reduce((s, a) => s + (a.balanceUsd || 0), 0);
    const totalGoldGrams = meltedGrams + bullionGrams + newDisplayGrams + usedNoWageGrams + otherGoldGrams;

    const totalSalesToman = sales.reduce((s, t) => s + (t.totalAmountToman || 0), 0);
    const totalProfitToman = sales.reduce((s, t) => s + (t.calculatedProfitToman || 0), 0);
    const totalExpensesToman = this.data.expenses.reduce((s, e) => s + (e.amountToman || 0), 0);
    const totalCashAccountsToman = this.data.accounts.reduce((s, a) => s + (a.balanceToman || 0), 0);
    const totalAccountsGoldEquivGrams = this.data.accounts.reduce((s, a) => s + this.goldEquivalent18k(a, goldRate, usdRate), 0);

    return {
      todaySalesToman: todaySales.reduce((s, t) => s + (t.totalAmountToman || 0), 0),
      todayProfitToman: todaySales.reduce((s, t) => s + (t.calculatedProfitToman || 0), 0),
      totalSalesToman,
      totalProfitToman,
      totalExpensesToman,
      netProfitAfterExpensesToman: totalProfitToman - totalExpensesToman,
      totalCashAccountsToman,
      totalAccountsGoldEquivGrams: Number(totalAccountsGoldEquivGrams.toFixed(3)),
      totalUsdBalance: Number((accountUsd + stockUsd).toFixed(2)),
      totalInventoryGrams: Number(totalGoldGrams.toFixed(3)),
      totalInvestorGrams: Number(this.data.investors.filter((i) => i.contractStatus === 'ACTIVE').reduce((s, i) => s + (i.investedGrams || 0), 0).toFixed(3)),
      pendingVoiceSyncCount: this.data.voiceNotes.filter((v) => !v.sheetsSynced).length,
      goldBreakdown: {
        meltedGrams: Number(meltedGrams.toFixed(3)),
        bullionGrams: Number(bullionGrams.toFixed(3)),
        newDisplayGrams: Number(newDisplayGrams.toFixed(3)),
        usedNoWageGrams: Number(usedNoWageGrams.toFixed(3)),
        otherGoldGrams: Number(otherGoldGrams.toFixed(3)),
        totalGoldGrams: Number(totalGoldGrams.toFixed(3)),
        totalUsdBalance: Number((accountUsd + stockUsd).toFixed(2))
      }
    };
  }

  // --- Accounts ---
  listAccountsWithGoldEquiv(market) {
    const goldRate = Math.max(1, Number(market.gold18kGramToman) || 1);
    const usdRate = Number(market.usdToToman) || 0;
    return this.data.accounts.map((a) => ({
      ...a,
      goldEquivalent18kGrams: this.goldEquivalent18k(a, goldRate, usdRate)
    }));
  }

  addAccount(payload) {
    const name = String(payload.name || '').trim();
    if (!name) throw new Error('نام حساب یا کارت الزامی است.');
    const allowed = ['CASH_REGISTER', 'BANK_POS', 'PERSONAL_CARD', 'PETTY_CASH', 'GOLD_SAFE', 'CUSTOM'];
    const accountType = allowed.includes(payload.accountType) ? payload.accountType : 'CUSTOM';
    const item = {
      id: newId('acc'),
      name,
      accountType,
      balanceToman: Number(payload.balanceToman) || 0,
      balanceUsd: Number(payload.balanceUsd) || 0,
      goldGrams18k: Number(payload.goldGrams18k) || 0,
      accountNumber: payload.accountNumber ? String(payload.accountNumber).trim() : null,
      createdAtMillis: Date.now()
    };
    this.data.accounts.unshift(item);
    this.save();
    return item;
  }

  deleteAccount(id) {
    this.data.accounts = this.data.accounts.filter((a) => a.id !== id);
    this.save();
  }

  // --- Inventory ---
  addInventory(payload) {
    const title = String(payload.title || '').trim();
    if (!title) throw new Error('عنوان کالا الزامی است.');
    const item = {
      id: newId('inv'),
      category: payload.category || 'NEW_GOLD',
      pieceType: payload.pieceType || 'RING',
      title,
      karat: Math.min(1000, Math.max(0, Number(payload.karat) || 750)),
      weightGrams: Math.max(0, Number(payload.weightGrams) || 0),
      quantity: Math.max(0, Math.round(Number(payload.quantity) || 1)),
      avgBuyPricePerUnit: Math.max(0, Number(payload.avgBuyPricePerUnit) || 0),
      defaultWagePercent: Math.min(100, Math.max(0, Number(payload.defaultWagePercent) || 0)),
      labCode: payload.labCode ? String(payload.labCode).trim() : null,
      createdAtMillis: Date.now()
    };
    this.data.inventory.unshift(item);
    this.save();
    return item;
  }

  updateInventory(id, payload) {
    const idx = this.data.inventory.findIndex((i) => i.id === id);
    if (idx === -1) throw new Error('کالا یافت نشد.');
    this.data.inventory[idx] = {
      ...this.data.inventory[idx],
      category: payload.category || this.data.inventory[idx].category,
      pieceType: payload.pieceType || this.data.inventory[idx].pieceType,
      title: String(payload.title || this.data.inventory[idx].title).trim(),
      karat: Math.min(1000, Math.max(0, Number(payload.karat ?? this.data.inventory[idx].karat))),
      weightGrams: Math.max(0, Number(payload.weightGrams ?? this.data.inventory[idx].weightGrams)),
      quantity: Math.max(0, Math.round(Number(payload.quantity ?? this.data.inventory[idx].quantity))),
      avgBuyPricePerUnit: Math.max(0, Number(payload.avgBuyPricePerUnit ?? this.data.inventory[idx].avgBuyPricePerUnit)),
      defaultWagePercent: Math.min(100, Math.max(0, Number(payload.defaultWagePercent ?? this.data.inventory[idx].defaultWagePercent))),
      labCode: payload.labCode !== undefined ? (payload.labCode ? String(payload.labCode).trim() : null) : this.data.inventory[idx].labCode
    };
    this.save();
    return this.data.inventory[idx];
  }

  deleteInventory(id) {
    this.data.inventory = this.data.inventory.filter((i) => i.id !== id);
    this.save();
  }

  // --- Sellers (3 to 5 Shop Users) ---
  addSeller(payload) {
    const name = String(payload.name || '').trim();
    if (!name) throw new Error('نام فروشنده الزامی است.');
    if (this.data.sellers.length >= 5) {
      throw new Error('حداکثر ۵ فروشنده برای مغازه قابل تعریف است.');
    }
    const seller = {
      id: newId('slr'),
      name,
      roleTitle: String(payload.roleTitle || 'فروشنده مغازه').trim(),
      phone: String(payload.phone || '-').trim(),
      goldInGrams: 0,
      goldOutGrams: 0,
      usdIn: 0,
      usdOut: 0,
      cashInToman: 0,
      cashOutToman: 0,
      notes: payload.notes ? String(payload.notes).trim() : null,
      createdAtMillis: Date.now()
    };
    this.data.sellers.push(seller);
    this.save();
    return seller;
  }

  deleteSeller(id) {
    this.data.sellers = this.data.sellers.filter((s) => s.id !== id);
    this.save();
  }

  recordSellerMovement(sellerId, payload) {
    const seller = this.data.sellers.find((s) => s.id === sellerId);
    if (!seller) throw new Error('فروشنده یافت نشد.');
    const direction = payload.direction === 'OUT' ? 'OUT' : 'IN';
    const weightGrams = Math.max(0, Number(payload.weightGrams) || 0);
    const usdAmount = Math.max(0, Number(payload.usdAmount) || 0);
    const cashToman = Math.max(0, Number(payload.cashToman) || 0);
    if (weightGrams === 0 && usdAmount === 0 && cashToman === 0) {
      throw new Error('حداقل یکی از مقادیر وزن طلا، دلار یا تومان را وارد کنید.');
    }

    const mv = {
      id: newId('smv'),
      sellerId: seller.id,
      sellerName: seller.name,
      direction,
      goldCategory: payload.goldCategory || 'MELTED_GOLD',
      weightGrams,
      karat: Number(payload.karat) || 750,
      usdAmount,
      cashToman,
      description: String(payload.description || (direction === 'IN' ? `ورود توسط ${seller.name}` : `خروج توسط ${seller.name}`)).trim(),
      timestampMillis: Date.now(),
      sheetsSynced: false
    };
    this.data.sellerMovements.unshift(mv);

    if (direction === 'IN') {
      seller.goldInGrams = Number((seller.goldInGrams + weightGrams).toFixed(4));
      seller.usdIn = Number((seller.usdIn + usdAmount).toFixed(2));
      seller.cashInToman += cashToman;
    } else {
      seller.goldOutGrams = Number((seller.goldOutGrams + weightGrams).toFixed(4));
      seller.usdOut = Number((seller.usdOut + usdAmount).toFixed(2));
      seller.cashOutToman += cashToman;
    }
    this.save();
    return { seller, movement: mv };
  }

  // --- Shop Expenses ---
  addExpense(payload, market) {
    const title = String(payload.title || '').trim();
    const amountToman = Math.max(0, Number(payload.amountToman) || 0);
    if (!title || amountToman <= 0) throw new Error('عنوان و مبلغ معتبر هزینه را وارد کنید.');
    const account = this.data.accounts.find((a) => a.id === payload.accountId) || this.data.accounts[0] || null;
    const goldRate = Number(market.gold18kGramToman) || 0;
    const goldEquivalentGrams = goldRate > 0 ? Number((amountToman / goldRate).toFixed(4)) : 0;

    const exp = {
      id: newId('exp'),
      title,
      category: payload.category || 'OTHER',
      amountToman,
      goldEquivalentGrams,
      accountId: account ? account.id : null,
      accountName: account ? account.name : 'صندوق مغازه',
      sellerName: payload.sellerName || null,
      notes: payload.notes ? String(payload.notes).trim() : null,
      timestampMillis: Date.now(),
      sheetsSynced: false
    };
    this.data.expenses.unshift(exp);
    if (account) {
      account.balanceToman -= amountToman;
    }
    this.save();
    return exp;
  }

  deleteExpense(id) {
    const idx = this.data.expenses.findIndex((e) => e.id === id);
    if (idx !== -1) {
      const exp = this.data.expenses[idx];
      const account = this.data.accounts.find((a) => a.id === exp.accountId);
      if (account) account.balanceToman += exp.amountToman;
      this.data.expenses.splice(idx, 1);
      this.save();
    }
  }

  // --- Transactions ---
  recordTransaction(payload) {
    const account = this.data.accounts.find((a) => a.id === payload.accountId) || this.data.accounts[0];
    if (!account) throw new Error('حساب مغازه یافت نشد.');
    const txType = payload.txType || 'SALE';
    const isTransfer = txType === 'TRANSFER_FROM_SHOP' || txType === 'TRANSFER_TO_SHOP';
    const breakdown = isTransfer ? null : this.calculateSaleBreakdown(payload);
    const totalAmountToman = isTransfer
      ? Math.max(0, Number(payload.totalAmountToman) || 0)
      : (Number(payload.totalAmountToman) > 0 ? Number(payload.totalAmountToman) : breakdown.totalAmountToman);

    if (totalAmountToman <= 0) throw new Error('مبلغ تراکنش باید بزرگتر از صفر باشد.');

    const tx = {
      id: newId('tx'),
      invoiceNumber: payload.invoiceNumber || `ZN-${Date.now() % 1_000_000}`,
      txType,
      goldCategory: isTransfer ? 'NONE' : (payload.goldCategory || 'NEW_GOLD'),
      inventoryItemId: payload.inventoryItemId || null,
      customerId: payload.customerId || null,
      customerName: payload.customerName || null,
      sellerId: payload.sellerId || null,
      sellerName: payload.sellerName || null,
      accountId: account.id,
      targetAccountId: payload.targetAccountId || null,
      accountName: account.name,
      weightGrams: isTransfer ? 0 : Math.max(0, Number(payload.weightGrams) || 0),
      karat: Number(payload.karat) || 750,
      unitPriceToman: Math.max(0, Number(payload.unitPriceToman) || 0),
      wagePercent: Math.max(0, Number(payload.wagePercent) || 0),
      profitMarginPercent: Math.max(0, Number(payload.profitMarginPercent) || 0),
      taxPercent: Math.max(0, Number(payload.taxPercent) || 0),
      costBasisToman: isTransfer ? totalAmountToman : breakdown.costBasisToman,
      totalAmountToman,
      calculatedProfitToman: txType === 'SALE' ? breakdown.netProfitToman : 0,
      paymentMethod: payload.paymentMethod || 'POS',
      paymentStatus: payload.paymentStatus || 'PAID',
      notes: payload.notes ? String(payload.notes).trim() : null,
      timestampMillis: Date.now(),
      sheetsSynced: false
    };

    // Update account balances
    if (txType === 'SALE' || txType === 'TRANSFER_TO_SHOP' || txType === 'INVESTOR_DEPOSIT') {
      account.balanceToman += totalAmountToman;
    } else if (txType === 'PURCHASE' || txType === 'TRANSFER_FROM_SHOP' || txType === 'INVESTOR_PAYOUT') {
      account.balanceToman -= totalAmountToman;
      if (txType === 'TRANSFER_FROM_SHOP' && tx.targetAccountId) {
        const target = this.data.accounts.find((a) => a.id === tx.targetAccountId);
        if (target) target.balanceToman += totalAmountToman;
      }
    }

    // Update inventory if selected
    if (tx.inventoryItemId && tx.weightGrams > 0) {
      const inv = this.data.inventory.find((i) => i.id === tx.inventoryItemId);
      if (inv) {
        if (txType === 'SALE') inv.weightGrams = Math.max(0, Number((inv.weightGrams - tx.weightGrams).toFixed(4)));
        if (txType === 'PURCHASE') inv.weightGrams = Number((inv.weightGrams + tx.weightGrams).toFixed(4));
      }
    }

    // Update customer summary
    if (tx.customerId) {
      const cust = this.data.customers.find((c) => c.id === tx.customerId);
      if (cust) {
        if (txType === 'SALE') {
          cust.totalPurchasedToman += totalAmountToman;
          cust.goldBalanceGrams = Number((cust.goldBalanceGrams + tx.weightGrams).toFixed(4));
        } else if (txType === 'PURCHASE') {
          cust.goldBalanceGrams = Number((cust.goldBalanceGrams - tx.weightGrams).toFixed(4));
        }
      }
    }

    // Update seller totals if attributed
    if (tx.sellerId) {
      const seller = this.data.sellers.find((s) => s.id === tx.sellerId);
      if (seller) {
        if (txType === 'SALE') {
          if (tx.goldCategory === 'USD_CURRENCY') seller.usdOut += tx.weightGrams;
          else seller.goldOutGrams = Number((seller.goldOutGrams + tx.weightGrams).toFixed(4));
          seller.cashInToman += totalAmountToman;
        } else if (txType === 'PURCHASE') {
          if (tx.goldCategory === 'USD_CURRENCY') seller.usdIn += tx.weightGrams;
          else seller.goldInGrams = Number((seller.goldInGrams + tx.weightGrams).toFixed(4));
          seller.cashOutToman += totalAmountToman;
        }
      }
    }

    this.data.transactions.unshift(tx);
    this.save();
    return tx;
  }

  // --- Customers & Investors ---
  addCustomer(payload) {
    const fullName = String(payload.fullName || '').trim();
    if (!fullName) throw new Error('نام مشتری الزامی است.');
    const item = {
      id: newId('cust'),
      fullName,
      phone: String(payload.phone || '-').trim(),
      nationalId: payload.nationalId ? String(payload.nationalId).trim() : null,
      goldBalanceGrams: 0,
      cashBalanceToman: 0,
      totalPurchasedToman: 0,
      notes: payload.notes ? String(payload.notes).trim() : null,
      createdAtMillis: Date.now()
    };
    this.data.customers.unshift(item);
    this.save();
    return item;
  }

  deleteCustomer(id) {
    this.data.customers = this.data.customers.filter((c) => c.id !== id);
    this.save();
  }

  addInvestor(payload) {
    const fullName = String(payload.fullName || '').trim();
    if (!fullName) throw new Error('نام سرمایه‌گذار الزامی است.');
    const item = {
      id: newId('invs'),
      fullName,
      phone: String(payload.phone || '-').trim(),
      goldCategory: payload.goldCategory || 'MELTED_GOLD',
      investedGrams: Math.max(0, Number(payload.investedGrams) || 0),
      investedCashToman: Math.max(0, Number(payload.investedCashToman) || 0),
      profitSharePercent: Math.min(100, Math.max(0, Number(payload.profitSharePercent) || 50)),
      realizedProfitToman: 0,
      contractStatus: 'ACTIVE',
      notes: payload.notes ? String(payload.notes).trim() : null,
      createdAtMillis: Date.now()
    };
    this.data.investors.unshift(item);
    this.save();
    return item;
  }

  settleInvestor(id, payoutAmountToman) {
    const inv = this.data.investors.find((i) => i.id === id);
    if (!inv) throw new Error('سرمایه‌گذار یافت نشد.');
    inv.realizedProfitToman += Math.max(0, Number(payoutAmountToman) || 0);
    this.save();
    return inv;
  }

  deleteInvestor(id) {
    this.data.investors = this.data.investors.filter((i) => i.id !== id);
    this.save();
  }

  // --- Voice Notes ---
  addVoiceNote(payload, market) {
    const raw = String(payload.rawTranscript || '').trim();
    if (!raw) throw new Error('متن پیام صوتی خالی است.');
    const parsed = parseVoiceMessage(raw, {
      timestampMillis: payload.recordedAtMillis || Date.now(),
      sellerName: payload.sellerName || null,
      createdOffline: Boolean(payload.createdOffline)
    });
    const note = { id: newId('vox'), ...parsed };
    this.data.voiceNotes.unshift(note);

    if (payload.alsoRecordAsAccountingEntry) {
      if (note.actionType === 'EXPENSE' && note.cashToman > 0) {
        this.addExpense({
          title: note.rawTranscript.slice(0, 60),
          category: 'OTHER',
          amountToman: note.cashToman,
          sellerName: note.sellerName,
          notes: 'ثبت خودکار از پیامگیر صوتی'
        }, market);
      } else if (note.weightGrams > 0 || note.usdAmount > 0) {
        const isUsd = note.goldCategory === 'USD_CURRENCY';
        const qty = isUsd ? note.usdAmount : note.weightGrams;
        const unitRate = isUsd ? (market.usdToToman || 85_000) : (market.gold18kGramToman || 6_850_000);
        const total = note.cashToman > 0 ? note.cashToman : Math.round(qty * unitRate);
        this.recordTransaction({
          txType: note.actionType === 'PURCHASE_IN' ? 'PURCHASE' : 'SALE',
          goldCategory: note.goldCategory,
          customerName: note.counterpartyName !== 'ثبت نشده' ? note.counterpartyName : null,
          sellerName: note.sellerName,
          weightGrams: qty,
          karat: note.karat,
          unitPriceToman: unitRate,
          totalAmountToman: total,
          paymentMethod: 'CASH',
          notes: `ثبت از پیامگیر صوتی: ${note.rawTranscript}`
        });
      }
    }

    this.save();
    return note;
  }

  markVoiceNotesSynced(ids) {
    const idSet = new Set(ids);
    for (const v of this.data.voiceNotes) {
      if (idSet.has(v.id)) v.sheetsSynced = true;
    }
    this.save();
  }

  markAllSynced() {
    this.data.transactions.forEach((t) => { t.sheetsSynced = true; });
    this.data.expenses.forEach((e) => { e.sheetsSynced = true; });
    this.data.sellerMovements.forEach((m) => { m.sheetsSynced = true; });
    this.data.voiceNotes.forEach((v) => { v.sheetsSynced = true; });
    this.save();
  }

  deleteVoiceNote(id) {
    this.data.voiceNotes = this.data.voiceNotes.filter((v) => v.id !== id);
    this.save();
  }

  // --- Price Alerts ---
  addPriceAlert(payload) {
    const targetPrice = Number(payload.targetPrice) || 0;
    if (targetPrice <= 0) throw new Error('قیمت هدف معتبر نیست.');
    const item = {
      id: newId('alrt'),
      assetSymbol: payload.assetSymbol || 'GOLD_18K',
      condition: payload.condition === 'BELOW' ? 'BELOW' : 'ABOVE',
      targetPrice,
      enabled: true,
      triggered: false,
      createdAtMillis: Date.now()
    };
    this.data.priceAlerts.unshift(item);
    this.save();
    return item;
  }

  togglePriceAlert(id) {
    const a = this.data.priceAlerts.find((x) => x.id === id);
    if (a) {
      a.enabled = !a.enabled;
      a.triggered = false;
      this.save();
    }
    return a;
  }

  deletePriceAlert(id) {
    this.data.priceAlerts = this.data.priceAlerts.filter((a) => a.id !== id);
    this.save();
  }

  // --- Settings & Pricing ---
  updateSettings(payload) {
    this.data.settings = {
      ...this.data.settings,
      ...(payload.shopName !== undefined ? { shopName: String(payload.shopName).trim() || 'طلافروشی زرنگار' } : {}),
      ...(payload.ownerName !== undefined ? { ownerName: String(payload.ownerName).trim() || 'مدیر مغازه' } : {}),
      ...(payload.sheetsWebhookUrl !== undefined ? { sheetsWebhookUrl: String(payload.sheetsWebhookUrl).trim() } : {}),
      ...(payload.paymentProvider !== undefined ? { paymentProvider: payload.paymentProvider } : {}),
      ...(payload.merchantId !== undefined ? { merchantId: String(payload.merchantId).trim() } : {}),
      ...(payload.darkTheme !== undefined ? { darkTheme: Boolean(payload.darkTheme) } : {})
    };
    this.save();
    return this.data.settings;
  }

  updatePricing(payload) {
    this.data.pricing = {
      useManualPrice: Boolean(payload.useManualPrice),
      manualGold18kToman: Number(payload.manualGold18kToman) || 0,
      manualMeltedMesghalToman: Number(payload.manualMeltedMesghalToman) || 0,
      manualBullion24kToman: Number(payload.manualBullion24kToman) || 0,
      manualUsdToman: Number(payload.manualUsdToman) || this.data.pricing.manualUsdToman || 85_000
    };
    this.save();
    return this.data.pricing;
  }

  // --- Financial Report ---
  buildReport(period, market) {
    const now = Date.now();
    let cutoff = 0;
    if (period === 'TODAY') {
      const d = new Date();
      d.setHours(0, 0, 0, 0);
      cutoff = d.getTime();
    } else if (period === 'WEEK') {
      cutoff = now - 7 * 86_400_000;
    } else if (period === 'MONTH') {
      cutoff = now - 30 * 86_400_000;
    }

    const periodTxs = this.data.transactions.filter((t) => (t.timestampMillis || 0) >= cutoff);
    const sales = periodTxs.filter((t) => t.txType === 'SALE');
    const purchases = periodTxs.filter((t) => t.txType === 'PURCHASE');
    const periodExpenses = this.data.expenses.filter((e) => (e.timestampMillis || 0) >= cutoff);

    const salesTotal = sales.reduce((s, t) => s + (t.totalAmountToman || 0), 0);
    const purchasesTotal = purchases.reduce((s, t) => s + (t.totalAmountToman || 0), 0);
    const grossProfit = sales.reduce((s, t) => s + (t.calculatedProfitToman || 0), 0);
    const expensesTotal = periodExpenses.reduce((s, e) => s + (e.amountToman || 0), 0);
    const netProfit = grossProfit - expensesTotal;
    const goldRate = Math.max(1, Number(market.gold18kGramToman) || 1);

    const expensesByCategory = {};
    for (const e of periodExpenses) {
      expensesByCategory[e.category] = (expensesByCategory[e.category] || 0) + (e.amountToman || 0);
    }

    const summary = this.getDashboardSummary(market);

    return {
      period: period || 'ALL',
      generatedAtFa: formatDateTimeFa(now),
      salesTotalToman: salesTotal,
      purchasesTotalToman: purchasesTotal,
      grossProfitToman: grossProfit,
      expensesTotalToman: expensesTotal,
      netProfitToman: netProfit,
      netProfitGold18kGrams: Number((netProfit / goldRate).toFixed(3)),
      goldSoldGrams: Number(sales.filter((t) => t.goldCategory !== 'USD_CURRENCY').reduce((s, t) => s + (t.weightGrams || 0), 0).toFixed(3)),
      goldBoughtGrams: Number(purchases.filter((t) => t.goldCategory !== 'USD_CURRENCY').reduce((s, t) => s + (t.weightGrams || 0), 0).toFixed(3)),
      expensesByCategory,
      goldBreakdown: summary.goldBreakdown,
      accounts: this.listAccountsWithGoldEquiv(market),
      sellers: this.data.sellers.map((s) => ({
        ...s,
        netGoldGrams: Number((s.goldInGrams - s.goldOutGrams).toFixed(3)),
        netUsd: Number((s.usdIn - s.usdOut).toFixed(2))
      }))
    };
  }
}

module.exports = { ZarnegarStore };
