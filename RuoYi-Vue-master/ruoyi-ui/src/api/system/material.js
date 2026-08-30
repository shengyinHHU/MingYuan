import request from '@/utils/request'

export function listMaterial(query) {
  return request({
    url: '/system/material/list',
    method: 'get',
    params: query
  })
}

export function getMaterial(materialId) {
  return request({
    url: '/system/material/' + materialId,
    method: 'get'
  })
}

export function addMaterial(data) {
  return request({
    url: '/system/material',
    method: 'post',
    data: data
  })
}

export function updateMaterial(data) {
  return request({
    url: '/system/material',
    method: 'put',
    data: data
  })
}

export function delMaterial(materialId) {
  return request({
    url: '/system/material/' + materialId,
    method: 'delete'
  })
}

export function changeShelfStatus(materialId, shelfStatus) {
  return request({
    url: '/system/material/shelf/' + materialId,
    method: 'put',
    params: { shelfStatus: shelfStatus }
  })
}
