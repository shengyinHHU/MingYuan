const app = getApp()

function request(options) {
  const token = wx.getStorageSync('token')
  const baseUrl = app.globalData.baseUrl
  const header = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.header || {})
  }

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${baseUrl}${options.url}`,
      method: options.method || 'GET',
      timeout: options.timeout || 60000,
      data: options.data || {},
      header,
      success(res) {
        const data = res.data || {}
        if (res.statusCode >= 200 && res.statusCode < 300 && data.code === 200) {
          resolve(data)
          return
        }
        const usesCurrentSession = wx.getStorageSync('token') === token &&
          (token ? header.Authorization === `Bearer ${token}` : !header.Authorization)
        if ((data.code === 401 || res.statusCode === 401) && usesCurrentSession) {
          wx.removeStorageSync('token')
          wx.removeStorageSync('roles')
          wx.removeStorageSync('userInfo')
          app.globalData.token = ''
          app.globalData.roles = []
          app.globalData.userInfo = null
          wx.showToast({ title: '登录已失效，请重新进入', icon: 'none' })
          setTimeout(() => {
            if (!wx.getStorageSync('token')) {
              wx.reLaunch({ url: '/pages/register/register' })
            }
          }, 600)
        }
        const error = new Error(data.msg || `Request failed: ${res.statusCode}`)
        error.code = data.code || res.statusCode
        error.applicationRejected = res.statusCode >= 200 && res.statusCode < 500 && typeof data === 'object' && !Array.isArray(data) && Number.isInteger(data.code) && data.code > 0 && data.code !== 200 && typeof data.msg === 'string' && data.msg.length > 0
        error.uncertain = !error.applicationRejected
        reject(error)
      },
      fail(error) {
        const message = error.errMsg || '网络连接失败，请重试'
        const failure = new Error(message.includes('timeout') ? '请求超时，请检查后端是否已重启并正常运行' : message)
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
