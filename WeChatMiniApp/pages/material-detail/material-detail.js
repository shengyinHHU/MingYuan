const shop = require('../../utils/material-shop')
Page({
  data: { loading: false, error: '', detail: null },
  onLoad(options) { this.materialId = options.id; this._sequence = 0 },
  onShow() { this._hidden = false; return this.loadDetail() },
  onHide() { this._hidden = true; this._sequence++ },
  onUnload() { this.onHide() },
  onPullDownRefresh() { this.loadDetail().finally(() => wx.stopPullDownRefresh()) },
  async loadDetail() {
    const sequence = ++this._sequence
    this.setData({ loading: true, error: '', detail: null })
    try {
      if (!this.materialId) throw new Error('商品不存在')
      const info = await shop.request({ url: '/getInfo' })
      if (sequence !== this._sequence || this._hidden) return
      const roles = info.roles || []
      this._role = roles.includes('admin') ? 'admin' : roles.includes('parent') ? 'parent' : ''
      this.setData({ role: this._role })
      if (!this._role) throw new Error('当前账号仅可在资料管理提交草稿')
      const response = await shop.request({ url: `${this._role === 'admin' ? '/system/material' : '/miniapp/parent/material'}/${this.materialId}` })
      if (sequence !== this._sequence || this._hidden) return
      this.setData({ detail: shop.product(response.data) })
    } catch (error) { if (sequence === this._sequence && !this._hidden) this.setData({ error: error.message }) }
    finally { if (sequence === this._sequence && !this._hidden) this.setData({ loading: false }) }
  },
  buy() {
    if (this._role !== 'parent') return
    const detail = this.data.detail
    if (!detail) return
    if (detail.deliveryType === 'DIGITAL' && detail.bought) { this.openOrders(); return }
    if (detail.deliveryType === 'PHYSICAL' && Number(detail.availableStock) <= 0) return
    wx.navigateTo({ url: `/pages/material-checkout/material-checkout?id=${this.materialId}` })
  },
  openOrders() { if (this._role === 'parent') wx.navigateTo({ url: '/pages/material-orders/material-orders' }) },
  previewImage(e) {
    const detail = this.data.detail
    wx.previewImage({ current: e.currentTarget.dataset.url, urls: detail.gallery.concat(detail.detailImages).map(image => image.imageUrl) })
  }
})
