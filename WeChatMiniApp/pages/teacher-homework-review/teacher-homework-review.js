const { request } = require('../../utils/request')
Page({
  data: { id: null, list: [], stats: { total: 0, submitted: 0, pending: 0 }, loading: false },
  onLoad(q) { this.setData({ id: q.id }); this.load() },
  async load() {
    this.setData({ loading: true })
    try {
      const [submissionRes, statsRes] = await Promise.all([
        request({ url: `/miniapp/teacher/homework/${this.data.id}/submissions` }),
        request({ url: `/miniapp/teacher/homework/${this.data.id}/submission-stats` })
      ])
      const statsRows = statsRes.data || []
      const list = (submissionRes.data || []).map(item => ({ ...item, submissionId: item.submissionId || item.id, statusText: item.status === '2' || item.status === 2 ? '已批阅' : '待批阅' }))
      const stats = { total: statsRows.length, submitted: statsRows.filter(item => item.submitted === 1 || item.submitted === '1' || item.submissionId).length, pending: statsRows.filter(item => !(item.submitted === 1 || item.submitted === '1' || item.submissionId)).length }
      const missing = statsRows.filter(item => !(item.submitted === 1 || item.submitted === '1' || item.submissionId)).map(item => ({ ...item, missing: true, studentName: item.studentName || '学生' }))
      this.setData({ list: list.concat(missing), stats })
    } catch (e) { wx.showToast({ title: e.message || '加载失败', icon: 'none' }) } finally { this.setData({ loading: false }) }
  },
  open(e) { if (!e.currentTarget.dataset.id) return; wx.navigateTo({ url: `/pages/teacher-homework-review-detail/teacher-homework-review-detail?id=${e.currentTarget.dataset.id}&homeworkId=${this.data.id}` }) }
})