const shop = require('../../utils/material-shop')
Page({
  data: { loading: false, error: '', orders: [], tabs: ['全部', '待支付', '待发货', '待收货', '已完成', '已关闭', '已退款'], tabIndex: 0, total: 0, pageNum: 1 },
  onLoad(options) { this.materialId = options.materialId || ''; this._sequence = 0 },
  onShow() { this._hidden = false; return this.loadOrders() },
  onHide() { this._hidden = true; this._sequence++ },
  onUnload() { this.onHide() },
  onPullDownRefresh() { this.loadOrders().finally(() => wx.stopPullDownRefresh()) },
  onReachBottom() { if (!this.data.loading && this.data.orders.length < this.data.total) this.loadOrders(true) },
  selectTab(e) { this.setData({ tabIndex: Number(e.currentTarget.dataset.index) }); this.loadOrders() },
  async loadOrders(append = false) {
    append = append === true
    if (append && this.data.loading) return
    const sequence = ++this._sequence, pageNum = append ? this.data.pageNum + 1 : 1
    this.setData({ loading: true, error: '', ...(append ? {} : { orders: [], total: 0 }) })
    const data = { pageNum, pageSize: 20 }
    const status = ['', 'WAIT_PAY', 'WAIT_SHIP', 'WAIT_RECEIVE', 'COMPLETED', 'CLOSED', ''][this.data.tabIndex]
    if (status) data.orderStatus = status
    if (this.data.tabIndex === 6) data.payStatus = '2'
    if ([2, 3, 4].includes(this.data.tabIndex)) data.payStatus = '1'
    try {
      const responses = await Promise.all((this.data.tabIndex === 5 ? ['CLOSED', 'CANCELLED'] : [data.orderStatus]).map(orderStatus => shop.request({ url: shop.ordersUrl, data: { ...data, ...(orderStatus ? { orderStatus } : {}) } })))
      if (sequence !== this._sequence || this._hidden) return
      const rows = responses.flatMap(response => (response.data || {}).rows || []).map(shop.order).sort((a, b) => String(b.createTime).localeCompare(String(a.createTime)))
      this.setData({ orders: (append ? this.data.orders : []).concat(rows), total: responses.reduce((sum, response) => sum + Number((response.data || {}).total || 0), 0), pageNum })
    } catch (error) { if (sequence === this._sequence && !this._hidden) this.setData({ error: error.message }) }
    finally { if (sequence === this._sequence && !this._hidden) this.setData({ loading: false }) }
  },
  openDetail(e) { wx.navigateTo({ url: `/pages/material-order-detail/material-order-detail?id=${e.currentTarget.dataset.id}` }) },
  openShop() { wx.navigateTo({ url: '/pages/material/material' }) }
})
