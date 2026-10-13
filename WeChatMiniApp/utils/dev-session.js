const { request } = require('./request')
const app = getApp()
const CACHE_KEY = 'miniappDevSessions'

function readCache() {
  const cache = wx.getStorageSync(CACHE_KEY)
  return cache && cache.baseUrl === app.globalData.baseUrl && cache.sessions
    ? cache
    : { baseUrl: app.globalData.baseUrl, version: '', sessions: {} }
}

function clearActive() {
  const keys = ['token', 'roles', 'userInfo', 'devSessionVersion']
  keys.forEach((key) => wx.removeStorageSync(key))
  app.globalData.token = ''
  app.globalData.roles = []
  app.globalData.userInfo = null
}

async function checkBackendSession() {
  const result = await request({ url: '/miniapp/dev/session', timeout: 15000 })
  const version = result.data && result.data.version
  if (!version) throw new Error('无法确认开发会话，请重启后端后重试')
  const activeVersion = wx.getStorageSync('devSessionVersion')
  const cache = readCache()
  const restarted = !!activeVersion && activeVersion !== version
  if (restarted) clearActive()
  if (cache.version !== version || restarted) {
    wx.setStorageSync(CACHE_KEY, { baseUrl: app.globalData.baseUrl, version, sessions: {} })
  }
  return { version, restarted }
}

function keyFor(role, identityId, cache) {
  if (role === 'teacher') return identityId ? `teacher:${identityId}` : cache.lastTeacherKey
  if (role === 'parent') return identityId ? `parent:${identityId}` : cache.lastParentKey
  return role
}

function validIdentity(role, info, identityId) {
  const roles = info.roles || []
  const user = info.user
  if (!user || !roles.includes(role)) return false
  // 教师 / 家长：登录身份必须与所选账号一致，且不能是管理员账号
  if (role === 'teacher' || role === 'parent') {
    return !roles.includes('admin') && !roles.includes('administrator')
      && String(user.userId) === String(identityId)
  }
  return true
}

function activate(role, token, info, version) {
  const identityId = info.user && info.user.userId
  if (!validIdentity(role, info, identityId)) throw new Error('登录身份不匹配，请重新选择账号')
  if (version) {
    const previous = readCache()
    const cache = previous.version === version ? previous : { baseUrl: app.globalData.baseUrl, version, sessions: {} }
    const key = keyFor(role, identityId, cache)
    cache.sessions[key] = { token, userId: String(identityId) }
    if (role === 'teacher') cache.lastTeacherKey = key
    if (role === 'parent') cache.lastParentKey = key
    wx.setStorageSync(CACHE_KEY, cache)
    wx.setStorageSync('devSessionVersion', version)
  } else {
    wx.removeStorageSync('devSessionVersion')
    wx.removeStorageSync(CACHE_KEY)
  }
  wx.setStorageSync('token', token)
  wx.setStorageSync('roles', info.roles || [])
  wx.setStorageSync('userInfo', info.user || null)
  wx.setStorageSync('devRole', role)
  app.globalData.token = token
  app.globalData.roles = info.roles || []
  app.globalData.userInfo = info.user || null
}

async function restore(role, identityId, isCurrent = () => true) {
  const checked = await checkBackendSession()
  if (checked.restarted) return { restored: false, restarted: true }
  if (!isCurrent()) return { restored: false }
  const cache = readCache()
  const key = keyFor(role, identityId, cache)
  const saved = cache.sessions[key]
  if (!saved) return { restored: false }
  const previousToken = wx.getStorageSync('token')
  let info
  try {
    info = await request({ url: '/getInfo', header: { Authorization: `Bearer ${saved.token}` }, timeout: 15000 })
  } catch (error) {
    // A timeout is not evidence that an identity expired.
    if (error.code !== 401) throw error
    const latest = readCache()
    if (isCurrent() && latest.version === checked.version && latest.sessions[key] && latest.sessions[key].token === saved.token) {
      delete latest.sessions[key]
      wx.setStorageSync(CACHE_KEY, latest)
    }
    return { restored: false }
  }
  if (!isCurrent() || wx.getStorageSync('token') !== previousToken || readCache().version !== checked.version) return { restored: false }
  if (String(info.user && info.user.userId) !== saved.userId || !validIdentity(role, info, saved.userId)) {
    delete cache.sessions[key]
    wx.setStorageSync(CACHE_KEY, cache)
    return { restored: false }
  }
  activate(role, saved.token, info, checked.version)
  return { restored: true }
}

module.exports = { activate, restore, checkBackendSession }
