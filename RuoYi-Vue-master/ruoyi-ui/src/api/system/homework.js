import request from '@/utils/request'

const baseUrl = '/admin/education/homework'

export function listHomework(query) {
  return request({
    url: baseUrl + '/list',
    method: 'get',
    params: query
  }).then(response => ({
    rows: response.rows || response.data || [],
    total: response.total || (Array.isArray(response.data) ? response.data.length : 0)
  }))
}

export function getHomeworkSubmissionStats(homeworkId) {
  return request({
    url: '/miniapp/teacher/homework/' + homeworkId + '/submission-stats',
    method: 'get'
  })
}

export function archiveHomework(homeworkId) {
  return request({
    url: baseUrl + '/' + homeworkId + '/archive',
    method: 'post'
  })
}

export function restoreHomework(homeworkId) {
  return request({
    url: baseUrl + '/' + homeworkId + '/restore',
    method: 'post'
  })
}

export function delHomework(homeworkId) {
  return request({
    url: baseUrl + '/' + homeworkId,
    method: 'delete'
  })
}
