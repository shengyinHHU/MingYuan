const app = getApp()

function request(options) {
  const token = wx.getStorageSync('token')
  const baseUrl = app.globalData.baseUrl

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${baseUrl}${options.url}`,
      method: options.method || 'GET',
      timeout: options.timeout || 60000,
      data: options.data || {},
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.header || {})
      },
      success(res) {
        const data = res.data || {}
        if (res.statusCode >= 200 && res.statusCode < 300 &&
            !(typeof data.code === 'number' && data.code >= 400)) {
          resolve(data)
          return
        }
        if (data.code === 401 || res.statusCode === 401) {
          wx.removeStorageSync('token')
          wx.removeStorageSync('roles')
          wx.removeStorageSync('userInfo')
          app.globalData.token = ''
          app.globalData.roles = []
          app.globalData.userInfo = null
          wx.showToast({ title: '登录已失效，请重新进入', icon: 'none' })
          setTimeout(() => {
            wx.reLaunch({ url: '/pages/register/register' })
          }, 600)
        }
        reject(new Error(data.msg || `Request failed: ${res.statusCode}`))
      },
      fail(error) {
        const message = error.errMsg || 'Network request failed'
        reject(new Error(message.includes('timeout') ? '请求超时，请检查后端是否已重启并正常运行' : message))
      }
    })
  })
}

module.exports = {
  request
}
