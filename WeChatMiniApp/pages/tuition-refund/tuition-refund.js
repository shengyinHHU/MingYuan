const t = require('../../utils/tuition')
Page({
  data: { loading: false, submitting: false, error: '', detail: null, rows: [], total: 0, pageNum: 1, amount: '', reason: '', kindIndex: 0, kindOptions: ['不退课退费', '退课退费'], retrying: false },
  onLoad(options) { this.id = String(options.id || ''); this._visit = 0 },
  onShow() { this._active = true; this._visit++; this.setData({ submitting: false }); return this.load(true) },
  onHide() { this._active = false; this._visit++ },
  onUnload() { this.onHide() },
  onPullDownRefresh() { return this.load(true).finally(() => wx.stopPullDownRefresh()) },
  onReachBottom() { if (!this.data.loading && this.data.rows.length < this.data.total) return this.load(false) },
  input(e) { if (!this.data.submitting && !this.data.retrying && ['amount', 'reason'].includes(e.currentTarget.dataset.field)) this.setData({ [e.currentTarget.dataset.field]: e.detail.value }) },
  kind(e) { if (!this.data.submitting && !this.data.retrying) this.setData({ kindIndex: Number(e.detail.value) }) },
  async load(reset = true) {
    if (!t.auth('parent')) return
    const visit = this._visit, sequence = this._loadSequence = (this._loadSequence || 0) + 1, pageNum = reset ? 1 : this.data.pageNum + 1
    this.setData({ loading: true, error: '' })
    try {
      if (this.id && reset) {
        const bill = await t.request({ url: `${t.parentUrl}/${encodeURIComponent(this.id)}` })
        if (!this._active || visit !== this._visit || sequence !== this._loadSequence) return
        const pending = t.savedIntent('refund', this.id)
        this.setData({ detail: t.bill(bill.data), retrying: !!pending, ...(pending ? { amount: pending.requestedAmount, reason: pending.reason, kindIndex: pending.refundKind === 'WITHDRAWAL' ? 1 : 0 } : {}) })
      }
      const res = await t.request({ url: `${t.parentUrl}/refunds`, data: { pageNum, pageSize: 20, ...(this.id ? { enrollmentId: this.id } : {}) } })
      if (this._active && visit === this._visit && sequence === this._loadSequence) this.setData({ rows: (reset ? [] : this.data.rows).concat((res.rows || []).map(t.refund)), total: res.total || 0, pageNum })
    } catch (error) { if (this._active && visit === this._visit && sequence === this._loadSequence) this.setData({ error: error.message }) }
    finally { if (this._active && visit === this._visit && sequence === this._loadSequence) this.setData({ loading: false }) }
  },
  async apply() {
    if (!this._active || this.data.submitting || !t.auth('parent') || !this.id) return
    const visit = this._visit, detail = this.data.detail
    this.setData({ submitting: true, error: '' })
    try {
      const saved = t.savedIntent('refund', this.id)
      if (!saved && (!detail || !detail.canRefund)) throw new Error('当前无可申请退款金额，或已有退费正在处理')
      const payment = detail.payments.find(p => p.paymentStatus === 'SUCCESS' && p.channel !== 'FREE')
      if (!saved && !payment) throw new Error('没有可退款的成功收款')
      const requested = saved ? saved.requestedAmount : t.amount(this.data.amount)
      if (t.cents(requested) <= 0 || (!saved && t.cents(requested) > t.cents(detail.refundableAmount))) throw new Error('申请金额须大于0且不超过可退金额')
      const reason = saved ? saved.reason : this.data.reason.trim()
      if (!reason) throw new Error('请填写退费原因')
      if (!await t.confirm('提交退费申请', `${t.money(requested)}，${saved ? (saved.refundKind === 'WITHDRAWAL' ? '退课退费' : '不退课退费') : this.data.kindOptions[this.data.kindIndex]}。审核通过后仍需财务执行，成功后才代表退款完成。`)) return
      if (!this._active || visit !== this._visit) return
      const payload = t.intent('refund', this.id, saved || { paymentId: payment.paymentId, refundKind: this.data.kindIndex ? 'WITHDRAWAL' : 'PARTIAL', requestedAmount: requested, reason })
      this.setData({ retrying: true })
      await t.request({ url: `${t.parentUrl}/refunds`, method: 'POST', data: payload })
      t.clearIntent('refund', this.id)
      if (this._active && visit === this._visit) { this.setData({ amount: '', reason: '', retrying: false }); await this.load(true) }
    } catch (error) { if (this._active && visit === this._visit) { if (error.applicationRejected) t.clearIntent('refund', this.id); this.setData({ error: error.uncertain ? '申请结果尚未确定，请重试原申请以查询结果。' : error.message, retrying: !!t.savedIntent('refund', this.id) }) } }
    finally { if (this._active && visit === this._visit) this.setData({ submitting: false }) }
  },
  async cancel(e) {
    if (this.data.submitting || !this._active || !t.auth('parent')) return
    const id = String(e.currentTarget.dataset.id), row = this.data.rows.find(r => String(r.refundId) === id), visit = this._visit
    if (!row || !row.canCancel) return
    this.setData({ submitting: true })
    try { if (await t.confirm('撤回申请', '撤回尚未审核的退费申请？') && this._active && visit === this._visit) { await t.request({ url: `${t.parentUrl}/refunds/${encodeURIComponent(id)}/cancel`, method: 'POST' }); if (this._active && visit === this._visit) await this.load(true) } } catch (error) { if (this._active && visit === this._visit) t.message(error) }
    finally { if (this._active && visit === this._visit) this.setData({ submitting: false }) }
  }
})
