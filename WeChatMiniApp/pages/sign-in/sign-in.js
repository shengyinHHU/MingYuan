const { request } = require('../../utils/request')

Page({
  data: {
    loading: false,
    list: [],
    showEmpty: false
  },

  onUnload() { this._unloaded = true; this._version = (this._version || 0) + 1 },

  onShow() {
    this.loadList()
  },

  onPullDownRefresh() {
    this.loadList().finally(() => wx.stopPullDownRefresh())
  },

  async loadList() {
    const version = this._version = (this._version || 0) + 1
    const token = wx.getStorageSync('token')
    const current = () => !this._unloaded && version === this._version && wx.getStorageSync('token') === token
    this.setData({ loading: true })
    try {
      const res = await request({ url: '/miniapp/teacher/sign/list' })
      if (!current()) return
      const list = (res.data || []).map((item) => this.decorate(item))
      this.setData({ list, showEmpty: list.length === 0 })
    } catch (error) {
      if (!current()) return
      wx.showToast({ title: error.message || '加载失败', icon: 'none' })
      this.setData({ list: [], showEmpty: true })
    } finally {
      if (current()) this.setData({ loading: false })
    }
  },

  decorate(item) {
    const course = item.courseClassName || [item.gradeName, item.subjectName, item.classType].filter(Boolean).join('')
    const reviewed = item.reviewStatus === '1'
    const signed = item.lastSignDate != null && item.lastSignDate !== ''
    return {
      ...item,
      course,
      signed,
      lastDateText: signed ? String(item.lastSignDate).slice(0, 10) : '未签到',
      actualText: signed ? `${item.actualCount || 0}/${item.totalCount || 0}人` : '',
      reviewText: reviewed ? '已复核' : (signed ? '待复核' : ''),
      reviewClass: reviewed ? 'done' : (signed ? 'pending' : ''),
      room: [item.campusName, item.classroomName].filter(Boolean).join(' · ')
    }
  },

  openSign(e) {
    const { id } = e.currentTarget.dataset
    const item = this.data.list.find((row) => String(row.scheduleId) === String(id))
    if (!item) return
    wx.navigateTo({ url: `/pages/sign-in-edit/sign-in-edit?scheduleId=${encodeURIComponent(id)}&course=${encodeURIComponent(item.course)}&room=${encodeURIComponent(item.room)}&time=${encodeURIComponent(item.timeSlot || '')}` })
  }
})
