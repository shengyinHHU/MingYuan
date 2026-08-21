const { request } = require('../../utils/request')

Page({
  data: {
    loading: false,
    subjects: [],
    grades: [],
    subjectOptions: ['全部学科'],
    gradeOptions: ['全部年级'],
    subjectIndex: 0,
    gradeIndex: 0,
    keyword: '',
    list: [],
    showEmpty: false
  },

  onLoad() {
    this.loadDict().then(() => this.loadList())
  },

  onPullDownRefresh() {
    Promise.all([this.loadDict(), this.loadList()])
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
        subjectOptions: ['全部学科'].concat(subjects.map((i) => i.dictLabel)),
        gradeOptions: ['全部年级'].concat(grades.map((i) => i.dictLabel))
      })
    } catch (error) {
      // 字典加载失败不阻断主流程
    }
  },

  async loadList() {
    this.setData({ loading: true })
    try {
      const params = {}
      if (this.data.keyword) params.title = this.data.keyword
      if (this.data.subjectIndex > 0) {
        params.subjectName = this.data.subjects[this.data.subjectIndex - 1].dictValue
      }
      if (this.data.gradeIndex > 0) {
        params.gradeName = this.data.grades[this.data.gradeIndex - 1].dictValue
      }
      const res = await request({ url: '/miniapp/parent/material/list', data: params })
      const list = this.decorate((res.data || []))
      this.setData({ list, showEmpty: list.length === 0 })
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' })
      this.setData({ list: [], showEmpty: true })
    } finally {
      this.setData({ loading: false })
    }
  },

  decorate(list) {
    return list.map((item) => ({
      ...item,
      priceText: Number(item.price) === 0 ? '免费' : `¥${Number(item.price).toFixed(2)}`,
      subjectText: this.labelOf(this.data.subjects, item.subjectName) || item.subjectName,
      gradeText: this.labelOf(this.data.grades, item.gradeName) || item.gradeName,
      bought: Number(item.bought) === 1,
      sizeText: this.formatSize(item.fileSize)
    }))
  },

  labelOf(list, value) {
    const hit = list.find((i) => i.dictValue === value)
    return hit ? hit.dictLabel : ''
  },

  formatSize(bytes) {
    if (!bytes) return ''
    const n = Number(bytes)
    if (n < 1024) return `${n}B`
    if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)}KB`
    return `${(n / 1024 / 1024).toFixed(1)}MB`
  },

  onKeyword(e) {
    this.setData({ keyword: e.detail.value })
  },

  onSubjectChange(e) {
    this.setData({ subjectIndex: Number(e.detail.value) })
    this.loadList()
  },

  onGradeChange(e) {
    this.setData({ gradeIndex: Number(e.detail.value) })
    this.loadList()
  },

  resetFilters() {
    this.setData({ keyword: '', subjectIndex: 0, gradeIndex: 0 })
    this.loadList()
  },

  openDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: `/pages/material-detail/material-detail?id=${id}` })
  }
})
