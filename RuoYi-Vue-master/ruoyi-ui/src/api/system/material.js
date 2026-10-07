import request from '@/utils/request'
import { shopResponse } from '@/utils/materialShop'

export function listMaterial(query) {
  return request({
    url: '/system/material/list',
    method: 'get',
    params: query
  }).then(shopResponse)
}

export function getMaterial(materialId) {
  return request({
    url: '/system/material/' + materialId,
    method: 'get'
  }).then(shopResponse)
}

export function addMaterial(data) {
  return request({
    url: '/system/material',
    method: 'post',
    data: data
  }).then(shopResponse)
}

export function updateMaterial(data) {
  return request({
    url: '/system/material',
    method: 'put',
    data: data
  }).then(shopResponse)
}

export function delMaterial(materialId) {
  return request({
    url: '/system/material/' + materialId,
    method: 'delete'
  }).then(shopResponse)
}

export function changeShelfStatus(materialId, shelfStatus) {
  return request({
    url: '/system/material/' + materialId + '/shelf',
    method: 'put',
    data: { shelfStatus }
  }).then(shopResponse)
}

export function adjustMaterialStock(materialId, data) {
  return request({ url: '/system/material/' + materialId + '/stock-adjustments', method: 'post', data }).then(shopResponse)
}
