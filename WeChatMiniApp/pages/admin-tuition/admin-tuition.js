const t = require('../../utils/tuition')
const pad = value => String(value).padStart(2, '0')
const timeRanges = [24, 60, 60].map(count => Array.from({ length: count }, (_, i) => pad(i)))
function localTime(now) {
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}
function billingStep(detail) {
  if (detail.activePayment) return 3
  if (detail.canPay) return 2
  if (detail.billingStatus === 'PENDING_PRICE') return 1
  if (detail.payments.some(p => p.paymentStatus === 'SUCCESS')) return 4
  return 0
}
Page({
  // Keep the same yyyy-MM-dd HH:mm:ss value used by the web form and API.
  data: { loading: false, submitting: false, error: '', rows: [], total: 0, pageNum: 1, searchText: '', filterIndex: 0, filters: ['全部账单', '待核价', '已核价', '历史待核验', '已关闭'], detail: null, allowed: {}, price: '', modeIndex: 0, modes: ['REAL', 'MOCK'], modeLabels: ['真实线下收费', '本地模拟（仅本地可用）'], channelIndex: 0, channels: ['CASH', 'BANK_TRANSFER', 'WECHAT_OFFLINE'], channelLabels: ['现金', '银行转账', '微信线下收款'], payerName: '', providerTradeNo: '', paidTime: '', evidenceKey: '', evidenceName: '', closeReason: '' },
  onLoad(options) {
    this.id = String(options.id || ''); this._visit = 0
    const now = localTime(new Date())
    this.setData({ paidDate: '', paidClock: '', pickerDate: now.slice(0, 10), timeRanges, timeIndex: now.slice(11).split(':').map(Number) })
  },
  onShow() { this._active = true; this._visit++; this.setData({ submitting: false }); return this.load(true) },
  onHide() { this._active = false; this._visit++ },
  onUnload() { this.onHide() },
  onPullDownRefresh() { return this.load(true).finally(() => wx.stopPullDownRefresh()) },
  onReachBottom() { if (!this.id && !this.data.loading && this.data.rows.length < this.data.total) return this.load(false) },
  permitted(action) { return t.auth('staff') && t.can(`system:tuition:${action}`) },
  async load(reset = true) {
    if (!t.auth('staff')) { this.setData({ error: '仅授权管理员或教务可处理收费' }); return }
    const visit = this._visit, sequence = this._loadSequence = (this._loadSequence || 0) + 1, pageNum = reset ? 1 : this.data.pageNum + 1
    this.setData({ loading: true, error: '', allowed: {}, ...(reset ? { detail: null, rows: [] } : {}) })
    try {
      const info = await t.request({ url: '/getInfo' })
      if (!this._active || visit !== this._visit || sequence !== this._loadSequence) return
      getApp().globalData.permissions = info.permissions || []
      getApp().globalData.roles = info.roles || []
      if (!t.auth('staff')) throw new Error('收费角色权限已失效')
      const allowed = {}; ['list', 'query', 'price', 'collect', 'confirm'].forEach(p => { allowed[p] = t.can(`system:tuition:${p}`) }); allowed.download = t.can('system:tuitionReceipt:download')
      this.setData({ allowed })
      if (!allowed[this.id ? 'query' : 'list']) throw new Error('当前账号没有收费查询权限')
      if (this.id) {
        const res = await t.request({ url: `${t.adminUrl}/${encodeURIComponent(this.id)}` })
        if (!this._active || visit !== this._visit || sequence !== this._loadSequence) return
        const detail = t.bill(res.data), pending = t.savedIntent('admin-payment', this.id)
        this.setData({ billingStep: billingStep(detail), retryPayment: !!pending })
        this.setData({ detail, price: detail.originalAmount || '', modeIndex: detail.financeMode === 'MOCK' ? 1 : 0, channels: detail.financeMode === 'MOCK' && detail.mockAllowed ? ['MOCK'] : ['CASH', 'BANK_TRANSFER', 'WECHAT_OFFLINE'], channelLabels: detail.financeMode === 'MOCK' && detail.mockAllowed ? ['本地模拟'] : ['现金', '银行转账', '微信线下收款'], channelIndex: 0, ...(pending ? { payerName: pending.payerName, providerTradeNo: pending.providerTradeNo || '', paidTime: pending.paidTime || '', evidenceKey: pending.evidenceKey || '', evidenceName: pending.evidenceKey ? '已上传凭证（待确认绑定）' : '' } : {}) })
        if (pending) this.setPaidTime(pending.paidTime || '')
      } else {
        const statuses = ['', 'PENDING_PRICE', 'OPEN', 'HISTORY_PENDING', 'CLOSED']
        const res = await t.request({ url: `${t.adminUrl}/list`, data: { pageNum, pageSize: 20, billingStatus: statuses[this.data.filterIndex], studentName: this.data.searchText.trim() } })
        if (this._active && visit === this._visit && sequence === this._loadSequence) this.setData({ rows: (reset ? [] : this.data.rows).concat((res.rows || []).map(t.bill)), total: res.total || 0, pageNum })
      }
    } catch (error) { if (this._active && visit === this._visit && sequence === this._loadSequence) this.setData({ error: error.message }) }
    finally { if (this._active && visit === this._visit && sequence === this._loadSequence) this.setData({ loading: false }) }
  },
  input(e) { const field = e.currentTarget.dataset.field; if (!this.data.submitting && ['searchText', 'price', 'payerName', 'providerTradeNo', 'paidTime', 'closeReason'].includes(field)) this.setData({ [field]: e.detail.value }) },
  setPaidTime(value) {
    this.setData({ paidTime: value, paidDate: value.slice(0, 10), paidClock: value.slice(11), ...(value ? { timeIndex: value.slice(11).split(':').map(Number) } : {}) })
  },
  paidDateChange(e) {
    if (this.data.submitting) return
    this.setPaidTime(`${e.detail.value} ${this.data.timeIndex.map(pad).join(':')}`)
  },
  paidClockChange(e) {
    if (this.data.submitting) return
    this.setPaidTime(`${this.data.paidDate || localTime(new Date()).slice(0, 10)} ${e.detail.value.map(pad).join(':')}`)
  },
  paidTimeNow() { if (!this.data.submitting) this.setPaidTime(localTime(new Date())) },
  mode(e) { if (!this.data.submitting) this.setData({ modeIndex: Number(e.detail.value) }) },
  channel(e) { if (!this.data.submitting) this.setData({ channelIndex: Number(e.detail.value) }) },
  filter(e) { this._visit++; this.setData({ filterIndex: Number(e.detail.value), rows: [] }); return this.load(true) },
  search() { this._visit++; return this.load(true) },
  detail(e) { if (this.data.allowed.query) wx.navigateTo({ url: `/pages/admin-tuition/admin-tuition?id=${encodeURIComponent(String(e.currentTarget.dataset.id))}` }) },
  editPrice() {
    if (!this.data.submitting && this.permitted('price') && this.data.detail && this.data.detail.canPay && !this.data.detail.payments.length && !this.data.retryPayment) this.setData({ billingStep: 1 })
  },
  nextStep(title, selector) {
    wx.showToast({ title, icon: 'none', duration: 2500 })
    if (typeof wx.pageScrollTo === 'function') wx.pageScrollTo({ selector, duration: 300 })
  },
  async savePrice() {
    if (!this._active || this.data.submitting || !this.permitted('price') || !this.data.detail) return
    const visit = this._visit, detail = this.data.detail
    if (detail.billingStatus === 'HISTORY_PENDING') { t.message(new Error('历史账单请在管理网页凭证核验')); return }
    this.setData({ submitting: true })
    try {
      const originalAmount = t.amount(this.data.price), financeMode = this.data.modes[this.data.modeIndex]
      if (!await t.confirm('核定账单', `${t.money(originalAmount)} · ${financeMode === 'MOCK' ? '本地模拟，不涉及真实资金' : '真实线下收费'}，确认核价？`) || !this._active || visit !== this._visit) return
      await t.request({ url: `${t.adminUrl}/${encodeURIComponent(this.id)}/price`, method: 'PUT', data: { originalAmount, financialVersion: detail.financialVersion, financeMode } })
      if (this._active && visit === this._visit) {
        await this.load()
        if (this._active && visit === this._visit && !this.data.error) this.nextStep('核价成功，请登记实际收款', '#collection-form')
      }
    } catch (error) { if (this._active && visit === this._visit) { this.setData({ error: error.message }); t.message(error) } }
    finally { if (this._active && visit === this._visit) this.setData({ submitting: false }) }
  },
  chooseEvidence() {
    if (this.data.submitting || !this.permitted('collect')) return
    wx.chooseMessageFile({ count: 1, type: 'file', extension: ['pdf', 'jpg', 'jpeg', 'png'], success: async res => {
      const file = res.tempFiles[0]
      if (!file) return
      this.setData({ submitting: true })
      try { const key = await t.uploadEvidence(file, 'PAYMENT', this.id); this.setData({ evidenceKey: key, evidenceName: file.name || '凭证已上传' }) } catch (error) { t.message(error) }
      finally { this.setData({ submitting: false }) }
    } })
  },
  async collect() {
    if (!this._active || this.data.submitting || !this.permitted('collect') || !this.data.detail) return
    const visit = this._visit, detail = this.data.detail
    this.setData({ submitting: true })
    try {
      const saved = t.savedIntent('admin-payment', this.id)
      if (!saved && !detail.canPay) throw new Error('当前账单不能登记新收款，请核对现有记录')
      const channel = detail.payableAmount === '0.00' ? 'FREE' : this.data.channels[this.data.channelIndex]
      if (!saved && !this.data.payerName.trim()) throw new Error('请填写实际付款人')
      if (!saved && !['MOCK', 'FREE'].includes(channel) && (!this.data.evidenceKey || !this.data.paidTime.trim())) throw new Error('线下登记须上传凭证并填写实际收款时间')
      if (!saved && ['BANK_TRANSFER', 'WECHAT_OFFLINE'].includes(channel) && !this.data.providerTradeNo.trim()) throw new Error('转账须填写完整流水参考号')
      const body = saved || { channel, userCouponId: detail.userCouponId || null, expectedPayableAmount: detail.payableAmount, financialVersion: detail.financialVersion, payerName: this.data.payerName.trim(), providerTradeNo: this.data.providerTradeNo.trim() || null, evidenceKey: this.data.evidenceKey || null, paidTime: this.data.paidTime.trim() || null }
      if (!await t.confirm('登记收款', `${t.money(body.expectedPayableAmount)} · ${t.status(body.channel)}。登记后须核对确认。`) || !this._active || visit !== this._visit) return
      await t.request({ url: `${t.adminUrl}/${encodeURIComponent(this.id)}/payments`, method: 'POST', data: t.intent('admin-payment', this.id, body) })
      t.clearIntent('admin-payment', this.id)
      if (this._active && visit === this._visit) {
        await this.load()
        if (this._active && visit === this._visit && !this.data.error) this.nextStep('已登记，请核对并确认收款', '#payment-records')
      }
    } catch (error) { if (error.applicationRejected) t.clearIntent('admin-payment', this.id); if (this._active && visit === this._visit) this.setData({ error: error.uncertain ? '登记结果尚未确定，请保留凭证并重试原登记。' : error.message }) }
    finally { if (this._active && visit === this._visit) this.setData({ submitting: false }) }
  },
  async paymentAction(e) {
    if (!this._active || this.data.submitting) return
    const action = e.currentTarget.dataset.action, id = String(e.currentTarget.dataset.id), visit = this._visit
    if (!['confirm', 'close'].includes(action) || !this.permitted(action === 'confirm' ? 'confirm' : 'collect')) return
    const payment = this.data.detail.payments.find(p => String(p.paymentId) === id)
    if (!payment || !['PENDING', 'UNKNOWN'].includes(payment.paymentStatus)) return
    const reason = this.data.closeReason.trim()
    if (action === 'close' && !reason) { t.message(new Error('请填写关闭原因；结果未知时请先人工核对')); return }
    this.setData({ submitting: true })
    try {
      const data = action === 'close' ? { reason } : { evidenceKey: this.data.evidenceKey || payment.evidenceKey || null, providerTradeNo: this.data.providerTradeNo.trim() || payment.providerTradeNo || null, paidTime: this.data.paidTime.trim() || payment.paidTime || null }
      if (await t.confirm(action === 'confirm' ? '确认实际收款' : '关闭付款', action === 'confirm' ? `请核对 ${payment.amountText}、实际时间与凭证。确认后形成资金记录和收据。` : `确认此付款没有成功收款后关闭：${reason}`) && this._active && visit === this._visit) {
        await t.request({ url: `${t.adminUrl}/payments/${encodeURIComponent(id)}/${action}`, method: 'POST', data })
        if (this._active && visit === this._visit) {
          await this.load()
          if (this._active && visit === this._visit && !this.data.error) this.nextStep(action === 'confirm' ? '收款已确认，请查看收据' : '付款记录已关闭', action === 'confirm' ? '#receipt-records' : '#collection-form')
        }
      }
    } catch (error) { if (this._active && visit === this._visit) t.message(error) }
    finally { if (this._active && visit === this._visit) this.setData({ submitting: false }) }
  },
  async receipt(e) { if (!t.can('system:tuitionReceipt:download')) return; try { await t.downloadReceipt(String(e.currentTarget.dataset.id), true) } catch (error) { t.message(error) } }
})
