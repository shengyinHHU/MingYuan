const s = require('../../utils/sign-review')
Page({
  data: { loading: false, list: [], showEmpty: false, error: '', filterDate: '', filterIndex: 0, filters: ['待确认', '已确认', '全部课次'], pendingCount: 0, confirmedCount: 0 },
  onShow() { this._active = true; this._visit = (this._visit || 0) + 1; this._token = wx.getStorageSync('token'); return this.loadList() },
  onHide() { this._active = false; this._visit++ },
  onUnload() { this.onHide() },
  onPullDownRefresh() { return this.loadList().finally(() => wx.stopPullDownRefresh()) },
  current(visit) { return this._active && visit === this._visit && this._token === wx.getStorageSync('token') },
  onDateFilter(e) { this.setData({ filterDate: e.detail.value }); return this.loadList() },
  resetFilter() { this.setData({ filterDate: '' }); return this.loadList() },
  changeFilter(e) { this.setData({ filterIndex: Number(e.currentTarget.dataset.index) }); this.applyFilter() },
  applyFilter() {
    const all = this._rows || [], list = all.filter(r => this.data.filterIndex === 2 || (this.data.filterIndex === 1 ? r.reviewStatus === '1' : r.reviewStatus !== '1'))
    this.setData({ list, showEmpty: !list.length, pendingCount: all.filter(r => r.reviewStatus !== '1').length, confirmedCount: all.filter(r => r.reviewStatus === '1').length })
  },
  async loadList() {
    const visit = this._visit, sequence = this._sequence = (this._sequence || 0) + 1
    const current = () => this.current(visit) && sequence === this._sequence
    this._rows = []; this.setData({ loading: true, error: '', list: [], pendingCount: 0, confirmedCount: 0 })
    try {
      if (!await s.authorize(current)) return
      const res = await s.request({ url: s.base + '/list', data: this.data.filterDate ? { classDate: this.data.filterDate } : {} })
      if (!current()) return
      this._rows = (res.data || []).map(item => this.decorate(item)); this.applyFilter()
    } catch (error) { if (current()) this.setData({ error: error.message, list: [], showEmpty: false }) }
    finally { if (current()) this.setData({ loading: false }) }
  },
  decorate(item) {
    const dateText = String(item.classDate || '').slice(0, 10), done = item.reviewStatus === '1'
    return { ...item, scheduleId: String(item.scheduleId), rowKey: String(item.scheduleId) + ':' + dateText, dateText,
      course: item.courseClassName || [item.gradeName, item.subjectName, item.classType].filter(Boolean).join(''),
      reviewText: done ? '教务已确认' : (item.reviewStatus === '2' ? '审核异常 · 待更正' : '待教务确认'), reviewClass: done ? 'done' : 'pending',
      room: [item.campusName, item.classroomName].filter(Boolean).join(' · ') }
  },
  openDetail(e) {
    const id = String(e.currentTarget.dataset.scheduleId), date = e.currentTarget.dataset.date
    if (!this.current(this._visit) || !this.data.list.some(r => r.scheduleId === id && r.dateText === date)) return
    wx.navigateTo({ url: '/pages/sign-review-detail/sign-review-detail?scheduleId=' + encodeURIComponent(id) + '&classDate=' + encodeURIComponent(date) })
  }
})
