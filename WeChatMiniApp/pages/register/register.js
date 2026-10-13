const { request } = require('../../utils/request')
const devSession = require('../../utils/dev-session')

const devRoles = [
  { key: 'parent', name: '家长', className: 'active' },
  { key: 'teacher', name: '教师', className: '' },
  { key: 'admin', name: '管理员', className: '' }
]

Page({
  data: {
    submitting: false,
    devRole: 'parent',
    selectMode: '',
    teachers: [],
    teacherOptions: [{ label: '请选择教师' }],
    teachersLoading: false,
    teacherError: '',
    teacherIndex: 0,
    teacherLabel: '',
    devTeacherId: '',
    parents: [],
    parentOptions: [{ label: '请选择家长' }],
    parentsLoading: false,
    parentError: '',
    parentIndex: 0,
    parentLabel: '',
    devParentId: '',
    devRoles
  },

  onLoad(options = {}) {
    this._unloaded = false
    // 从首页「切换家长 / 切换教师」进入时携带选择标记，此时即使已有 token 也停留在本页
    this._selectMode = options.selectTeacher === '1' ? 'teacher'
      : (options.selectParent === '1' ? 'parent' : '')
    if (wx.getStorageSync('token') && !this._selectMode) {
      wx.reLaunch({ url: '/pages/index/index' })
      return
    }
    this.setData({ selectMode: this._selectMode })
    const storedRole = this._selectMode || wx.getStorageSync('devRole') || 'parent'
    this.setDevRole(storedRole)
  },

  onUnload() {
    this._unloaded = true
  },

  chooseDevRole(e) {
    if (this.data.submitting) return
    this.setDevRole(e.currentTarget.dataset.role)
  },

  setDevRole(role) {
    const devRole = ['parent', 'teacher', 'admin'].includes(role) ? role : 'parent'
    this.setData({
      devRole,
      devRoles: devRoles.map((item) => ({
        ...item,
        className: item.key === devRole ? 'active' : ''
      }))
    })
    wx.setStorageSync('devRole', devRole)
    // 与教师端一致：选中「家长」即加载家长列表，登录页也能直接切换家长
    if (devRole === 'teacher') this.loadTeachers()
    if (devRole === 'parent') this.loadParents()
  },

  async loadTeachers() {
    if (this.data.teachersLoading || this.data.submitting) return
    this.setData({ teachersLoading: true, teacherError: '' })
    try {
      const result = await request({ url: '/miniapp/dev/teachers', timeout: 15000 })
      if (this._unloaded) return
      const teachers = (result.data || []).map((teacher) => ({
        ...teacher,
        label: `${teacher.name}（${teacher.userName}）`
      }))
      const selected = teachers.find((teacher) => String(teacher.userId) === this.data.devTeacherId)
      const teacherIndex = selected ? teachers.indexOf(selected) + 1 : 0
      this.setData({
        teachers,
        teacherOptions: [{ label: '请选择教师' }, ...teachers],
        teacherIndex,
        teacherLabel: selected ? selected.label : '',
        devTeacherId: selected ? String(selected.userId) : '',
        teacherError: teachers.length ? '' : '暂无可用教师，请在若依中配置独立教师账号及教师角色'
      })
    } catch (error) {
      if (this._unloaded) return
      this.setData({ teachers: [], teacherOptions: [{ label: '请选择教师' }], teacherIndex: 0, teacherLabel: '', devTeacherId: '', teacherError: error.message || '教师列表加载失败' })
    } finally {
      if (!this._unloaded) this.setData({ teachersLoading: false })
    }
  },

  async loadParents() {
    if (this.data.parentsLoading || this.data.submitting) return
    this.setData({ parentsLoading: true, parentError: '' })
    try {
      const result = await request({ url: '/miniapp/dev/parents', timeout: 20000 })
      if (this._unloaded) return
      const parents = (result.data || []).map((parent) => ({
        ...parent,
        label: `${parent.name}（${parent.phone || parent.userName}）`
      }))
      const selected = parents.find((parent) => String(parent.userId) === this.data.devParentId)
      const parentIndex = selected ? parents.indexOf(selected) + 1 : 0
      this.setData({
        parents,
        parentOptions: [{ label: '请选择家长' }, ...parents],
        parentIndex,
        parentLabel: selected ? selected.label : '',
        devParentId: selected ? String(selected.userId) : '',
        parentError: parents.length ? '' : '暂无可用家长账号'
      })
    } catch (error) {
      if (this._unloaded) return
      this.setData({ parents: [], parentOptions: [{ label: '请选择家长' }], parentIndex: 0, parentLabel: '', devParentId: '', parentError: error.message || '家长列表加载失败' })
    } finally {
      if (!this._unloaded) this.setData({ parentsLoading: false })
    }
  },

  chooseTeacher(e) {
    if (this.data.submitting || this.data.teachersLoading) return
    const teacherIndex = Number(e.detail.value)
    if (teacherIndex === 0) {
      this.setData({ teacherIndex: 0, teacherLabel: '', devTeacherId: '' })
      return
    }
    const teacher = this.data.teachers[teacherIndex - 1]
    if (!teacher) return
    this.setData({ teacherIndex, teacherLabel: teacher.label, devTeacherId: String(teacher.userId) })
  },

  chooseParent(e) {
    if (this.data.submitting || this.data.parentsLoading) return
    const parentIndex = Number(e.detail.value)
    if (parentIndex === 0) {
      this.setData({ parentIndex: 0, parentLabel: '', devParentId: '' })
      return
    }
    const parent = this.data.parents[parentIndex - 1]
    if (!parent) return
    this.setData({ parentIndex, parentLabel: parent.label, devParentId: String(parent.userId) })
  },

  handlePhoneLogin(e) {
    if (this.data.submitting) return
    const phoneCode = e.detail && e.detail.code
    this.loginWithWechat(phoneCode || 'mock', this.data.devRole)
  },

  handleDevLogin() {
    if (this.data.submitting) return
    this.loginWithWechat('mock', this.data.devRole)
  },

  async loginWithWechat(phoneCode, devRole) {
    if (this._unloaded || this.data.submitting) return
    if (devRole === 'teacher' && (this.data.teachersLoading || !this.data.devTeacherId)) {
      wx.showToast({ title: '请先选择教师', icon: 'none' })
      return
    }
    if (devRole === 'parent' && this._selectMode === 'parent' && (this.data.parentsLoading || !this.data.devParentId)) {
      wx.showToast({ title: '请先选择家长', icon: 'none' })
      return
    }
    const devTeacherId = devRole === 'teacher' ? this.data.devTeacherId : undefined
    const devParentId = devRole === 'parent' ? this.data.devParentId : undefined
    let previousToken = wx.getStorageSync('token')
    this.setData({ submitting: true })
    try {
      if (phoneCode === 'mock') {
        const cached = await devSession.restore(devRole, devTeacherId || devParentId, () =>
          !this._unloaded && wx.getStorageSync('token') === previousToken)
        if (this._unloaded) return
        if (cached.restored) {
          wx.reLaunch({ url: '/pages/index/index' })
          return
        }
        if (!cached.restarted && wx.getStorageSync('token') !== previousToken) return
        previousToken = wx.getStorageSync('token')
      }
      const loginCode = await this.getWxLoginCode()
      if (this._unloaded || wx.getStorageSync('token') !== previousToken) return
      const loginRes = await request({
        url: '/miniapp/login',
        method: 'POST',
        data: {
          loginCode,
          phoneCode,
          devRole,
          ...(devTeacherId ? { devTeacherId } : {}),
          ...(devParentId ? { devParentId } : {}),
          nickName: `MiniApp ${devRole}`
        }
      })
      if (this._unloaded || wx.getStorageSync('token') !== previousToken) return
      // Resolve the new identity before replacing the current session.
      const infoRes = await request({ url: '/getInfo', header: { Authorization: `Bearer ${loginRes.token}` } })
      if (this._unloaded || wx.getStorageSync('token') !== previousToken) return
      if (devRole === 'teacher' && String(infoRes.user && infoRes.user.userId) !== devTeacherId) {
        throw new Error('教师身份不匹配，请重新选择')
      }
      if (devRole === 'parent' && devParentId && String(infoRes.user && infoRes.user.userId) !== devParentId) {
        throw new Error('家长身份不匹配，请重新选择')
      }
      devSession.activate(devRole, loginRes.token, infoRes, loginRes.devSessionVersion)

      wx.reLaunch({ url: '/pages/index/index' })
    } catch (error) {
      if (!this._unloaded) wx.showToast({ title: error.message || 'Login failed', icon: 'none' })
    } finally {
      if (!this._unloaded) this.setData({ submitting: false })
    }
  },

  getWxLoginCode() {
    return new Promise((resolve) => {
      wx.login({
        success(res) {
          resolve(res.code || 'mock')
        },
        fail() {
          resolve('mock')
        }
      })
    })
  }
})
