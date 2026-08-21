const { request } = require('../../utils/request')

Page({
  data: {
    loading: false,
    submitting: false,
    schedules: [],
    enrollments: [],
    showScheduleEmpty: false,
    showEnrollmentEmpty: false,
    showSheet: false,
    filters: {
      gradeName: '',
      subjectName: ''
    },
    selectedSchedule: null,
    form: {
      studentName: '',
      contactPhone: ''
    }
  },

  onLoad() {
    this.loadPage()
  },

  onPullDownRefresh() {
    this.loadPage().finally(() => wx.stopPullDownRefresh())
  },

  async loadPage() {
    this.setData({ loading: true })
    try {
      await Promise.all([this.loadSchedules(), this.loadEnrollments()])
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async loadSchedules() {
    const res = await request({
      url: '/miniapp/parent/schedules',
      data: this.data.filters
    })
    const schedules = this.decorateSchedules(res.data || [])
    this.setData({
      schedules,
      showScheduleEmpty: schedules.length === 0
    })
  },

  async loadEnrollments() {
    const res = await request({ url: '/miniapp/parent/enrollments' })
    const enrollments = this.decorateEnrollments(res.data || [])
    this.setData({
      enrollments,
      showEnrollmentEmpty: enrollments.length === 0
    })
  },

  decorateSchedules(list) {
    return list.map((item) => {
      const enrolled = Number(item.enrolled) === 1
      const canEnroll = !enrolled && Number(item.remainingCount) > 0
      return {
        ...item,
        timeText: this.buildTimeText(item),
        placeText: this.buildPlaceText(item),
        countText: `${item.enrolledCount || 0}/${item.capacity || '不限'}`,
        canEnroll,
        statusClass: canEnroll ? 'ok' : 'disabled',
        statusText: enrolled ? '已报名' : (canEnroll ? '报名' : '已满')
      }
    })
  },

  decorateEnrollments(list) {
    return list.map((item) => ({
      ...item,
      timeText: this.buildTimeText(item),
      placeText: this.buildPlaceText(item),
      statusText: item.enrollmentStatus === '2' ? '已取消' : '报名成功'
    }))
  },

  buildPlaceText(item) {
    return `${item.campusName || ''}${item.classroomName ? ' · ' + item.classroomName : ''}`
  },

  buildTimeText(item) {
    const start = item.startTime ? String(item.startTime).slice(0, 5) : ''
    const end = item.endTime ? String(item.endTime).slice(0, 5) : ''
    const clock = start && end ? `${start}-${end}` : (item.timeSlot || '')
    return `${item.termName || ''} ${item.periodName || ''} ${clock}`.trim()
  },

  handleFilterInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`filters.${field}`]: e.detail.value })
  },

  applyFilters() {
    this.loadSchedules().catch((error) => {
      wx.showToast({ title: error.message || '筛选失败', icon: 'none' })
    })
  },

  resetFilters() {
    this.setData({
      filters: {
        gradeName: '',
        subjectName: ''
      }
    })
    this.applyFilters()
  },

  openEnroll(e) {
    const schedule = this.data.schedules.find((item) => String(item.scheduleId) === String(e.currentTarget.dataset.id))
    if (!schedule) return
    if (!schedule.canEnroll) {
      wx.showToast({ title: schedule.statusText, icon: 'none' })
      return
    }
    const userInfo = wx.getStorageSync('userInfo') || {}
    this.setData({
      selectedSchedule: schedule,
      showSheet: true,
      form: {
        studentName: '',
        contactPhone: userInfo.phonenumber || ''
      }
    })
  },

  closeEnroll() {
    if (this.data.submitting) return
    this.setData({
      selectedSchedule: null,
      showSheet: false
    })
  },

  handleFormInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: e.detail.value })
  },

  async submitEnroll() {
    const { selectedSchedule, form } = this.data
    if (!form.studentName.trim()) {
      wx.showToast({ title: '请输入学生姓名', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      await request({
        url: '/miniapp/parent/enroll',
        method: 'POST',
        data: {
          scheduleId: selectedSchedule.scheduleId,
          studentName: form.studentName.trim(),
          contactPhone: form.contactPhone.trim()
        }
      })
      wx.showToast({ title: '报名成功', icon: 'success' })
      this.setData({
        selectedSchedule: null,
        showSheet: false
      })
      await this.loadPage()
    } catch (error) {
      wx.showToast({ title: error.message || '报名失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
