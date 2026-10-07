App({
  globalData: {
    // WeChat DevTools can call the local backend directly.
    // Use an HTTPS domain here when testing on a real device or publishing.
    baseUrl: 'http://192.168.2.6:8080',
    token: '',
    userInfo: null,
    roles: []
  },

  onLaunch() {
    this.globalData.token = wx.getStorageSync('token') || ''
    this.globalData.userInfo = wx.getStorageSync('userInfo') || null
    this.globalData.roles = wx.getStorageSync('roles') || []
  }
})
