import request from '@/utils/request'

// 查询薪资标准列表
export function listConfig(query) {
  return request({
    url: '/system/salaryConfig/list',
    method: 'get',
    params: query
  })
}

// 教师账号下拉（teacher角色）
export function listTeachers() {
  return request({
    url: '/system/salaryConfig/teachers',
    method: 'get'
  })
}

// 查询薪资标准详情
export function getConfig(configId) {
  return request({
    url: '/system/salaryConfig/' + configId,
    method: 'get'
  })
}

// 新增薪资标准
export function addConfig(data) {
  return request({
    url: '/system/salaryConfig',
    method: 'post',
    data
  })
}

// 修改薪资标准
export function updateConfig(data) {
  return request({
    url: '/system/salaryConfig',
    method: 'put',
    data
  })
}

// 删除薪资标准
export function delConfig(configId) {
  return request({
    url: '/system/salaryConfig/' + configId,
    method: 'delete'
  })
}
