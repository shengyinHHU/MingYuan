const { request } = require('../../utils/request')

Page({
  data: {
    loading: false,
    timetable: [],
    groupedTimetable: [],
    timetableCount: 0,
    showEmpty: false
  },

  onLoad() {
    this.loadTimetable()
  },

  onPullDownRefresh() {
    this.loadTimetable().finally(() => wx.stopPullDownRefresh())
  },

  async loadTimetable() {
    this.setData({ loading: true })
    try {
      const res = await request({ url: '/miniapp/parent/timetable' })
      const timetable = this.decorateTimetable(res.data || [])
      this.setData({
        timetable,
        groupedTimetable: this.groupByPeriod(timetable),
        timetableCount: timetable.length,
        showEmpty: timetable.length === 0
      })
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  decorateTimetable(list) {
    return list.map((item) => ({
      ...item,
      timeText: this.buildTimeText(item),
      placeText: this.joinText([item.campusName, item.classroomName]),
      subjectTag: this.joinText([item.gradeName, item.subjectName]),
      payText: item.payStatus === '1' ? '已支付' : '未支付'
    }))
  },

  buildTimeText(item) {
    const start = item.startTime ? String(item.startTime).slice(0, 5) : ''
    const end = item.endTime ? String(item.endTime).slice(0, 5) : ''
    if (start && end) return `${start}-${end}`
    return item.timeSlot || ''
  },

  joinText(parts) {
    return parts.filter(Boolean).join(' · ')
  },

  groupByPeriod(list) {
    const groups = []
    list.forEach((item) => {
      const key = this.joinText([item.termName || '课程', item.periodName])
      let group = groups.find((entry) => entry.key === key)
      if (!group) {
        group = { key, items: [] }
        groups.push(group)
      }
      group.items.push(item)
    })
    return groups
  },

  goEnrollment() {
    wx.navigateTo({ url: '/pages/enrollment/enrollment' })
  }
})
