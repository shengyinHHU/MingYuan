const { request } = require('../../utils/request')

Page({
  data: {
    loading: false,
    keyword: '',
    filterStatus: '',
    list: [],
    shown: [],
    pageSize: 30,
    shownCount: 0,
    hasMore: true,
    total: 0,
    showEmpty: false,
    detail: null,
    statusOptions: [
      { label: '全部', value: '' },
      { label: '报名成功', value: '报名成功' },
      { label: '已取消', value: '已取消' }
    ]
  },

  onShow() { this.load() },
  onPullDownRefresh() { this.load().finally(() => wx.stopPullDownRefresh()) },
  onReachBottom() { this.showMore() },

  async load() {
    this.setData({ loading: true })
    try {
      const res = await request({ url: '/system/enrollment/list', data: { pageNum: 1, pageSize: 3000 } })
      const all = (res.rows || []).filter((r) => r.delFlag !== '2').map((r) => ({
        enrollmentId: r.enrollmentId,
        enrollmentCode: r.enrollmentCode || '—',
        studentName: r.studentName || '—',
        studentPhone: r.studentPhone || '—',
        contactPhone: r.contactPhone || '—',
        gradeName: r.gradeName || '—',
        subjectName: r.subjectName || '—',
        classType: r.classType || '—',
        teacherName: r.teacherName || '—',
        enrollmentStatus: r.enrollmentStatus || '—',
        payStatus: r.payStatus || '—',
        createTime: (r.createTime || '').slice(0, 10),
        remark: r.remark || '—'
      }))
      this._all = all
      this.setData({ loading: false })
      this.applyFilter(true)
    } catch (e) {
      this.setData({ loading: false, list: [], shown: [], showEmpty: true })
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  onKeyword(e) { this.setData({ keyword: e.detail.value }); this.applyFilter(true) },
  onStatus(e) { this.setData({ filterStatus: e.currentTarget.dataset.value }); this.applyFilter(true) },

  applyFilter(reset) {
    const kw = (this.data.keyword || '').trim()
    const st = this.data.filterStatus
    const list = (this._all || []).filter((s) => {
      if (st && s.enrollmentStatus !== st) return false
      if (!kw) return true
      return [s.studentName, s.studentPhone, s.contactPhone, s.gradeName, s.subjectName, s.teacherName, s.enrollmentCode]
        .some((v) => (v || '').indexOf(kw) >= 0)
    })
    const pageSize = this.data.pageSize
    const shownCount = reset ? Math.min(pageSize, list.length) : this.data.shownCount
    this.setData({
      list,
      total: list.length,
      shown: list.slice(0, shownCount),
      shownCount,
      hasMore: shownCount < list.length,
      showEmpty: list.length === 0
    })
  },

  showMore() {
    if (!this.data.hasMore) return
    this.setData({ shownCount: this.data.shownCount + this.data.pageSize })
    this.applyFilter(false)
  },

  openDetail(e) {
    const id = e.currentTarget.dataset.id
    const item = this.data.list.find((s) => s.enrollmentId === id)
    if (item) this.setData({ detail: item })
  },
  closeDetail() { this.setData({ detail: null }) },

  noop() {}
})
