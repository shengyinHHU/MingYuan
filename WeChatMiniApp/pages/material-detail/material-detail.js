const { request } = require('../../utils/request')
const app = getApp()

Page({
  data: {
    loading: false,
    buying: false,
    detail: null,
    subjectText: '',
    gradeText: '',
    priceText: '',
    sizeText: '',
    bought: false,
    orderId: null,
    role: 'parent'
  },

  onLoad(options) {
    this.materialId = options.id
    const roles = wx.getStorageSync('roles') || []
    let role = 'parent'
    if (roles.includes('admin')) role = 'admin'
    else if (roles.includes('teacher')) role = 'teacher'
    this.setData({ role })
    this.loadDetail()
  },

  onPullDownRefresh() {
    this.loadDetail().finally(() => wx.stopPullDownRefresh())
  },

  async loadDetail() {
    if (!this.materialId) return
    this.setData({ loading: true })
    try {
      const res = await request({ url: `/miniapp/parent/material/${this.materialId}` })
      const detail = res.data || {}
      this.setData({
        detail,
        priceText: Number(detail.price) === 0 ? '免费' : `¥${Number(detail.price).toFixed(2)}`,
        sizeText: this.formatSize(detail.fileSize),
        bought: Number(detail.bought) === 1
      })
      // 加载字典做翻译
      try {
        const dictRes = await request({ url: '/miniapp/material/dict' })
        const dict = dictRes.data || {}
        this.setData({
          subjectText: this.labelOf(dict.subjects, detail.subjectName) || detail.subjectName,
          gradeText: this.labelOf(dict.grades, detail.gradeName) || detail.gradeName
        })
      } catch (e) {
        this.setData({
          subjectText: detail.subjectName,
          gradeText: detail.gradeName
        })
      }
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  labelOf(list, value) {
    const hit = (list || []).find((i) => i.dictValue === value)
    return hit ? hit.dictLabel : ''
  },

  formatSize(bytes) {
    if (!bytes) return ''
    const n = Number(bytes)
    if (n < 1024) return `${n}B`
    if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)}KB`
    return `${(n / 1024 / 1024).toFixed(1)}MB`
  },

  async buy() {
    if (this.data.buying) return
    if (this.data.bought) {
      wx.showToast({ title: '已购买，可直接下载', icon: 'none' })
      return
    }
    this.setData({ buying: true })
    try {
      const res = await request({
        url: '/miniapp/parent/material/buy',
        method: 'POST',
        data: { materialId: Number(this.materialId) }
      })
      wx.showToast({ title: '购买成功', icon: 'success' })
      this.setData({ bought: true, orderId: res.orderId })
      this.loadDetail()
    } catch (error) {
      wx.showToast({ title: error.message || '购买失败', icon: 'none' })
    } finally {
      this.setData({ buying: false })
    }
  },

  async download() {
    // 已购买，但要先拿到 orderId（可能从 buy 接口返回，也可能从订单列表接口获取）
    let orderId = this.data.orderId
    if (!orderId) {
      try {
        const res = await request({ url: '/miniapp/parent/material/orders' })
        const orders = res.data || []
        const hit = orders.find((i) => Number(i.materialId) === Number(this.materialId))
        if (hit) orderId = hit.orderId
      } catch (error) {
        wx.showToast({ title: '获取订单失败', icon: 'none' })
        return
      }
    }
    if (!orderId) {
      wx.showToast({ title: '订单不存在', icon: 'none' })
      return
    }

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
            wx.showToast({ title: '无法打开文件，已保存到本地', icon: 'none' })
            wx.saveFile({
              tempFilePath: filePath,
              success() {},
              fail() {
                wx.showToast({ title: '保存失败', icon: 'none' })
              }
            })
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
