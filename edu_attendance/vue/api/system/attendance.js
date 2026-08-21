import request from '@/utils/request'

// 查询课程上课记录列表
export function listAttendance(query) {
  return request({
    url: '/system/attendance/list',
    method: 'get',
    params: query
  })
}

// 查询课程上课记录详细
export function getAttendance(attendanceId) {
  return request({
    url: '/system/attendance/' + attendanceId,
    method: 'get'
  })
}

// 新增课程上课记录
export function addAttendance(data) {
  return request({
    url: '/system/attendance',
    method: 'post',
    data: data
  })
}

// 修改课程上课记录
export function updateAttendance(data) {
  return request({
    url: '/system/attendance',
    method: 'put',
    data: data
  })
}

// 删除课程上课记录
export function delAttendance(attendanceId) {
  return request({
    url: '/system/attendance/' + attendanceId,
    method: 'delete'
  })
}
