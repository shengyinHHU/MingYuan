const roleProfiles = {
  parent: {
    name: '家长/学生',
    tag: '学习成长中心',
    eyebrow: '家长端 · 学员端',
    title: '查看课程、课时、请假和成长记录',
    subtitle: '家长重点看到孩子相关信息：课程报名、到课情况、剩余课时、请假通知和老师填写的成长报告。',
    stats: [
      { label: '剩余课时', value: '18', suffix: '节' },
      { label: '待上课程', value: '3', suffix: '节' },
      { label: '成长记录', value: '5', suffix: '条' }
    ],
    actions: [
      { title: '课程报名', desc: '选课续费', icon: '报', tone: 'blue' },
      { title: '我的课表', desc: '上课安排', icon: '课', tone: 'orange' },
      { title: '考勤记录', desc: '到课明细', icon: '勤', tone: 'cyan' },
      { title: '请假申请', desc: '提交审核', icon: '假', tone: 'violet' },
      { title: '成长档案', desc: '老师评价', icon: '档', tone: 'green' },
      { title: '缴费课时', desc: '余额有效期', icon: '费', tone: 'gold' },
      { title: '资料商城', desc: '讲义试卷', icon: '材', tone: 'indigo' },
      { title: 'AI咨询', desc: '学习建议', icon: 'AI', tone: 'red' }
    ],
    agendaTitle: '我的课程',
    schedule: [
      { time: '16:30', name: '五年级数学提高班', room: '星辰教室 A', teacher: '李老师', status: '待上课' },
      { time: '18:40', name: '英语阅读专项课', room: '未来教室 B', teacher: '王老师', status: '已预约' }
    ],
    insightTitle: '成长档案',
    insight: [
      { label: '计算能力', value: 86 },
      { label: '逻辑表达', value: 78 },
      { label: '课堂专注', value: 92 }
    ],
    note: '成长报告由老师填写，家长端只展示孩子本人相关内容。'
  },
  teacher: {
    name: '教师',
    tag: '教师工作台',
    eyebrow: '教师端',
    title: '快速处理点名、请假和学员成长记录',
    subtitle: '教师重点看到授课班级、今日课表、待点名、待审核请假和需要填写的成长记录。',
    stats: [
      { label: '今日授课', value: '4', suffix: '节' },
      { label: '待点名', value: '2', suffix: '班' },
      { label: '待填报告', value: '7', suffix: '份' }
    ],
    actions: [
      { title: '我的课表', desc: '授课安排', icon: '课', tone: 'orange' },
      { title: '考勤点名', desc: '班级签到', icon: '勤', tone: 'cyan' },
      { title: '请假审核', desc: '处理申请', icon: '假', tone: 'violet' },
      { title: '学生档案', desc: '查看学情', icon: '档', tone: 'green' },
      { title: '成长报告', desc: '老师填写', icon: '评', tone: 'blue' },
      { title: '班级名单', desc: '学员管理', icon: '班', tone: 'indigo' },
      { title: '授课统计', desc: '课时绩效', icon: '统', tone: 'gold' },
      { title: 'AI建议', desc: '辅助反馈', icon: 'AI', tone: 'red' }
    ],
    agendaTitle: '今日授课',
    schedule: [
      { time: '15:20', name: '三年级数学思维班', room: '星辰教室 C', teacher: '22人', status: '待点名' },
      { time: '19:00', name: '初一数学提高班', room: '未来教室 A', teacher: '18人', status: '待上课' }
    ],
    insightTitle: '班级观察',
    insight: [
      { label: '平均出勤', value: 94 },
      { label: '课后反馈', value: 72 },
      { label: '续费意向', value: 68 }
    ],
    note: '教师端保留成长报告填写入口，但不显示财务收款和校区经营总览。'
  },
  academic: {
    name: '教务',
    tag: '教务调度台',
    eyebrow: '教务端',
    title: '统筹排课、班级、考勤与请假审核',
    subtitle: '教务重点处理班级创建、排课冲突、考勤异常、请假审核和每年7月1日学生年级自动升级。',
    stats: [
      { label: '待排课程', value: '9', suffix: '节' },
      { label: '请假待审', value: '6', suffix: '条' },
      { label: '冲突提醒', value: '2', suffix: '项' }
    ],
    actions: [
      { title: '班级管理', desc: '开班分班', icon: '班', tone: 'blue' },
      { title: '排课管理', desc: '冲突检测', icon: '排', tone: 'orange' },
      { title: '考勤管理', desc: '异常处理', icon: '勤', tone: 'cyan' },
      { title: '请假审核', desc: '流程处理', icon: '假', tone: 'violet' },
      { title: '学生档案', desc: '年级学情', icon: '档', tone: 'green' },
      { title: '教师档案', desc: '授课科目', icon: '师', tone: 'indigo' },
      { title: '年级升级', desc: '7月1日', icon: '升', tone: 'gold' },
      { title: '通知提醒', desc: '订阅消息', icon: '通', tone: 'red' }
    ],
    agendaTitle: '教务提醒',
    schedule: [
      { time: '09:20', name: '五年级数学提高班', room: '教师时间冲突', teacher: '李老师', status: '需处理' },
      { time: '14:00', name: '新初一暑期班', room: '待分配教室', teacher: '教务组', status: '待排课' }
    ],
    insightTitle: '调度健康度',
    insight: [
      { label: '排课完成', value: 82 },
      { label: '考勤闭环', value: 88 },
      { label: '请假处理', value: 76 }
    ],
    note: '教务端重在流程和异常，不暴露财务利润等管理层数据。'
  },
  finance: {
    name: '财务',
    tag: '财务收费台',
    eyebrow: '财务端',
    title: '管理收费、课时余额和教师薪资核算',
    subtitle: '财务重点看到缴费、续费、优惠、课时消耗、教师薪资类别和自动核算结果。',
    stats: [
      { label: '待收款', value: '12', suffix: '笔' },
      { label: '本月收入', value: '8.6', suffix: '万' },
      { label: '待核薪资', value: '5', suffix: '人' }
    ],
    actions: [
      { title: '收费管理', desc: '报名续费', icon: '收', tone: 'gold' },
      { title: '课时余额', desc: '有效期', icon: '余', tone: 'blue' },
      { title: '优惠补缴', desc: '费用调整', icon: '惠', tone: 'orange' },
      { title: '教师薪资', desc: '自动核算', icon: '薪', tone: 'red' },
      { title: '薪资类别', desc: '规则配置', icon: '类', tone: 'violet' },
      { title: '订单统计', desc: '收入分析', icon: '统', tone: 'indigo' },
      { title: '资料订单', desc: '商城收款', icon: '材', tone: 'green' },
      { title: '财务报表', desc: '月度汇总', icon: '表', tone: 'cyan' }
    ],
    agendaTitle: '财务待办',
    schedule: [
      { time: '10:00', name: '王同学续费订单', room: '24课时', teacher: '¥3600', status: '待确认' },
      { time: '17:30', name: '7月教师薪资核算', room: '课时工资', teacher: '5位教师', status: '待核算' }
    ],
    insightTitle: '收入结构',
    insight: [
      { label: '课程收入', value: 78 },
      { label: '资料收入', value: 12 },
      { label: '优惠占比', value: 10 }
    ],
    note: '财务端能看到收费与薪资，不展示教师填写成长报告的编辑入口。'
  },
  principal: {
    name: '校长/管理员',
    tag: '校区经营驾驶舱',
    eyebrow: '管理端',
    title: '总览招生、教学、财务和运营分析',
    subtitle: '管理层看到全校区经营数据、招生转化、教学质量、教师绩效、利润分析和AI运营建议。',
    stats: [
      { label: '在读学员', value: '386', suffix: '人' },
      { label: '本月收入', value: '28.4', suffix: '万' },
      { label: '续费率', value: '83', suffix: '%' }
    ],
    actions: [
      { title: '招生分析', desc: '渠道转化', icon: '招', tone: 'blue' },
      { title: '教学分析', desc: '出勤成绩', icon: '教', tone: 'green' },
      { title: '经营分析', desc: '收入利润', icon: '营', tone: 'gold' },
      { title: '教师绩效', desc: '课时续费', icon: '绩', tone: 'red' },
      { title: '班级管理', desc: '资源总览', icon: '班', tone: 'orange' },
      { title: '财务收费', desc: '收费利润', icon: '财', tone: 'cyan' },
      { title: '权限角色', desc: '若依RBAC', icon: '权', tone: 'violet' },
      { title: 'AI教务', desc: '智能分析', icon: 'AI', tone: 'indigo' }
    ],
    agendaTitle: '经营关注',
    schedule: [
      { time: '本周', name: '新生报名转化率下降', room: '渠道：地推码', teacher: '-8%', status: '需关注' },
      { time: '本月', name: '初中数学续费表现较好', room: '续费率', teacher: '91%', status: '优秀' }
    ],
    insightTitle: '运营分析',
    insight: [
      { label: '招生转化', value: 73 },
      { label: '教学完成', value: 89 },
      { label: '利润健康', value: 81 }
    ],
    note: '管理端通常对应若依后台的校长或管理员角色，可看全局数据但仍受数据权限限制。'
  }
}

const roleOptions = Object.keys(roleProfiles).map((key) => ({
  key,
  name: roleProfiles[key].name
}))

function buildRoleState(roleKey) {
  const profile = roleProfiles[roleKey] || roleProfiles.parent
  return {
    currentRole: roleKey,
    currentRoleName: profile.name,
    tag: profile.tag,
    eyebrow: profile.eyebrow,
    title: profile.title,
    subtitle: profile.subtitle,
    stats: profile.stats,
    quickActions: profile.actions,
    agendaTitle: profile.agendaTitle,
    schedule: profile.schedule,
    insightTitle: profile.insightTitle,
    insight: profile.insight,
    note: profile.note
  }
}

Page({
  data: {
    logoUrl: '/images/logo.png',
    roles: roleOptions,
    ...buildRoleState('parent')
  },

  switchRole(e) {
    const role = e.currentTarget.dataset.role
    this.setData(buildRoleState(role))
  },

  handleAction(e) {
    const title = e.currentTarget.dataset.title
    wx.showToast({
      title: `${title}暂未开放`,
      icon: 'none'
    })
  }
})
