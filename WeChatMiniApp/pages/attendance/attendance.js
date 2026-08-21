const { request } = require('../../utils/request')

Page({
  data: {
    loading: false,
    courses: [],
    details: [],
    selectedCourse: null,
    totalAttended: 0,
    totalCount: 0,
    showEmpty: false,
    showSheet: false
  },

  onLoad() {
    this.loadSummary()
  },

  onPullDownRefresh() {
    this.loadSummary().finally(() => wx.stopPullDownRefresh())
  },

  async loadSummary() {
    this.setData({ loading: true })
    try {
      const res = await request({ url: '/miniapp/parent/attendance' })
      const courses = this.decorateCourses(res.data || [])
      const totals = courses.reduce((acc, item) => {
        acc.attended += Number(item.attendedCount || 0)
        acc.total += Number(item.totalCount || 0)
        return acc
      }, { attended: 0, total: 0 })

      this.setData({
        courses,
        totalAttended: totals.attended,
        totalCount: totals.total,
        showEmpty: courses.length === 0
      })
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  decorateCourses(list) {
    return list.map((item) => {
      const attended = Number(item.attendedCount || 0)
      const total = Number(item.totalCount || 0)
      return {
        ...item,
        attendedCount: attended,
        totalCount: total,
        progressText: `${attended}/${total}`,
        percent: total ? Math.round((attended / total) * 100) : 0,
        subtitle: this.joinText([item.gradeName, item.subjectName, item.teacherName])
      }
    })
  },

  async openDetails(e) {
    const courseClassName = e.currentTarget.dataset.name
    const selectedCourse = this.data.courses.find((item) => item.courseClassName === courseClassName)
    if (!selectedCourse) return

    this.setData({
      selectedCourse,
      details: [],
      showSheet: true
    })

    try {
      const res = await request({
        url: '/miniapp/parent/attendance/details',
        data: { courseClassName }
      })
      this.setData({ details: this.decorateDetails(res.data || []) })
    } catch (error) {
      wx.showToast({ title: error.message || '加载明细失败', icon: 'none' })
    }
  },

  decorateDetails(list) {
    return list.map((item, index) => {
      const done = item.attendanceStatus === '1'
      return {
        ...item,
        indexNo: index + 1,
        timeText: this.buildTimeText(item),
        placeText: this.joinText([item.campusName, item.classroomName]),
        statusText: this.statusText(item.attendanceStatus),
        statusClass: done ? 'done' : ''
      }
    })
  },

  buildTimeText(item) {
    const start = item.startTime ? String(item.startTime).slice(0, 5) : ''
    const end = item.endTime ? String(item.endTime).slice(0, 5) : ''
    const clock = start && end ? `${start}-${end}` : (item.timeSlot || '')
    return this.joinText([item.termName, item.periodName, clock])
  },

  joinText(parts) {
    return parts.filter(Boolean).join(' · ')
  },

  statusText(status) {
    if (status === '1') return '已上课'
    if (status === '2') return '已取消'
    return '未上课'
  },

  closeDetails() {
    this.setData({
      selectedCourse: null,
      details: [],
      showSheet: false
    })
  }
})
