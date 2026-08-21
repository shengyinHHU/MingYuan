const { request } = require('../../utils/request')
const app = getApp()

const devRoles = [
  { key: 'parent', name: '家长', className: 'active' },
  { key: 'teacher', name: '教师', className: '' },
  { key: 'admin', name: '管理员', className: '' }
]

Page({
  data: {
    submitting: false,
    devRole: 'parent',
    devRoles
  },

  onLoad() {
    const storedRole = wx.getStorageSync('devRole') || 'parent'
    this.setDevRole(storedRole)

    if (wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/index/index' })
    }
  },

  chooseDevRole(e) {
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
    this.setData({ submitting: true })
    try {
      const loginCode = await this.getWxLoginCode()
      const loginRes = await request({
        url: '/miniapp/login',
        method: 'POST',
        data: {
          loginCode,
          phoneCode,
          devRole,
          nickName: `MiniApp ${devRole}`
        }
      })
      wx.setStorageSync('token', loginRes.token)
      app.globalData.token = loginRes.token

      const infoRes = await request({ url: '/getInfo' })
      const roles = infoRes.roles || []
      wx.setStorageSync('roles', roles)
      wx.setStorageSync('userInfo', infoRes.user || null)
      app.globalData.roles = roles
      app.globalData.userInfo = infoRes.user || null

      wx.reLaunch({ url: '/pages/index/index' })
    } catch (error) {
      wx.showToast({ title: error.message || 'Login failed', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
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
