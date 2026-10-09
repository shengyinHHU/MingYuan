import request from '@/utils/request'
export function listReceipts(params) {
  return request({ url: '/system/tuition/receipts/list', method: 'get', params })
}
export function issueReceipt(id) {
  return request({ url: '/system/tuition/receipts/' + id + '/issue', method: 'post' })
}
export function retryReceipt(id) {
  return request({ url: '/system/tuition/receipts/' + id + '/retry', method: 'post' })
}
export function replaceReceipt(id, reason) {
  return request({
    url: '/system/tuition/receipts/' + id + '/replace',
    method: 'post',
    data: { reason },
  })
}
export function receiptFile(id) {
  return request({
    url: '/system/tuition/receipts/' + id + '/file',
    method: 'get',
    responseType: 'blob',
    timeout: 60000,
  })
}
