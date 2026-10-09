const t = require('../../utils/tuition')
Page({
  data: { loading: false, submitting: false, error: '', detail: null, quote: null, coupons: [], couponOptions: ['不使用优惠券'], couponIndex: 0, selectedCouponId: '', retrying: false, paymentId: '' },
  onLoad(options) { this.id = String(options.id || ''); this._visit = 0 },
  onShow() { this._active = true; this._visit++; this.setData({ submitting: false }); return this.load() },
  onHide() { this._active = false; this._visit++ },
  onUnload() { this.onHide() },
  onPullDownRefresh() { return this.load().finally(() => wx.stopPullDownRefresh()) },
  async load() {
    if (!t.auth('parent') || !this.id) return
    const visit = this._visit, sequence = this._loadSequence = (this._loadSequence || 0) + 1
    const active = () => this._active && visit === this._visit && sequence === this._loadSequence
    this.setData({ loading: true, error: '', detail: null })
    try {
      const res = await t.request({ url: `${t.parentUrl}/${encodeURIComponent(this.id)}` })
      if (!active()) return
      const detail = t.bill(res.data)
      if (detail.payments.some(p => p.paymentStatus === 'SUCCESS')) t.clearIntent('payment', this.id)
      const pending = t.savedIntent('payment', this.id)
      this.setData({ detail, retrying: !!pending, paymentId: detail.activePayment ? detail.activePayment.paymentId : '', quote: null })
      if (detail.canPay) await this.quoteCoupon(pending ? pending.userCouponId : this.data.selectedCouponId)
    } catch (error) { if (active()) this.setData({ error: error.message }) }
    finally { if (active()) this.setData({ loading: false }) }
  },
  async quoteCoupon(id) {
    const visit = this._visit, sequence = this._quoteSequence = (this._quoteSequence || 0) + 1
    const res = await t.request({ url: `${t.parentUrl}/${encodeURIComponent(this.id)}/quote`, method: 'POST', data: { userCouponId: id || null } })
    if (!this._active || visit !== this._visit || sequence !== this._quoteSequence) return
    const quote = res.data, coupons = (quote.coupons || []).map(t.coupon)
    const selected = quote.userCouponId || ''
    this.setData({ quote: { ...quote, isFree: t.cents(quote.payableAmount) === 0, originalText: t.money(quote.originalAmount), discountText: t.money(quote.discountAmount), payableText: t.money(quote.payableAmount) }, coupons, couponOptions: ['不使用优惠券'].concat(coupons.map(c => `${c.couponName}（${c.amountText}）`)), selectedCouponId: selected, couponIndex: selected ? coupons.findIndex(c => String(c.userCouponId) === String(selected)) + 1 : 0 })
  },
  async chooseCoupon(e) {
    if (this.data.submitting || this.data.retrying) return
    const index = Number(e.detail.value), id = index ? this.data.coupons[index - 1].userCouponId : ''
    this.setData({ quote: null })
    try { await this.quoteCoupon(id) } catch (error) { t.message(error) }
  },
  async pay() {
    if (!this._active || this.data.submitting || !t.auth('parent')) return
    const visit = this._visit, detail = this.data.detail, quote = this.data.quote, saved = t.savedIntent('payment', this.id)
    if (!detail || (!saved && (!detail.canPay || !quote))) return
    const payload = saved || { channel: t.cents(quote.payableAmount) === 0 ? 'FREE' : 'MOCK', userCouponId: quote.userCouponId || null, expectedPayableAmount: quote.payableAmount, financialVersion: quote.financialVersion }
    if (payload.channel === 'MOCK' && (detail.financeMode !== 'MOCK' || detail.mockAllowed !== true)) { t.message(new Error('请按线下说明缴费，等待财务核对')); return }
    this.setData({ submitting: true, error: '' })
    try {
      if (!await t.confirm(payload.channel === 'FREE' ? '确认费用减免' : '本地模拟缴费', payload.channel === 'FREE' ? '应付为0元，将生成费用减免确认单。' : `模拟缴费 ${t.money(payload.expectedPayableAmount)}，不涉及真实资金。`)) return
      if (!this._active || visit !== this._visit) return
      const body = t.intent('payment', this.id, payload)
      this.setData({ retrying: true })
      const res = await t.request({ url: `${t.parentUrl}/${encodeURIComponent(this.id)}/payments`, method: 'POST', data: body })
      const payment = res.data
      if (!payment || !payment.paymentId || !['PENDING', 'UNKNOWN', 'SUCCESS', 'FAILED', 'CLOSED'].includes(payment.paymentStatus)) { const failure = new Error('缴费结果尚未确定'); failure.uncertain = true; throw failure }
      if (!this._active || visit !== this._visit) return
      this.setData({ paymentId: payment.paymentId || '' })
      if (payment.paymentStatus === 'SUCCESS') { t.clearIntent('payment', this.id); await this.load(); return }
      if (payment.channel === 'MOCK' && payment.paymentStatus === 'PENDING') await this.confirmMock(payment.paymentId, visit)
      else await this.checkPayment(payment.paymentId, visit)
    } catch (error) {
      if (!this._active || visit !== this._visit) return
      if (error.applicationRejected && !this.data.paymentId) t.clearIntent('payment', this.id)
      this.setData({ error: error.uncertain ? '缴费结果尚未确定，请刷新或重试原请求；请勿重复办理线下缴费。' : error.message, retrying: !!t.savedIntent('payment', this.id) })
    } finally { if (this._active && visit === this._visit) this.setData({ submitting: false }) }
  },
  async confirmMock(id, visit) {
    let error
    try { await t.request({ url: `${t.parentUrl}/payments/${encodeURIComponent(String(id))}/mock-confirm`, method: 'POST' }) } catch (failure) { error = failure }
    if (!this._active || visit !== this._visit) return
    const final = await this.checkPayment(id, visit)
    if (error && final && final.paymentStatus !== 'SUCCESS') throw error
  },
  async checkPayment(id, visit = this._visit) {
    const res = await t.request({ url: `${t.parentUrl}/payments/${encodeURIComponent(String(id))}` })
    if (!this._active || visit !== this._visit) return
    if (['SUCCESS', 'FAILED', 'CLOSED'].includes(res.data.paymentStatus)) t.clearIntent('payment', this.id)
    await this.load()
    return res.data
  },
  async resumePayment() {
    if (this.data.submitting || !this._active || !t.auth('parent')) return
    const detail = this.data.detail, payment = detail && detail.activePayment
    if (!payment) return this.pay()
    const visit = this._visit
    this.setData({ submitting: true })
    try { if (payment.channel === 'MOCK' && payment.paymentStatus === 'PENDING' && detail.financeMode === 'MOCK' && detail.mockAllowed === true && await t.confirm('本地模拟缴费', '继续确认此笔模拟付款，不涉及真实资金。')) { if (this._active && visit === this._visit) await this.confirmMock(payment.paymentId, visit) } else await this.checkPayment(payment.paymentId, visit) } catch (error) { if (this._active && visit === this._visit) t.message(error) }
    finally { if (this._active && visit === this._visit) this.setData({ submitting: false }) }
  },
  async cancelEnrollment() {
    if (!this._active || this.data.submitting || !t.auth('parent')) return
    const detail = this.data.detail, visit = this._visit
    if (!detail || !detail.canCancel || t.savedIntent('payment', this.id)) { t.message(new Error('已有收款、待核验或结果未知，请先联系财务核对或申请退费')); return }
    this.setData({ submitting: true })
    try { if (await t.confirm('取消未缴费报名', '取消此报名并释放课程名额？后端将再次核对收款状态。') && this._active && visit === this._visit) { await t.request({ url: `/miniapp/parent/enrollment/${encodeURIComponent(this.id)}`, method: 'DELETE' }); if (this._active && visit === this._visit) await this.load() } } catch (error) { if (this._active && visit === this._visit) t.message(error) }
    finally { if (this._active && visit === this._visit) this.setData({ submitting: false }) }
  },
  receipts() { wx.navigateTo({ url: `/pages/tuition-receipt/tuition-receipt?id=${encodeURIComponent(this.id)}` }) },
  refunds() { wx.navigateTo({ url: `/pages/tuition-refund/tuition-refund?id=${encodeURIComponent(this.id)}` }) }
})
