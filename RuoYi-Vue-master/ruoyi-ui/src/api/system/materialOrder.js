import request from '@/utils/request'
import { shopResponse } from '@/utils/materialShop'

export function listMaterialOrder(query) {
  return request({
    url: '/system/materialOrder/list',
    method: 'get',
    params: query
  }).then(shopResponse)
}

export function getMaterialOrder(orderId) {
  return request({
    url: '/system/materialOrder/' + orderId,
    method: 'get'
  }).then(shopResponse)
}

export function shipMaterialOrder(orderId, data) {
  return request({
    url: '/system/materialOrder/' + orderId + '/ship',
    method: 'post',
    data: data
  }).then(shopResponse)
}

export function refundMaterialOrder(orderId, reason) {
  return request({
    url: '/system/materialOrder/' + orderId + '/refund',
    method: 'post',
    data: { reason }
  }).then(shopResponse)
}
