const shop = require('../../utils/material-shop')
Page({
  data: { loading: false, submitting: false, error: '', detail: null, address: null, quantity: 1, buyerRemark: '', estimatedTotal: '', pending: false, order: null },
  onLoad(options) {
    this.materialId = options.id
    this._sequence = 0
    this._visit = 0
    this._addressPick = 0
    const user = wx.getStorageSync('userInfo') || {}
    this._pendingKey = `material-checkout:${user.userId || ''}:${this.materialId}`
    this._submission = wx.getStorageSync(this._pendingKey) || null
    if (this._submission) this.setData({ pending: true, quantity: this._submission.quantity, buyerRemark: this._submission.buyerRemark, address: this._submission.address ? { ...this._submission.address } : null })
  },
  onShow() { this._visit = (this._visit || 0) + 1; this._hidden = false; this.setData({ submitting: false, pending: Boolean(this._submission) }); return this.load() },
  onHide() { this._hidden = true; this._sequence++; this._visit = (this._visit || 0) + 1 },
  onUnload() { this.onHide(); this._addressPick++ },
  async load() {
    if (!this.materialId) { this.setData({ error: '商品不存在' }); return }
    const sequence = ++this._sequence
    this.setData({ loading: true, error: '', detail: null })
    try {
      const response = await shop.request({ url: `/miniapp/parent/material/${this.materialId}` })
      if (sequence !== this._sequence || this._hidden) return
      const detail = shop.product(response.data)
      this.setData({ detail, quantity: detail.deliveryType === 'DIGITAL' ? 1 : this.data.quantity })
      this.updateTotal()
      if (detail.deliveryType === 'PHYSICAL') {
        const addresses = await shop.request({ url: shop.addressUrl })
        if (sequence !== this._sequence || this._hidden) return
        const selected = this._selectedAddressId || (this.data.address || {}).addressId
        const list = addresses.data || []
        const address = (this._submission && this._submission.address) || (this._selectedAddress && !this._selectedAddress.addressId ? this._selectedAddress : null) || list.find(item => item.addressId === selected) || list.find(item => String(item.isDefault) === '1') || list[0]
        this.setData({ address: address ? { ...address } : null })
      }
    } catch (error) { if (sequence === this._sequence && !this._hidden) this.setData({ error: error.message }) }
    finally { if (sequence === this._sequence && !this._hidden) this.setData({ loading: false }) }
  },
  updateTotal() {
    if (!this.data.detail) return
    this.setData({ estimatedTotal: shop.calculateTotal(this.data.detail.price, this.data.quantity, this.data.detail.deliveryType === 'PHYSICAL' ? this.data.detail.shippingFee || '0.00' : '0.00') })
  },
  changeQuantity(e) {
    if (this.data.pending || this.data.submitting) return
    try {
      const detail = this.data.detail
      this.setData({ quantity: shop.normalizeQuantity(this.data.quantity + Number(e.currentTarget.dataset.delta), detail.availableStock, detail.purchaseLimit) }); this.updateTotal()
    } catch (error) { shop.message(error) }
  },
  onRemark(e) { if (!this.data.pending) this.setData({ buyerRemark: e.detail.value }) },
  chooseAddress() {
    if (this.data.pending || this.data.submitting) return
    const pick = ++this._addressPick
    wx.navigateTo({ url: '/pages/material-address/material-address?pick=1', events: { selected: address => {
      if (pick !== this._addressPick || this._submission || this.data.submitting) return
      this._selectedAddress = { ...address }; this._selectedAddressId = address.addressId; this.setData({ address: this._selectedAddress })
    } } })
  },
  async submit() {
    if (this.data.submitting || (!this._submission && !this.data.detail)) return
    const visit = this._visit
    const isActive = () => !this._hidden && visit === this._visit
    this.setData({ submitting: true, error: '' })
    try {
      if (this._needsPriceConfirmation) {
        const confirmed = await shop.confirm('确认最新价格', `最新单价为¥${this.data.detail.price}，确认后才能重新提交订单。`)
        if (!isActive()) return
        if (!confirmed) { this.setData({ error: '最新价格尚未确认，请核对商品单价' }); return }
        this._needsPriceConfirmation = false
      }
      if (!this._submission) {
        const detail = this.data.detail
        const quantity = detail.deliveryType === 'DIGITAL' ? 1 : shop.normalizeQuantity(this.data.quantity, detail.availableStock, detail.purchaseLimit)
        if (detail.deliveryType === 'PHYSICAL' && !this.data.address) throw new Error('请选择完整收货地址')
        this._submission = { materialId: this.materialId, quantity, expectedPrice: detail.price, buyerRemark: this.data.buyerRemark.trim(), clientRequestId: `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`, ...(detail.deliveryType === 'PHYSICAL' ? (this.data.address.addressId ? { addressId: this.data.address.addressId } : { address: { ...this.data.address } }) : {}) }
        wx.setStorageSync(this._pendingKey || `material-checkout:${this.materialId}`, this._submission)
        this.setData({ pending: true })
      }
      const response = await shop.request({ url: shop.ordersUrl, method: 'POST', data: this._submission })
      // An inactive visit retains the immutable request for safe reconciliation by retry.
      if (!isActive()) return
      if (!response.data || typeof response.data.orderId !== 'string' || !/^\d+$/.test(response.data.orderId) || !/^\d+\.\d{2}$/.test(response.data.payableAmount) || typeof response.data.orderStatus !== 'string') throw new Error('订单响应无法确认，请重试核对')
      const order = shop.order(response.data)
      wx.removeStorageSync(this._pendingKey || `material-checkout:${this.materialId}`)
      this._submission = null
      this.setData({ order, pending: false })
      wx.redirectTo({ url: `/pages/material-order-detail/material-order-detail?id=${order.orderId}` })
    } catch (error) {
      if (!isActive()) return
      this.setData({ error: this._submission && !error.applicationRejected ? '提交结果尚未确认。请重试，系统会核对同一订单，请勿重复新建。' : error.message })
      if (error.applicationRejected) {
        this._submission = null; wx.removeStorageSync(this._pendingKey || `material-checkout:${this.materialId}`); this.setData({ pending: false })
        if (/价格/.test(error.message)) {
          const oldPrice = this.data.detail && this.data.detail.price
          await this.load()
          if (!isActive()) return
          if (this.data.detail && this.data.detail.price !== oldPrice) {
            this._needsPriceConfirmation = true
            const confirmed = await shop.confirm('价格已更新', `最新单价为¥${this.data.detail.price}，确认此价格后可重新提交。`)
            if (!isActive()) return
            this._needsPriceConfirmation = !confirmed
            if (this._needsPriceConfirmation) this.setData({ error: '最新价格尚未确认，请核对商品单价' })
          }
        }
      }
    } finally { if (isActive()) this.setData({ submitting: false }) }
  }
})
