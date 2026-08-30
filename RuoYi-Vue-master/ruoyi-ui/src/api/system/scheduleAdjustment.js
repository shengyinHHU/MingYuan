import request from '@/utils/request'

// 查询调课管理列表
export function listScheduleAdjustment(query) {
  return request({
    url: '/system/scheduleAdjustment/list',
    method: 'get',
    params: query
  })
}

// 查询调课管理详细
export function getScheduleAdjustment(adjustmentId) {
  return request({
    url: '/system/scheduleAdjustment/' + adjustmentId,
    method: 'get'
  })
}

// 新增调课管理
export function addScheduleAdjustment(data) {
  return request({
    url: '/system/scheduleAdjustment',
    method: 'post',
    data: data
  })
}

// 批量调课（按学期+期次将日期范围内的上课日统一调整/停课）
export function batchScheduleAdjustment(data) {
  return request({
    url: '/system/scheduleAdjustment/batch',
    method: 'post',
    data: data
  })
}

// 修改调课管理
export function updateScheduleAdjustment(data) {
  return request({
    url: '/system/scheduleAdjustment',
    method: 'put',
    data: data
  })
}

// 删除调课管理
export function delScheduleAdjustment(adjustmentId) {
  return request({
    url: '/system/scheduleAdjustment/' + adjustmentId,
    method: 'delete'
  })
}
