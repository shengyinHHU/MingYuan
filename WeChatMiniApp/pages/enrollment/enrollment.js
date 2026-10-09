const { request } = require('../../utils/request')

Page({
  data: {
    loading: false,
    submitting: false,
    schedules: [],
    teacherGroups: [], teacherOptions: [], selectedTeacherKeys: [], selectedTeachers: [],
    teacherKeyword: '', showTeacherPicker: false,
    enrollments: [],
    showScheduleEmpty: false,
    showEnrollmentEmpty: false,
    showSheet: false,
    subjects: [],
    grades: [],
    subjectOptions: ['全部科目'],
    gradeOptions: ['全部年级'],
    subjectIndex: 0,
    gradeIndex: 0,
    selectedSchedule: null,
    form: {
      studentName: '',
      contactPhone: ''
    }
  },

  onLoad() {
    this._token = wx.getStorageSync('token')
    this._unloaded = false
    this.loadDict().then(() => this.loadPage())
  },

  onUnload() { this._unloaded = true },

  current() { return !this._unloaded && this._token === wx.getStorageSync('token') },

  onPullDownRefresh() {
    Promise.all([this.loadDict(), this.loadPage()])
      .finally(() => wx.stopPullDownRefresh())
  },

  async loadDict() {
    try {
      const res = await request({ url: '/miniapp/material/dict' })
      const data = res.data || {}
      const subjects = data.subjects || []
      const grades = data.grades || []
      this.setData({
        subjects,
        grades,
        subjectOptions: ['全部科目'].concat(subjects.map((i) => i.dictLabel)),
        gradeOptions: ['全部年级'].concat(grades.map((i) => i.dictLabel))
      })
    } catch (error) {
      // 字典加载失败不阻断主流程
    }
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
    const version = this._scheduleVersion = (this._scheduleVersion || 0) + 1
    // 现有接口返回全部可报名课程，年级／学科／多位教师统一在同一数据集筛选。
    const res = await request({ url: '/miniapp/parent/schedules' })
    if (!this.current() || version !== this._scheduleVersion) return
    this._allSchedules = this.decorateSchedules(res.data || []).map(course => ({
      ...course,
      teacherKey: course.teacherId ? `teacher:${course.teacherId}` : `unbound:${course.scheduleId}`
    }))
    const teachers = new Map()
    this._allSchedules.forEach(course => {
      if (!teachers.has(course.teacherKey)) teachers.set(course.teacherKey, {
        key: course.teacherKey, name: course.teacherName || '教师待配置',
        label: course.teacherName || '教师待配置'
      })
    })
    this._teachers = Array.from(teachers.values()).sort((a,b) => a.name.localeCompare(b.name, 'zh-CN') || a.key.localeCompare(b.key))
    this.setData({ selectedTeacherKeys: this.data.selectedTeacherKeys.filter(key => teachers.has(key)) })
    this.applyFilters()
  },

  applyFilters() {
    const subject = this.data.subjectIndex > 0 && this.data.subjects[this.data.subjectIndex - 1]
    const grade = this.data.gradeIndex > 0 && this.data.grades[this.data.gradeIndex - 1]
    const keys = this.data.selectedTeacherKeys
    const schedules = (this._allSchedules || []).filter(course =>
      (!subject || course.subjectName === subject.dictLabel) &&
      (!grade || course.gradeName === grade.dictLabel) &&
      (!keys.length || keys.includes(course.teacherKey)))
    const groups = new Map()
    schedules.forEach(course => {
      if (!groups.has(course.teacherKey)) groups.set(course.teacherKey, { key: course.teacherKey, label: (this._teachers || []).find(t => t.key === course.teacherKey).label, courses: [] })
      groups.get(course.teacherKey).courses.push(course)
    })
    this.setData({ schedules, teacherGroups: Array.from(groups.values()).sort((a,b) => a.label.localeCompare(b.label, 'zh-CN')), showScheduleEmpty: schedules.length === 0 })
    this.updateTeacherOptions()
  },

  updateTeacherOptions() {
    const keyword = this.data.teacherKeyword.trim().toLowerCase(), keys = this.data.selectedTeacherKeys
    const teachers = this._teachers || []
    this.setData({
      teacherOptions: teachers.filter(t => !keyword || t.name.toLowerCase().includes(keyword)).map(t => ({ ...t, selected: keys.includes(t.key) })),
      selectedTeachers: teachers.filter(t => keys.includes(t.key))
    })
  },
  toggleTeacherPicker() { this.setData({ showTeacherPicker: !this.data.showTeacherPicker }) },
  searchTeacher(e) { this.setData({ teacherKeyword: e.detail.value }); this.updateTeacherOptions() },
  toggleTeacher(e) {
    const key = e.currentTarget.dataset.key
    if (!(this._teachers || []).some(t => t.key === key)) return
    const keys = this.data.selectedTeacherKeys
    this.setData({ selectedTeacherKeys: keys.includes(key) ? keys.filter(k => k !== key) : keys.concat(key) })
    this.applyFilters()
  },
  clearTeachers() { this.setData({ selectedTeacherKeys: [], teacherKeyword: '' }); this.applyFilters() },

  async loadEnrollments() {
    const res = await request({ url: '/miniapp/parent/enrollments' })
    if (!this.current()) return
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
        subjectText: this.labelOf(this.data.subjects, item.subjectName) || item.subjectName,
        gradeText: this.labelOf(this.data.grades, item.gradeName) || item.gradeName,
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
      subjectText: this.labelOf(this.data.subjects, item.subjectName) || item.subjectName,
      gradeText: this.labelOf(this.data.grades, item.gradeName) || item.gradeName,
      timeText: this.buildTimeText(item),
      placeText: this.buildPlaceText(item),
      statusText: item.enrollmentStatus === '2' ? '已取消' : '报名成功'
    }))
  },

  labelOf(list, value) {
    const hit = list.find((i) => i.dictValue === value)
    return hit ? hit.dictLabel : ''
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

  onSubjectChange(e) {
    this.setData({ subjectIndex: Number(e.detail.value) }); this.applyFilters()
  },
  onGradeChange(e) {
    this.setData({ gradeIndex: Number(e.detail.value) }); this.applyFilters()
  },
  resetFilters() {
    this.setData({ subjectIndex: 0, gradeIndex: 0, selectedTeacherKeys: [], teacherKeyword: '' }); this.applyFilters()
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
