const { request } = require('../../utils/request')

Page({
  data: {
    loading: false,
    groups: [],
    total: 0,
    // 新增/编辑弹层
    editVisible: false,
    editForm: null,
    campusOptions: ['万达', '百家湖']
  },

  onShow() { this.load() },
  onPullDownRefresh() { this.load().finally(() => wx.stopPullDownRefresh()) },

  async load() {
    this.setData({ loading: true })
    try {
      const res = await request({ url: '/system/wificonfig/list' })
      const items = (res.data || []).map((w) => ({
        wifiId: w.wifiId,
        campusName: w.campusName || '未分配',
        ssid: w.ssid || '—',
        bssid: w.bssid || '',
        status: w.status,
        remark: w.remark || ''
      }))
      const byCampus = {}
      items.forEach((w) => {
        (byCampus[w.campusName] || (byCampus[w.campusName] = [])).push(w)
      })
      const order = ['万达', '百家湖']
      const keys = Object.keys(byCampus).sort((a, b) => {
        const ia = order.indexOf(a); const ib = order.indexOf(b)
        return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib)
      })
      const groups = keys.map((k) => ({
        campus: k,
        wifis: byCampus[k].sort((a, b) => a.ssid.localeCompare(b.ssid, 'zh'))
      }))
      this.setData({ groups, total: items.length, loading: false })
    } catch (e) {
      this.setData({ loading: false, groups: [] })
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  openAdd() {
    this.setData({
      editVisible: true,
      editForm: { wifiId: null, ssid: '', bssid: '', campusIndex: 0, remark: '' }
    })
  },
  openEdit(e) {
    const id = e.currentTarget.dataset.id
    let wifi = null
    this.data.groups.forEach((g) => { g.wifis.forEach((w) => { if (w.wifiId === id) wifi = w }) })
    if (!wifi) return
    const idx = this.data.campusOptions.indexOf(wifi.campusName)
    this.setData({
      editVisible: true,
      editForm: {
        wifiId: wifi.wifiId,
        ssid: wifi.ssid === '—' ? '' : wifi.ssid,
        bssid: wifi.bssid,
        campusIndex: idx < 0 ? 0 : idx,
        remark: wifi.remark
      }
    })
  },
  closeEdit() { this.setData({ editVisible: false, editForm: null }) },
  onField(e) { this.setData({ [`editForm.${e.currentTarget.dataset.field}`]: e.detail.value }) },
  onCampus(e) { this.setData({ 'editForm.campusIndex': Number(e.detail.value) }) },

  async save() {
    const f = this.data.editForm
    if (!f) return
    if (!f.ssid.trim()) { wx.showToast({ title: '请填写WiFi名称', icon: 'none' }); return }
    const payload = {
      campusName: this.data.campusOptions[f.campusIndex],
      ssid: f.ssid.trim(),
      bssid: f.bssid.trim() || null,
      status: '0'
    }
    if (f.remark.trim()) payload.remark = f.remark.trim()
    try {
      wx.showLoading({ title: '保存中' })
      if (f.wifiId) {
        payload.wifiId = f.wifiId
        await request({ url: '/system/wificonfig', method: 'PUT', data: payload })
      } else {
        await request({ url: '/system/wificonfig', method: 'POST', data: payload })
      }
      wx.hideLoading()
      wx.showToast({ title: '已保存', icon: 'success' })
      this.setData({ editVisible: false, editForm: null })
      this.load()
    } catch (e) {
      wx.hideLoading()
      wx.showToast({ title: e.message || '保存失败', icon: 'none' })
    }
  },

  remove(e) {
    const id = e.currentTarget.dataset.id
    const name = e.currentTarget.dataset.name
    wx.showModal({
      title: '删除WiFi',
      content: `确定删除「${name}」吗？`,
      confirmColor: '#d94c4c',
      success: async (res) => {
        if (!res.confirm) return
        try {
          wx.showLoading({ title: '删除中' })
          await request({ url: `/system/wificonfig/${id}`, method: 'DELETE' })
          wx.hideLoading()
          wx.showToast({ title: '已删除', icon: 'success' })
          this.load()
        } catch (err) {
          wx.hideLoading()
          wx.showToast({ title: err.message || '删除失败', icon: 'none' })
        }
      }
    })
  },

  noop() {}
})
