import request from '@/utils/request'

export function listMaterialOrder(query) {
  return request({
    url: '/system/materialOrder/list',
    method: 'get',
    params: query
  })
}

export function getMaterialOrder(orderId) {
  return request({
    url: '/system/materialOrder/' + orderId,
    method: 'get'
  })
}

export function updateMaterialOrder(data) {
  return request({
    url: '/system/materialOrder',
    method: 'put',
    data: data
  })
}

export function delMaterialOrder(orderId) {
  return request({
    url: '/system/materialOrder/' + orderId,
    method: 'delete'
  })
}
