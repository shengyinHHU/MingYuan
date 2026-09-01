const { request } = require('../../utils/request')
const app = getApp()

// 各来源可选的签到状态：报名=到课/录播/请假，试听=试听到课/请假，调课=调课到课/请假
const STATUS_OPTIONS = {
  '1': [{ key: '1', label: '到课' }, { key: '2', label: '录播' }, { key: '3', label: '请假' }],
  '2': [{ key: '4', label: '试听到课' }, { key: '3', label: '请假' }],
  '3': [{ key: '5', label: '调课到课' }, { key: '3', label: '请假' }]
}
const STATUS_LABELS = { '1': '到课', '2': '录播', '3': '请假', '4': '试听到课', '5': '调课到课' }
const SOURCE_LABELS = { '1': '报名', '2': '试听', '3': '调课' }

function todayStr() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

Page({
  data: {
    scheduleId: null,
    course: '',
    room: '',
    timeText: '',
    classDate: todayStr(),
    maxDate: todayStr(),
    rows: [],
    locked: false,
    review: null,
    stats: null,
    loading: false,
    submitting: false
  },

  onLoad(query) {
    this.setData({
      scheduleId: Number(query.scheduleId),
      course: decodeURIComponent(query.course || ''),
      room: decodeURIComponent(query.room || ''),
      timeText: decodeURIComponent(query.time || '')
    })
    this.loadDetail()
  },

  onDateChange(e) {
    this.setData({ classDate: e.detail.value })
    this.loadDetail()
  },

  async loadDetail() {
    const { scheduleId, classDate } = this.data
    this.setData({ loading: true })
    try {
      const detail = await request({
        url: '/miniapp/teacher/sign/detail',
        data: { scheduleId, classDate }
      })
      const data = detail.data || {}
      const review = data.review || {}
      const details = data.details || []
      let rows
      if (details.length) {
        rows = details.map((d) => this.decorateRow({
          enrollmentId: d.enrollmentId,
          studentName: d.studentName,
          sourceType: d.sourceType,
          signStatus: d.signStatus,
          remark: d.remark || ''
        }, d.sourceType))
      } else {
        const res = await request({
          url: '/miniapp/teacher/sign/students',
          data: { scheduleId }
        })
        rows = (res.data || []).map((s) => this.decorateRow({
          enrollmentId: s.enrollment_id,
          studentName: s.student_name,
          sourceType: '1',
          signStatus: '1',
          remark: ''
        }, '1'))
      }
      this.setData({
        rows,
        review,
        locked: review.reviewStatus === '1',
        stats: this.buildStats(rows)
      })
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  decorateRow(row, sourceType) {
    return {
      ...row,
      sourceType,
      sourceText: SOURCE_LABELS[sourceType] || '报名',
      statusText: STATUS_LABELS[row.signStatus] || '',
      statusOptions: STATUS_OPTIONS[sourceType] || STATUS_OPTIONS['1'],
      added: sourceType !== '1'
    }
  },

  buildStats(rows) {
    const count = (key) => rows.filter((r) => r.signStatus === key).length
    return {
      attended: count('1'),
      recorded: count('2'),
      leave: count('3'),
      audited: count('4'),
      transferred: count('5'),
      actual: count('1') + count('4') + count('5'),
      total: rows.length
    }
  },

  setStatus(e) {
    const { index, key } = e.currentTarget.dataset
    if (this.data.locked) return
    const rows = this.data.rows
    rows[index].signStatus = key
    rows[index].statusText = STATUS_LABELS[key]
    this.setData({ rows, stats: this.buildStats(rows) })
  },

  addAudited() { this.addStudent('2') },

  addTransferred() { this.addStudent('3') },

  addStudent(sourceType) {
    if (this.data.locked) return
    wx.showModal({
      title: sourceType === '2' ? '添加试听学员' : '添加调课学员',
      editable: true,
      placeholderText: '请输入学生姓名',
      success: (res) => {
        if (!res.confirm) return
        const name = (res.content || '').trim()
        if (!name) {
          wx.showToast({ title: '姓名不能为空', icon: 'none' })
          return
        }
        if (this.data.rows.some((r) => r.studentName === name)) {
          wx.showToast({ title: '该学生已在名单中', icon: 'none' })
          return
        }
        const row = this.decorateRow({
          enrollmentId: null,
          studentName: name,
          sourceType,
          signStatus: sourceType === '2' ? '4' : '5',
          remark: ''
        }, sourceType)
        const rows = this.data.rows.concat([row])
        this.setData({ rows, stats: this.buildStats(rows) })
      }
    })
  },

  removeRow(e) {
    if (this.data.locked) return
    const { index } = e.currentTarget.dataset
    const row = this.data.rows[index]
    if (!row || !row.added) return
    wx.showModal({
      title: '移除学员',
      content: `确定移除「${row.studentName}」吗？`,
      success: (res) => {
        if (!res.confirm) return
        const rows = this.data.rows.filter((_, i) => i !== index)
        this.setData({ rows, stats: this.buildStats(rows) })
      }
    })
  },

  async submit() {
    const { scheduleId, classDate, rows, submitting, locked } = this.data
    if (submitting || locked) return
    if (!rows.length) {
      wx.showToast({ title: '暂无学员可签到', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      const res = await request({
        url: '/miniapp/teacher/sign/submit',
        method: 'POST',
        data: {
          scheduleId,
          classDate,
          details: rows.map((r) => ({
            enrollmentId: r.enrollmentId,
            studentName: r.studentName,
            sourceType: r.sourceType,
            signStatus: r.signStatus,
            remark: r.remark || ''
          }))
        }
      })
      wx.showToast({ title: `提交成功，实到 ${res.actualCount} 人`, icon: 'none' })
      setTimeout(() => wx.navigateBack(), 900)
    } catch (error) {
      wx.showToast({ title: error.message || '提交失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
