const shop = require('../../utils/material-shop')
Page({
  data: { loading: false, error: '', subjects: [], grades: [], subjectOptions: ['全部学科'], gradeOptions: ['全部年级'], subjectIndex: 0, gradeIndex: 0, sortOptions: ['综合排序', '价格升序', '价格降序', '销量优先'], sortIndex: 0, keyword: '', list: [], total: 0, pageNum: 1 },
  onLoad() { this._sequence = 0; this._showSequence = 0; this.setData({ role: '' }) },
  async onShow() {
    this._hidden = false
    const sequence = ++this._showSequence
    this.setData({ loading: true, error: '', list: [], role: '' }); this._role = ''
    try {
      const info = await shop.request({ url: '/getInfo' })
      if (sequence !== this._showSequence || this._hidden) return
      const roles = info.roles || []
      this._role = roles.includes('admin') ? 'admin' : roles.includes('parent') ? 'parent' : roles.includes('teacher') ? 'teacher' : ''
      this.setData({ role: this._role })
      if (this._role === 'teacher') { this.setData({ loading: false }); return }
      if (!this._role) throw new Error('当前账号不能访问资料商城')
      await this.loadDict()
      if (sequence === this._showSequence && !this._hidden) return this.loadList()
    } catch (error) { if (sequence === this._showSequence && !this._hidden) this.setData({ error: error.message, loading: false }) }
  },
  onHide() { this._hidden = true; this._sequence++; this._showSequence++ },
  onUnload() { this.onHide() },
  onPullDownRefresh() { return this.search().finally(() => wx.stopPullDownRefresh()) },
  onReachBottom() { if (!this.data.loading && this.data.list.length < this.data.total) this.loadList(true) },
  async loadDict() {
    try {
      const response = await shop.request({ url: '/miniapp/material/dict' })
      if (this._hidden) return
      const { subjects = [], grades = [] } = response.data || {}
      this.setData({ subjects, grades, subjectOptions: ['全部学科'].concat(subjects.map(item => item.dictLabel)), gradeOptions: ['全部年级'].concat(grades.map(item => item.dictLabel)) })
    } catch (error) { /* Catalogue remains readable without dictionaries. */ }
  },
  async loadList(append = false) {
    if (!['parent', 'admin'].includes(this._role)) return
    if (append && this.data.loading) return
    const sequence = ++this._sequence
    const pageNum = append ? this.data.pageNum + 1 : 1
    this.setData({ loading: true, error: '', ...(append ? {} : { list: [], total: 0 }) })
    try {
      const response = await shop.request({ url: this._role === 'admin' ? '/system/material/list' : '/miniapp/parent/material/list', data: { pageNum, pageSize: 20, title: this.data.keyword.trim(), subjectCode: this.data.subjectIndex ? this.data.subjects[this.data.subjectIndex - 1].dictValue : '', gradeCode: this.data.gradeIndex ? this.data.grades[this.data.gradeIndex - 1].dictValue : '', sort: ['default', 'priceAsc', 'priceDesc', 'sales'][this.data.sortIndex] } })
      if (sequence !== this._sequence || this._hidden) return
      const result = (this._role === 'admin' ? response : response.data) || {}
      this.setData({ list: (append ? this.data.list : []).concat((result.rows || []).map(shop.product)), total: Number(result.total || 0), pageNum })
    } catch (error) { if (sequence === this._sequence && !this._hidden) this.setData({ error: error.message }) }
    finally { if (sequence === this._sequence && !this._hidden) this.setData({ loading: false }) }
  },
  search() { return this._role ? this.loadList() : this.onShow() },
  onKeyword(e) { this.setData({ keyword: e.detail.value }) },
  onSubjectChange(e) { this.setData({ subjectIndex: Number(e.detail.value) }); this.loadList() },
  onGradeChange(e) { this.setData({ gradeIndex: Number(e.detail.value) }); this.loadList() },
  onSortChange(e) { this.setData({ sortIndex: Number(e.detail.value) }); this.loadList() },
  resetFilters() { this.setData({ keyword: '', subjectIndex: 0, gradeIndex: 0, sortIndex: 0 }); this.loadList() },
  openDetail(e) { wx.navigateTo({ url: `/pages/material-detail/material-detail?id=${e.currentTarget.dataset.id}` }) },
  openOrders() { if (this._role === 'parent') wx.navigateTo({ url: '/pages/material-orders/material-orders' }) },
  openManagement() { wx.navigateTo({ url: '/pages/material-upload/material-upload' }) }
})
