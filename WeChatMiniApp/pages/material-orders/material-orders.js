const { request } = require('../../utils/request')
const app = getApp()

Page({
  data: {
    loading: false,
    orders: [],
    showEmpty: false
  },

  onLoad() {
    this.loadOrders()
  },

  onPullDownRefresh() {
    this.loadOrders().finally(() => wx.stopPullDownRefresh())
  },

  async loadOrders() {
    this.setData({ loading: true })
    try {
      const res = await request({ url: '/miniapp/parent/material/orders' })
      const orders = this.decorate(res.data || [])
      this.setData({ orders, showEmpty: orders.length === 0 })
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' })
      this.setData({ orders: [], showEmpty: true })
    } finally {
      this.setData({ loading: false })
    }
  },

  decorate(list) {
    return list.map((item) => ({
      ...item,
      amountText: Number(item.amount) === 0 ? '免费' : `¥${Number(item.amount).toFixed(2)}`,
      statusText: item.payStatus === '1' ? '已支付' : (item.payStatus === '2' ? '已退款' : '未支付'),
      statusClass: item.payStatus === '1' ? 'paid' : (item.payStatus === '2' ? 'refund' : 'unpaid'),
      timeText: this.formatTime(item.payTime || item.createTime)
    }))
  },

  formatTime(t) {
    if (!t) return ''
    return String(t).replace('T', ' ').substring(0, 16)
  },

  async download(e) {
    const orderId = e.currentTarget.dataset.id
    if (!orderId) return
    wx.showLoading({ title: '准备下载...', mask: true })
    const baseUrl = app.globalData.baseUrl
    const token = wx.getStorageSync('token')
    wx.downloadFile({
      url: `${baseUrl}/miniapp/parent/material/download/${orderId}`,
      header: { Authorization: `Bearer ${token}` },
      success(res) {
        wx.hideLoading()
        if (res.statusCode !== 200) {
          wx.showToast({ title: '下载失败', icon: 'none' })
          return
        }
        const filePath = res.tempFilePath
        wx.openDocument({
          filePath,
          fileType: 'pdf',
          success() {},
          fail() {
            wx.showToast({ title: '无法打开文件', icon: 'none' })
          }
        })
      },
      fail() {
        wx.hideLoading()
        wx.showToast({ title: '下载失败', icon: 'none' })
      }
    })
  }
})
