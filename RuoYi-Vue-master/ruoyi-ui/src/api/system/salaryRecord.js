import request from '@/utils/request'

// 结算单列表（已入库快照）
export function listRecord(query) {
  return request({
    url: '/system/salaryRecord/list',
    method: 'get',
    params: query
  })
}

// 按月预览（实时统计）
export function previewMonth(salaryMonth) {
  return request({
    url: '/system/salaryRecord/preview',
    method: 'get',
    params: { salaryMonth }
  })
}

// 生成/重算月度结算
export function generateMonth(salaryMonth) {
  return request({
    url: '/system/salaryRecord/generate',
    method: 'post',
    params: { salaryMonth }
  })
}

// 确认结算单
export function confirmRecord(recordId) {
  return request({
    url: '/system/salaryRecord/confirm/' + recordId,
    method: 'put'
  })
}

// 删除结算单
export function delRecord(recordId) {
  return request({
    url: '/system/salaryRecord/' + recordId,
    method: 'delete'
  })
}
