const { request } = require('../../utils/request')
const app = getApp()

const STATUS_LABELS = { '1': '到课', '2': '录播', '3': '请假', '4': '试听到课', '5': '调课到课' }
const SOURCE_LABELS = { '1': '报名', '2': '试听', '3': '调课' }

function imgUrl(path) {
  if (!path) return ''
  return path.startsWith('http') ? path : `${app.globalData.baseUrl}${path}`
}

Page({
  data: {
    scheduleId: null,
    classDate: '',
    course: '',
    rows: [],
    review: null,
    loading: false,
    uploading: false,
    submitting: false,
    pendingImages: []
  },

  onLoad(query) {
    this.setData({
      scheduleId: Number(query.scheduleId),
      classDate: query.classDate || '',
      course: decodeURIComponent(query.course || ''),
      room: decodeURIComponent(query.room || '')
    })
    this.loadDetail()
  },

  async loadDetail() {
    const { scheduleId, classDate } = this.data
    this.setData({ loading: true })
    try {
      const res = await request({
        url: '/miniapp/admin/sign/detail',
        data: { scheduleId, classDate }
      })
      const data = res.data || {}
      const review = this.decorateReview(data.review || {})
      const rows = (data.details || []).map((d) => ({
        ...d,
        sourceText: SOURCE_LABELS[d.sourceType] || '报名',
        statusText: STATUS_LABELS[d.signStatus] || ''
      }))
      const course = rows.length
        ? (rows[0].courseClassName || [rows[0].gradeName, rows[0].subjectName].filter(Boolean).join(''))
        : ''
      const room = rows.length
        ? [rows[0].campusName, rows[0].classroomName].filter(Boolean).join(' · ')
        : ''
      this.setData({ review, rows, course, room })
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  decorateReview(review) {
    return {
      ...review,
      review1Images: (review.review1Images || '').split(',').filter(Boolean).map(imgUrl),
      review2Images: (review.review2Images || '').split(',').filter(Boolean).map(imgUrl)
    }
  },

  chooseImages() {
    wx.chooseMedia({
      count: 9,
      mediaType: ['image'],
      success: (res) => {
        const files = (res.tempFiles || []).map((f) => f.tempFilePath)
        if (files.length) this.uploadImages(files)
      }
    })
  },

  uploadImages(files) {
    this.setData({ uploading: true })
    const token = wx.getStorageSync('token')
    const baseUrl = app.globalData.baseUrl
    const results = []
    const uploadOne = (i) => {
      if (i >= files.length) {
        this.setData({
          pendingImages: this.data.pendingImages.concat(results),
          uploading: false
        })
        if (results.length) wx.showToast({ title: `已添加 ${results.length} 张图片`, icon: 'none' })
        return
      }
      wx.uploadFile({
        url: `${baseUrl}/common/upload`,
        filePath: files[i],
        name: 'file',
        header: { Authorization: `Bearer ${token}` },
        success: (res) => {
          try {
            const data = JSON.parse(res.data)
            if (data.code === 200 && data.fileName) results.push(data.fileName)
            else wx.showToast({ title: data.msg || '图片上传失败', icon: 'none' })
          } catch (e) {
            wx.showToast({ title: '上传响应解析失败', icon: 'none' })
          }
        },
        fail: () => wx.showToast({ title: '图片上传失败', icon: 'none' }),
        complete: () => uploadOne(i + 1)
      })
    }
    uploadOne(0)
  },

  removePending(e) {
    const { index } = e.currentTarget.dataset
    const pendingImages = this.data.pendingImages.filter((_, i) => i !== index)
    this.setData({ pendingImages })
  },

  previewImage(e) {
    const { urls, current } = e.currentTarget.dataset
    if (urls && urls.length) wx.previewImage({ urls, current })
  },

  async confirmReview() {
    const { scheduleId, classDate, pendingImages, submitting } = this.data
    if (submitting) return
    this.setData({ submitting: true })
    try {
      const res = await request({
        url: '/miniapp/admin/sign/review',
        method: 'POST',
        data: { scheduleId, classDate, images: pendingImages }
      })
      const confirmed = res.confirmedCount
      const done = res.reviewStatus === '1'
      wx.showToast({
        title: done ? '复核完成（2/2）' : `确认成功（${confirmed}/2）`,
        icon: 'success'
      })
      this.setData({ pendingImages: [] })
      await this.loadDetail()
    } catch (error) {
      wx.showToast({ title: error.message || '确认失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
