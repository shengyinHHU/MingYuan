import request from '@/utils/request'

// 查询签到复核列表（按课次聚合，可选日期）
export function listReview(classDate) {
  return request({
    url: '/miniapp/admin/sign/list',
    method: 'get',
    params: classDate ? { classDate } : {}
  })
}

// 查询某课次签到明细与复核信息
export function getDetail(scheduleId, classDate) {
  return request({
    url: '/miniapp/admin/sign/detail',
    method: 'get',
    params: { scheduleId, classDate }
  })
}

// 确认复核（一节课需2位管理员）
export function confirmReview(scheduleId, classDate, images) {
  return request({
    url: '/miniapp/admin/sign/review',
    method: 'post',
    data: { scheduleId, classDate, images }
  })
}
