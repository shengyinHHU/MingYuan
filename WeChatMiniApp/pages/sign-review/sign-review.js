const { request } = require('../../utils/request')

Page({
  data: {
    loading: false,
    list: [],
    showEmpty: false,
    filterDate: ''
  },

  onShow() {
    this.loadList()
  },

  onPullDownRefresh() {
    this.loadList().finally(() => wx.stopPullDownRefresh())
  },

  onDateFilter(e) {
    this.setData({ filterDate: e.detail.value })
    this.loadList()
  },

  resetFilter() {
    this.setData({ filterDate: '' })
    this.loadList()
  },

  async loadList() {
    this.setData({ loading: true })
    try {
      const res = await request({
        url: '/miniapp/admin/sign/list',
        data: this.data.filterDate ? { classDate: this.data.filterDate } : {}
      })
      const list = (res.data || []).map((item) => this.decorate(item))
      this.setData({ list, showEmpty: list.length === 0 })
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' })
      this.setData({ list: [], showEmpty: true })
    } finally {
      this.setData({ loading: false })
    }
  },

  decorate(item) {
    const course = item.courseClassName || [item.gradeName, item.subjectName, item.classType].filter(Boolean).join('')
    const done = item.reviewStatus === '1'
    const confirmed = item.confirmedCount || 0
    const reviewText = done ? '复核完成' : (confirmed > 0 ? `已确认 ${confirmed}/2` : '待复核')
    return {
      ...item,
      course,
      dateText: item.classDate ? String(item.classDate).slice(0, 10) : '',
      reviewText,
      reviewClass: done ? 'done' : (confirmed > 0 ? 'half' : 'pending'),
      room: [item.campusName, item.classroomName].filter(Boolean).join(' · ')
    }
  },

  openDetail(e) {
    const { scheduleId, date, course, room } = e.currentTarget.dataset
    wx.navigateTo({
      url: `/pages/sign-review-detail/sign-review-detail?scheduleId=${scheduleId}&classDate=${date}` +
        `&course=${encodeURIComponent(course || '')}&room=${encodeURIComponent(room || '')}`
    })
  }
})
