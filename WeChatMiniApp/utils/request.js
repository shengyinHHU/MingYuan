const app = getApp()

function request(options) {
  const token = wx.getStorageSync('token')
  const baseUrl = app.globalData.baseUrl

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${baseUrl}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.header || {})
      },
      success(res) {
        const data = res.data || {}
        if (res.statusCode >= 200 && res.statusCode < 300 && data.code === 200) {
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
        const error = new Error(data.msg || `Request failed: ${res.statusCode}`)
        error.code = data.code || res.statusCode
        error.applicationRejected = res.statusCode >= 200 && res.statusCode < 500 && typeof data === 'object' && !Array.isArray(data) && Number.isInteger(data.code) && data.code > 0 && data.code !== 200 && typeof data.msg === 'string' && data.msg.length > 0
        error.uncertain = !error.applicationRejected
        reject(error)
      },
      fail(error) {
        const failure = new Error(error.errMsg || '网络连接失败，请重试')
        failure.network = true
        failure.uncertain = true
        reject(failure)
      }
    })
  })
}

module.exports = {
  request
}
