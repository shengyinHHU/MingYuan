const { request } = require('../../utils/request')

function num(v) { const n = Number(v); return Number.isFinite(n) ? n : 0 }

Page({
  data: {
    loading: false,
    keyword: '',
    list: [],
    showEmpty: false,
    // 详情弹层
    detail: null,
    detailLoading: false,
    // 薪资标准编辑弹层
    editVisible: false,
    editForm: null
  },

  onShow() { this.load() },
  onPullDownRefresh() { this.load().finally(() => wx.stopPullDownRefresh()) },

  onKeyword(e) {
    this.setData({ keyword: e.detail.value })
    this.applyFilter()
  },

  applyFilter() {
    const kw = (this.data.keyword || '').trim()
    const list = kw ? this._all.filter((t) => (t.teacherName || '').indexOf(kw) >= 0) : this._all
    this.setData({ list, showEmpty: list.length === 0 })
  },

  async load() {
    this.setData({ loading: true })
    try {
      const [rosterRes, cfgRes, scheduleRes] = await Promise.all([
        request({ url: '/system/salaryConfig/teachers' }),
        request({ url: '/system/salaryConfig/list' }),
        request({ url: '/system/schedule/list', data: { pageNum: 1, pageSize: 2000 } })
      ])
      const roster = rosterRes.data || []
      const configs = cfgRes.data || []
      const cfgByTeacher = {}
      configs.forEach((c) => { cfgByTeacher[c.teacherId] = c })
      const rows = scheduleRes.rows || []
      const statByTeacher = {}
      rows.forEach((s) => {
        const name = s.teacherName
        if (!name) return
        const st = statByTeacher[name] || (statByTeacher[name] = { count: 0, subjects: {}, modes: {} })
        st.count += 1
        if (s.subjectName) st.subjects[s.subjectName] = 1
        if (s.classMode) st.modes[s.classMode] = 1
      })
      const all = roster.map((t) => {
        const cfg = cfgByTeacher[t.teacherId] || null
        const st = statByTeacher[t.teacherName] || { count: 0, subjects: {}, modes: {} }
        const subjects = Object.keys(st.subjects)
        return {
          teacherId: t.teacherId,
          teacherName: t.teacherName,
          configId: cfg ? cfg.configId : null,
          baseSalary: cfg ? num(cfg.baseSalary) : null,
          hasConfig: !!cfg,
          lessonCount: st.count,
          subjectText: subjects.length ? subjects.join('、') : '—'
        }
      }).sort((a, b) => b.lessonCount - a.lessonCount)
      this._all = all
      this.setData({ loading: false })
      this.applyFilter()
    } catch (e) {
      this.setData({ loading: false, list: [], showEmpty: true })
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  async openDetail(e) {
    const id = e.currentTarget.dataset.id
    const base = this._all.find((t) => t.teacherId === id)
    if (!base) return
    this.setData({ detail: { ...base }, detailLoading: true })
    try {
      const res = await request({ url: `/system/user/${id}` })
      const u = res.data || {}
      const roles = (u.roles || []).map((r) => r.roleName).join('、')
      this.setData({ detail: { ...this.data.detail, phonenumber: u.phonenumber || '—', userName: u.userName || '—', status: u.status === '0' ? '正常' : '停用', roles: roles || '—' } })
    } catch (e) {
      // 详情补充失败不阻断
    } finally {
      this.setData({ detailLoading: false })
    }
  },
  closeDetail() { this.setData({ detail: null }) },

  async openEdit(e) {
    const id = e.currentTarget.dataset.id
    const base = this._all.find((t) => t.teacherId === id)
    if (!base) return
    let cfg = null
    if (base.configId) {
      try { const r = await request({ url: `/system/salaryConfig/${base.configId}` }); cfg = r.data } catch (e) {}
    }
    this.setData({
      editVisible: true,
      editForm: {
        configId: base.configId,
        teacherId: base.teacherId,
        teacherName: base.teacherName,
        baseSalary: cfg ? cfg.baseSalary : '',
        ratePrimaryClass: cfg ? cfg.ratePrimaryClass : '',
        rateJhClass: cfg ? cfg.rateJhClass : '',
        ratePrimary1on1: cfg ? cfg.ratePrimary1on1 : '',
        rateJunior1on1: cfg ? cfg.rateJunior1on1 : '',
        rateSenior1on1: cfg ? cfg.rateSenior1on1 : '',
        classHoursPerSession: cfg ? cfg.classHoursPerSession : '',
        remark: cfg ? (cfg.remark || '') : ''
      }
    })
  },
  closeEdit() { this.setData({ editVisible: false, editForm: null }) },
  onEditInput(e) { this.setData({ [`editForm.${e.currentTarget.dataset.field}`]: e.detail.value }) },

  async saveEdit() {
    const f = this.data.editForm
    if (!f) return
    const payload = {
      teacherId: f.teacherId,
      teacherName: f.teacherName,
      status: '0'
    }
    const map = { baseSalary: 'baseSalary', ratePrimaryClass: 'ratePrimaryClass', rateJhClass: 'rateJhClass', ratePrimary1on1: 'ratePrimary1on1', rateJunior1on1: 'rateJunior1on1', rateSenior1on1: 'rateSenior1on1', classHoursPerSession: 'classHoursPerSession' }
    let hasVal = false
    Object.keys(map).forEach((k) => { if (f[k] !== '' && f[k] != null) { payload[map[k]] = num(f[k]); hasVal = true } })
    if (f.remark) payload.remark = f.remark
    if (!hasVal) { wx.showToast({ title: '请至少填写一项标准', icon: 'none' }); return }
    try {
      wx.showLoading({ title: '保存中' })
      if (f.configId) { payload.configId = f.configId; await request({ url: '/system/salaryConfig', method: 'PUT', data: payload }) }
      else { await request({ url: '/system/salaryConfig', method: 'POST', data: payload }) }
      wx.hideLoading()
      wx.showToast({ title: '已保存', icon: 'success' })
      this.setData({ editVisible: false, editForm: null })
      this.load()
    } catch (e) {
      wx.hideLoading()
      wx.showToast({ title: e.message || '保存失败', icon: 'none' })
    }
  },

  noop() {}
})
