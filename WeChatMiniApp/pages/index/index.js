const { request } = require('../../utils/request')
const app = getApp()

// Development only: role tabs request a matching backend token.
// Set this to false before production release.
const DEV_ROLE_SWITCH_ENABLED = true

const roleProfiles = {
  parent: {
    name: '家长',
    tag: '学生家长',
    eyebrow: '家长端',
    title: '课程报名、课表与上课记录',
    stats: [
      { label: '剩余课时', value: '18', suffix: '节' },
      { label: '待上课程', value: '3', suffix: '节' },
      { label: '已上课程', value: '0', suffix: '节' }
    ],
    actions: [
      { title: '课程报名', icon: '报', tone: 'blue', route: '/pages/enrollment/enrollment' },
      { title: '我的课表', icon: '课', tone: 'orange', route: '/pages/timetable/timetable' },
      { title: '考勤记录', icon: '勤', tone: 'cyan', route: '/pages/attendance/attendance' },
      { title: '请假申请', icon: '假', tone: 'violet' },
      { title: '资料商城', icon: '资', tone: 'indigo', route: '/pages/material/material' },
      { title: '我的资料', icon: '料', tone: 'green', route: '/pages/material-orders/material-orders' }
    ],
    agendaTitle: '我的课程',
    schedule: [
      { time: '16:30', name: '五年级数学提高班', room: '万达24-5', teacher: '徐老师', status: '待上课' },
      { time: '18:40', name: '英语阅读专项课', room: '百家湖A', teacher: '张老师', status: '已预约' }
    ]
  },
  teacher: {
    name: '教师',
    tag: '教师工作台',
    eyebrow: '教师端',
    title: '上课、点名与班级管理',
    stats: [
      { label: '今日排课', value: '4', suffix: '节' },
      { label: '待点名', value: '2', suffix: '班' },
      { label: '待处理', value: '7', suffix: '项' }
    ],
    actions: [
      { title: '我的课表', icon: '课', tone: 'orange' },
      { title: '考勤点名', icon: '勤', tone: 'cyan' },
      { title: '请假审核', icon: '假', tone: 'violet' },
      { title: '学生档案', icon: '档', tone: 'green' },
      { title: '成长报告', icon: '评', tone: 'blue' },
      { title: '班级名单', icon: '班', tone: 'indigo' },
      { title: '排课统计', icon: '统', tone: 'gold' },
      { title: 'WiFi打卡', icon: '卡', tone: 'red' },
      { title: '资料商城', icon: '资', tone: 'indigo', route: '/pages/material/material' },
      { title: '我的资料', icon: '料', tone: 'green', route: '/pages/material-upload/material-upload' }
    ],
    agendaTitle: '今日排课',
    schedule: [
      { time: '15:20', name: '三年级数学思维班', room: '百家湖3', teacher: '22人', status: '待点名' },
      { time: '19:00', name: '初一数学提高班', room: '万达21-4', teacher: '18人', status: '待上课' }
    ]
  },
  admin: {
    name: '管理员',
    tag: '校区运营管理',
    eyebrow: '管理员端',
    title: '招生、排课、教室与运营管理',
    stats: [
      { label: '在读学员', value: '386', suffix: '人' },
      { label: '本月收入', value: '28.4', suffix: '万' },
      { label: '续费率', value: '83', suffix: '%' }
    ],
    actions: [
      { title: '课程报名', icon: '招', tone: 'blue' },
      { title: '排课管理', icon: '排', tone: 'orange' },
      { title: '教室管理', icon: '室', tone: 'cyan' },
      { title: '学生档案', icon: '档', tone: 'green' },
      { title: '教师管理', icon: '师', tone: 'indigo' },
      { title: '收费财务', icon: '财', tone: 'gold' },
      { title: '薪资核算', icon: '薪', tone: 'red' },
      { title: '运营分析', icon: '析', tone: 'violet' },
      { title: '资料商城', icon: '资', tone: 'indigo', route: '/pages/material/material' },
      { title: '资料管理', icon: '料', tone: 'green', route: '/pages/material-upload/material-upload' }
    ],
    agendaTitle: '管理待办',
    schedule: [
      { time: '09:20', name: '五年级数学提高班', room: '教师时间冲突', teacher: '徐老师', status: '需处理' },
      { time: '14:00', name: '新初一暑期班', room: '待分配教室', teacher: '教务组', status: '待排课' }
    ]
  }
}

const roleOptions = Object.keys(roleProfiles).map((key) => ({
  key,
  name: roleProfiles[key].name,
  className: ''
}))

function normalizeRole(roleKey) {
  return roleProfiles[roleKey] ? roleKey : 'parent'
}

function buildRoleState(roleKey, visibleRoles) {
  const normalizedRole = normalizeRole(roleKey)
  const profile = roleProfiles[normalizedRole]
  const roles = visibleRoles || roleOptions
  return {
    currentRole: normalizedRole,
    currentRoleName: profile.name,
    tag: profile.tag,
    eyebrow: profile.eyebrow,
    title: profile.title,
    stats: profile.stats,
    quickActions: profile.actions,
    agendaTitle: profile.agendaTitle,
    schedule: profile.schedule,
    roles: roles.map((role) => ({
      ...role,
      className: role.key === normalizedRole ? 'active' : ''
    }))
  }
}

function getRoleFromStorage() {
  const roles = wx.getStorageSync('roles') || []
  if (roles.includes('admin')) return 'admin'
  if (roles.includes('teacher')) return 'teacher'
  return 'parent'
}

Page({
  data: {
    logoUrl: '/images/logo.png',
    roleLocked: false,
    roleSwitching: false,
    roles: roleOptions,
    ...buildRoleState('parent')
  },

  onLoad() {
    this.applyLoginRole()
  },

  onShow() {
    this.applyLoginRole()
  },

  applyLoginRole() {
    const token = wx.getStorageSync('token')
    if (!token) {
      wx.reLaunch({ url: '/pages/register/register' })
      return
    }

    const role = getRoleFromStorage()
    if (DEV_ROLE_SWITCH_ENABLED) {
      this.setData({
        roleLocked: false,
        ...buildRoleState(role, roleOptions)
      })
    } else {
      this.setData({
        roleLocked: true,
        ...buildRoleState(role, [{ key: role, name: roleProfiles[role].name, className: 'active' }])
      })
    }

    if (role === 'parent') {
      this.loadParentTag()
    }
  },

  async loadParentTag() {
    try {
      const res = await request({ url: '/miniapp/parent/enrollments' })
      const enrollments = res.data || []
      const names = []
      enrollments.forEach((item) => {
        if (item.studentName && !names.includes(item.studentName)) {
          names.push(item.studentName)
        }
      })

      if (names.length === 1) {
        this.setData({ tag: `${names[0]}家长` })
      } else if (names.length > 1) {
        this.setData({ tag: `${names[0]}等${names.length}名学生家长` })
      } else {
        this.setData({ tag: '学生家长' })
      }
    } catch (error) {
      this.setData({ tag: '学生家长' })
    }
  },

  async switchRole(e) {
    if (this.data.roleLocked || this.data.roleSwitching) return

    const role = normalizeRole(e.currentTarget.dataset.role)
    if (!DEV_ROLE_SWITCH_ENABLED) {
      this.setData(buildRoleState(role, roleOptions))
      return
    }

    await this.loginAsDevRole(role)
  },

  async loginAsDevRole(role) {
    this.setData({ roleSwitching: true })
    try {
      const loginCode = await this.getWxLoginCode()
      const loginRes = await request({
        url: '/miniapp/login',
        method: 'POST',
        data: {
          loginCode,
          phoneCode: 'mock',
          devRole: role,
          nickName: `MiniApp ${role}`
        }
      })

      wx.setStorageSync('token', loginRes.token)
      wx.setStorageSync('devRole', role)
      app.globalData.token = loginRes.token

      const infoRes = await request({ url: '/getInfo' })
      const roles = infoRes.roles || []
      wx.setStorageSync('roles', roles)
      wx.setStorageSync('userInfo', infoRes.user || null)
      app.globalData.roles = roles
      app.globalData.userInfo = infoRes.user || null

      this.applyLoginRole()
      wx.showToast({ title: `${roleProfiles[role].name}已登录`, icon: 'none' })
    } catch (error) {
      wx.showToast({ title: error.message || '切换失败', icon: 'none' })
    } finally {
      this.setData({ roleSwitching: false })
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
  },

  handleAction(e) {
    const route = e.currentTarget.dataset.route
    const title = e.currentTarget.dataset.title
    if (route) {
      wx.navigateTo({ url: route })
      return
    }

    wx.showToast({
      title: `${title}暂未开放`,
      icon: 'none'
    })
  }
})
