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
    teachers: [],
    teacherOptions: [{ label: '请选择教师' }],
    teachersLoading: false,
    teacherError: '',
    teacherIndex: 0,
    teacherLabel: '',
    devTeacherId: '',
    devRoles
  },

  onLoad(options = {}) {
    this._unloaded = false
    if (wx.getStorageSync('token') && options.selectTeacher !== '1') {
      wx.reLaunch({ url: '/pages/index/index' })
      return
    }
    const storedRole = options.selectTeacher === '1' ? 'teacher' : (wx.getStorageSync('devRole') || 'parent')
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
    if (devRole === 'teacher') this.loadTeachers()
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
    const devTeacherId = devRole === 'teacher' ? this.data.devTeacherId : undefined
    let previousToken = wx.getStorageSync('token')
    this.setData({ submitting: true })
    try {
      if (phoneCode === 'mock') {
        const cached = await devSession.restore(devRole, devTeacherId, () =>
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
