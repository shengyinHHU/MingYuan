const t = require('../../utils/tuition')
Page({
  data: { loading: false, opening: '', error: '', rows: [], total: 0, pageNum: 1 },
  onLoad(options) { this.id = String(options.id || ''); this._visit = 0 },
  onShow() { this._active = true; this._visit++; return this.load(true) },
  onHide() { this._active = false; this._visit++ },
  onUnload() { this.onHide() },
  onPullDownRefresh() { return this.load(true).finally(() => wx.stopPullDownRefresh()) },
  onReachBottom() { if (!this.data.loading && this.data.rows.length < this.data.total) return this.load(false) },
  async load(reset = true) {
    if (!t.auth('parent')) return
    const visit = this._visit, sequence = this._loadSequence = (this._loadSequence || 0) + 1, pageNum = reset ? 1 : this.data.pageNum + 1
    this.setData({ loading: true, error: '' })
    try { const res = await t.request({ url: `${t.parentUrl}/receipts`, data: { pageNum, pageSize: 20, ...(this.id ? { enrollmentId: this.id } : {}) } }); if (this._active && visit === this._visit && sequence === this._loadSequence) this.setData({ rows: (reset ? [] : this.data.rows).concat((res.rows || []).map(t.receipt)), total: res.total || 0, pageNum }) } catch (error) { if (this._active && visit === this._visit && sequence === this._loadSequence) this.setData({ error: error.message }) }
    finally { if (this._active && visit === this._visit && sequence === this._loadSequence) this.setData({ loading: false }) }
  },
  async open(e) {
    if (this.data.opening || !t.auth('parent')) return
    const id = String(e.currentTarget.dataset.id), row = this.data.rows.find(r => String(r.receiptId) === id)
    if (!row || !row.canDownload) return
    this.setData({ opening: id })
    try { await t.downloadReceipt(id) } catch (error) { t.message(error) } finally { this.setData({ opening: '' }) }
  }
})
