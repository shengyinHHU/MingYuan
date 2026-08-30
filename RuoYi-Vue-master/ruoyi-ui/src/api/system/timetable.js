import request from '@/utils/request'

// 可视化课表：维度初始化（学期/期次/时段/班型/年级/学科/教室分组/教师下拉 + 当前角色）
export function getTimetableDimensions() {
  return request({
    url: '/system/courseTimetable/dimensions',
    method: 'get'
  })
}

// 可视化课表：网格查询（按 classroomId@timeSlot 映射）
export function getTimetableGrid(params) {
  return request({
    url: '/system/courseTimetable/grid',
    method: 'get',
    params
  })
}

// 单条排课详情（用于编辑弹窗回填）
export function getTimetableSchedule(scheduleId) {
  return request({
    url: '/system/courseTimetable/' + scheduleId,
    method: 'get'
  })
}

// 新增/编辑/删除：直接走 courseTimetable 自身的 Controller（带角色校验）
export function addTimetableSchedule(data) {
  return request({
    url: '/system/courseTimetable',
    method: 'post',
    data
  })
}

export function updateTimetableSchedule(data) {
  return request({
    url: '/system/courseTimetable',
    method: 'put',
    data
  })
}

export function delTimetableSchedule(scheduleIds) {
  return request({
    url: '/system/courseTimetable/' + scheduleIds,
    method: 'delete'
  })
}
