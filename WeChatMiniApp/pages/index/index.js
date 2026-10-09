const { request } = require('../../utils/request')
const devSession = require('../../utils/dev-session')

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
      { title: '我的资料', icon: '料', tone: 'green', route: '/pages/material-orders/material-orders' },
      { title: '我的作业', icon: '作', tone: 'violet', route: '/pages/homework/homework' }
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
      { label: '负责班级', value: '—', suffix: '班' },
      { label: '待批提交', value: '—', suffix: '份' },
      { label: '我的作业', value: '—', suffix: '项' }
    ],
    actions: [
      { title: '学生出勤', icon: '签', tone: 'blue', route: '/pages/sign-in/sign-in' },
      { title: '我的课表', icon: '课', tone: 'orange', route: '/pages/timetable/timetable' },
      { title: '请假审核', icon: '假', tone: 'violet' },
      { title: '班级学员', icon: '班', tone: 'green', route: '/pages/teacher-students/teacher-students' },
      { title: 'WiFi打卡', icon: '卡', tone: 'red' },
      { title: '资料商城', icon: '资', tone: 'indigo', route: '/pages/material/material' },
      { title: '我的资料', icon: '料', tone: 'green', route: '/pages/material-upload/material-upload' },
      { title: '作业管理', icon: '作', tone: 'violet', route: '/pages/teacher-homework/teacher-homework' }
    ],
    agendaTitle: '我的班级',
    schedule: []
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
      { title: '签到复核', icon: '核', tone: 'blue', route: '/pages/sign-review/sign-review' },
      { title: '课程报名', icon: '招', tone: 'blue', route: '/pages/admin-enrollment/admin-enrollment' },
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

// 日期解析（兼容 SQL DATE/时间戳/字符串多种格式）
function parseDate(val) {
  if (!val) return null
  if (val instanceof Date) return val
  if (typeof val === 'number') {
    const d = new Date(val)
    if (!isNaN(d.getTime())) return d
  }
  const s = String(val)
  if (s.indexOf('T') >= 0 || s.indexOf(' ') >= 0) {
    const d = new Date(s)
    if (!isNaN(d.getTime())) return d
  }
  const m = s.match(/^(\d{4})[-\/.](\d{1,2})[-\/.](\d{1,2})/)
  if (m) {
    const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))
    if (!isNaN(d.getTime())) return d
  }
  return null
}
function fmtDate(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}
function weekdayShort(d) {
  const names = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return names[d.getDay()]
}

// 按排课信息计算下一次上课日期（与 timetable 页面算法一致）
function computeNextDate(item) {
  const start = parseDate(item.startDate)
  const end = parseDate(item.endDate)
  if (!start || !end) return null
  if (start.getTime() > end.getTime()) return null

  let pattern = item.classPattern
  if (!pattern) {
    pattern = (item.periodName === '周六' || item.periodName === '周日') ? 'WEEKLY' : 'DAILY_5_1'
  }
  let dates = []
  if (pattern === 'WEEKLY') {
    const targetDay = item.periodName === '周日' ? 0 : 6
    let cur = new Date(start)
    while (cur <= end && cur.getDay() !== targetDay) {
      cur.setDate(cur.getDate() + 1)
    }
    while (cur <= end) {
      dates.push(new Date(cur.getFullYear(), cur.getMonth(), cur.getDate()))
      cur.setDate(cur.getDate() + 7)
    }
  } else if (pattern === 'DAILY_5_1') {
    let cur = new Date(start.getFullYear(), start.getMonth(), start.getDate())
    let cnt = 0
    const stop = new Date(end.getFullYear(), end.getMonth(), end.getDate())
    while (cur <= stop) {
      if (cnt % 6 < 5) dates.push(new Date(cur.getFullYear(), cur.getMonth(), cur.getDate()))
      cur.setDate(cur.getDate() + 1)
      cnt++
    }
  }

  // 应用调课表
  const adj = item.adjustments || []
  const adjByOrig = {}
  adj.forEach((a) => {
    if (a.originalDate) {
      const d = parseDate(a.originalDate)
      if (d) adjByOrig[fmtDate(d)] = a
    }
  })
  let result = []
  dates.forEach((d) => {
    const key = fmtDate(d)
    const a = adjByOrig[key]
    if (a) {
      if (a.adjustedDate) {
        const ad = parseDate(a.adjustedDate)
        if (ad) result.push(ad)
      }
    } else {
      result.push(d)
    }
  })

  const seen = {}
  result = result.filter((d) => {
    const k = fmtDate(d)
    if (seen[k]) return false
    seen[k] = true
    return true
  }).sort((a, b) => a - b)

  if (!result.length) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const future = result.find((d) => d >= today)
  if (future) return { date: fmtDate(future).slice(5), weekday: weekdayShort(future) } // 只显示 MM-DD
  return { date: '已结课', weekday: '' }
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
    teacherDataMessage: '',
    roles: roleOptions,
    ...buildRoleState('parent')
  },

  onLoad() {
    this._unloaded = false
    this.applyLoginRole()
  },

  onShow() {
    this.applyLoginRole()
  },

  onUnload() {
    this._unloaded = true
    this._teacherLoadVersion = (this._teacherLoadVersion || 0) + 1
    this._roleLoadVersion = (this._roleLoadVersion || 0) + 1
  },

  async applyLoginRole() {
    const loadVersion = this._roleLoadVersion = (this._roleLoadVersion || 0) + 1
    const token = wx.getStorageSync('token')
    if (!token) {
      wx.reLaunch({ url: '/pages/register/register' })
      return
    }

    if (DEV_ROLE_SWITCH_ENABLED && wx.getStorageSync('devSessionVersion')) {
      try {
        const checked = await devSession.checkBackendSession()
        if (this._unloaded || loadVersion !== this._roleLoadVersion) return
        if (checked.restarted || wx.getStorageSync('token') !== token) {
          if (!wx.getStorageSync('token')) wx.reLaunch({ url: '/pages/register/register' })
          return
        }
      } catch (error) {
        if (!this._unloaded && loadVersion === this._roleLoadVersion) {
          wx.showToast({ title: error.message || '后端暂时无法连接', icon: 'none' })
        }
        return
      }
    }
    const role = getRoleFromStorage()
    this._teacherLoadVersion = (this._teacherLoadVersion || 0) + 1
    this.setData({ teacherDataMessage: '' })
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
      this.loadParentSchedule()
    } else if (role === 'teacher') {
      const user = wx.getStorageSync('userInfo') || {}
      this.setData({ tag: user.nickName || user.userName || '教师工作台' })
      this.loadTeacherData()
    }
  },

  async loadTeacherData() {
    const token = wx.getStorageSync('token')
    const version = this._teacherLoadVersion
    const results = await Promise.allSettled([
      request({ url: '/miniapp/teacher/sign/list', timeout: 15000 }),
      request({ url: '/miniapp/teacher/homework/list', timeout: 15000 })
    ])
    if (version !== this._teacherLoadVersion || this.data.currentRole !== 'teacher' || wx.getStorageSync('token') !== token) return
    const classes = results[0].status === 'fulfilled' ? (results[0].value.data || []) : null
    const homework = results[1].status === 'fulfilled' ? (results[1].value.data || []) : null
    const pending = homework && homework.reduce((sum, item) => sum + Math.max(0, Number(item.submissionCount || 0) - Number(item.reviewedCount || 0)), 0)
    this.setData({
      stats: [
        { label: '负责班级', value: classes ? String(classes.length) : '—', suffix: '班' },
        { label: '待批提交', value: homework ? String(pending) : '—', suffix: '份' },
        { label: '我的作业', value: homework ? String(homework.length) : '—', suffix: '项' }
      ],
      schedule: (classes || []).slice(0, 3).map((item) => ({
        time: item.timeSlot || '—',
        name: item.courseClassName || '未命名班级',
        room: item.classroomName || '',
        teacher: item.teacherName || ''
      })),
      teacherDataMessage: classes === null ? '班级加载失败，请重新进入或查看学生出勤' : (classes.length ? '' : '当前教师尚未绑定班级'),
    })
  },

  async loadParentSchedule() {
    const token = wx.getStorageSync('token')
    try {
      const res = await request({ url: '/miniapp/parent/timetable' })
      if (this._unloaded || this.data.currentRole !== 'parent' || wx.getStorageSync('token') !== token) return
      const list = res.data || []
      // 取前 3 条，取最近 3 次课，按下次上课日期排序
      const decorated = list.map((item) => {
        const next = computeNextDate(item)
        const start = item.startTime ? String(item.startTime).slice(0, 5) : ''
        const end = item.endTime ? String(item.endTime).slice(0, 5) : ''
        return {
          time: (start && end) ? `${start}-${end}` : (item.timeSlot || '—'),
          date: next ? next.date : '',
          weekday: next ? next.weekday : '',
          dateReady: !!next,
          name: item.courseClassName || '未命名课程',
          room: item.classroomName || '',
          teacher: item.teacherName || '',
          status: (item.payStatus === '未支付') ? '待支付' : '待上课'
        }
      })
      decorated.sort((a, b) => {
        if (!a.date) return 1
        if (!b.date) return -1
        if (a.date === '已结课') return 1
        if (b.date === '已结课') return -1
        return a.date.localeCompare(b.date)
      })
      const schedule = decorated.slice(0, 3)
      this.setData({ schedule: schedule.length ? schedule : roleProfiles.parent.schedule })
    } catch (error) {
      // 接口失败时保留原假数据占位
    }
  },

  async loadParentTag() {
    const token = wx.getStorageSync('token')
    try {
      const res = await request({ url: '/miniapp/parent/enrollments' })
      if (this._unloaded || this.data.currentRole !== 'parent' || wx.getStorageSync('token') !== token) return
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
      if (!this._unloaded && this.data.currentRole === 'parent' && wx.getStorageSync('token') === token) {
        this.setData({ tag: '学生家长' })
      }
    }
  },

  async switchRole(e) {
    if (this.data.roleLocked || this.data.roleSwitching) return

    const role = normalizeRole(e.currentTarget.dataset.role)
    if (role === this.data.currentRole) return
    if (!DEV_ROLE_SWITCH_ENABLED) {
      this.setData(buildRoleState(role, roleOptions))
      return
    }

    await this.loginAsDevRole(role)
  },

  async loginAsDevRole(role) {
    const previousToken = wx.getStorageSync('token')
    this.setData({ roleSwitching: true })
    try {
      const cached = await devSession.restore(role, undefined, () =>
        !this._unloaded && wx.getStorageSync('token') === previousToken)
      if (this._unloaded) return
      if (cached.restarted) {
        wx.reLaunch({ url: '/pages/register/register' })
        return
      }
      if (cached.restored) {
        await this.applyLoginRole()
        return
      }
      if (wx.getStorageSync('token') !== previousToken) return
      if (role === 'teacher') {
        this.switchTeacher()
        return
      }
      const loginCode = await this.getWxLoginCode()
      if (this._unloaded || wx.getStorageSync('token') !== previousToken) return
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
      if (this._unloaded || wx.getStorageSync('token') !== previousToken) return

      const infoRes = await request({ url: '/getInfo', header: { Authorization: `Bearer ${loginRes.token}` } })
      if (this._unloaded || wx.getStorageSync('token') !== previousToken) return
      devSession.activate(role, loginRes.token, infoRes, loginRes.devSessionVersion)

      this.applyLoginRole()
      wx.showToast({ title: `${roleProfiles[role].name}已登录`, icon: 'none' })
    } catch (error) {
      if (!this._unloaded) wx.showToast({ title: error.message || '切换失败', icon: 'none' })
    } finally {
      if (!this._unloaded) this.setData({ roleSwitching: false })
    }
  },

  switchTeacher() {
    if (!this._unloaded) wx.navigateTo({ url: '/pages/register/register?selectTeacher=1' })
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
