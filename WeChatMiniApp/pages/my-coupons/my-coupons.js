const t = require('../../utils/tuition')
Page({
  data: { loading: false, error: '', rows: [], total: 0, pageNum: 1, filterIndex: 0, filters: ['全部优惠券', '可使用', '占用中', '已使用', '已过期', '已撤回'] },
  onLoad() { this._visit = 0 },
  onShow() { this._active = true; this._visit++; return this.load(true) },
  onHide() { this._active = false; this._visit++ },
  onUnload() { this.onHide() },
  onPullDownRefresh() { return this.load(true).finally(() => wx.stopPullDownRefresh()) },
  onReachBottom() { if (!this.data.loading && this.data.rows.length < this.data.total) return this.load(false) },
  filter(e) { this._visit++; this.setData({ filterIndex: Number(e.detail.value), rows: [] }); return this.load(true) },
  async load(reset = true) {
    if (!t.auth('parent')) return
    const visit = this._visit, sequence = this._loadSequence = (this._loadSequence || 0) + 1, pageNum = reset ? 1 : this.data.pageNum + 1, statuses = ['', 'AVAILABLE', 'LOCKED', 'USED', 'EXPIRED', 'REVOKED']
    this.setData({ loading: true, error: '' })
    try { const res = await t.request({ url: '/miniapp/parent/coupons', data: { pageNum, pageSize: 20, displayStatus: statuses[this.data.filterIndex] } }); if (this._active && visit === this._visit && sequence === this._loadSequence) this.setData({ rows: (reset ? [] : this.data.rows).concat((res.rows || []).map(t.coupon)), total: res.total || 0, pageNum }) } catch (error) { if (this._active && visit === this._visit && sequence === this._loadSequence) this.setData({ error: error.message }) }
    finally { if (this._active && visit === this._visit && sequence === this._loadSequence) this.setData({ loading: false }) }
  }
})
