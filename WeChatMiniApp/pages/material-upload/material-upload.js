const { request } = require('../../utils/request')
const app = getApp()

Page({
  data: {
    loading: false,
    submitting: false,
    subjects: [],
    grades: [],
    subjectIndex: -1,
    gradeIndex: -1,
    list: [],
    showEmpty: false,
    showForm: false,
    form: {
      title: '',
      price: '',
      intro: ''
    },
    file: null
  },

  onLoad() {
    this.loadDict().then(() => this.loadList())
  },

  onPullDownRefresh() {
    Promise.all([this.loadDict(), this.loadList()])
      .finally(() => wx.stopPullDownRefresh())
  },

  async loadDict() {
    try {
      const res = await request({ url: '/miniapp/material/dict' })
      const data = res.data || {}
      this.setData({
        subjects: data.subjects || [],
        grades: data.grades || []
      })
    } catch (error) {
      // 字典失败不阻断
    }
  },

  async loadList() {
    this.setData({ loading: true })
    try {
      const res = await request({ url: '/miniapp/teacher/material/list' })
      const list = this.decorate(res.data || [])
      this.setData({ list, showEmpty: list.length === 0 })
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' })
      this.setData({ list: [], showEmpty: true })
    } finally {
      this.setData({ loading: false })
    }
  },

  decorate(list) {
    return list.map((item) => ({
      ...item,
      priceText: Number(item.price) === 0 ? '免费' : `¥${Number(item.price).toFixed(2)}`,
      subjectText: this.labelOf(this.data.subjects, item.subjectName) || item.subjectName,
      gradeText: this.labelOf(this.data.grades, item.gradeName) || item.gradeName,
      shelfText: item.shelfStatus === '1' ? '已上架' : '已下架',
      shelfClass: item.shelfStatus === '1' ? 'on' : 'off',
      sizeText: this.formatSize(item.fileSize)
    }))
  },

  labelOf(list, value) {
    const hit = (list || []).find((i) => i.dictValue === value)
    return hit ? hit.dictLabel : ''
  },

  formatSize(bytes) {
    if (!bytes) return ''
    const n = Number(bytes)
    if (n < 1024) return `${n}B`
    if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)}KB`
    return `${(n / 1024 / 1024).toFixed(1)}MB`
  },

  openForm() {
    this.setData({
      showForm: true,
      subjectIndex: -1,
      gradeIndex: -1,
      form: { title: '', price: '', intro: '' },
      file: null
    })
  },

  closeForm() {
    if (this.data.submitting) return
    this.setData({ showForm: false })
  },

  onTitleInput(e) {
    this.setData({ 'form.title': e.detail.value })
  },

  onPriceInput(e) {
    this.setData({ 'form.price': e.detail.value })
  },

  onIntroInput(e) {
    this.setData({ 'form.intro': e.detail.value })
  },

  onSubjectChange(e) {
    this.setData({ subjectIndex: Number(e.detail.value) })
  },

  onGradeChange(e) {
    this.setData({ gradeIndex: Number(e.detail.value) })
  },

  chooseFile() {
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: ['pdf'],
      success: (res) => {
        const f = res.tempFiles && res.tempFiles[0]
        if (!f) return
        if (!f.name.toLowerCase().endsWith('.pdf')) {
          wx.showToast({ title: '仅支持 PDF 文件', icon: 'none' })
          return
        }
        if (f.size > 10 * 1024 * 1024) {
          wx.showToast({ title: '文件不能超过 10MB', icon: 'none' })
          return
        }
        this.setData({ file: f })
      },
      fail() {}
    })
  },

  async submit() {
    const { form, subjectIndex, gradeIndex, subjects, grades, file, submitting } = this.data
    if (submitting) return
    if (!form.title.trim()) {
      wx.showToast({ title: '请填写标题', icon: 'none' })
      return
    }
    if (subjectIndex < 0) {
      wx.showToast({ title: '请选择学科', icon: 'none' })
      return
    }
    if (gradeIndex < 0) {
      wx.showToast({ title: '请选择年级', icon: 'none' })
      return
    }
    if (!file) {
      wx.showToast({ title: '请选择 PDF 文件', icon: 'none' })
      return
    }
    const priceVal = form.price === '' ? 0 : Number(form.price)
    if (isNaN(priceVal) || priceVal < 0) {
      wx.showToast({ title: '价格格式不正确', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      // 1. 上传 PDF 文件
      const baseUrl = app.globalData.baseUrl
      const token = wx.getStorageSync('token')
      const uploadRes = await new Promise((resolve, reject) => {
        wx.uploadFile({
          url: `${baseUrl}/common/upload`,
          filePath: file.path,
          name: 'file',
          header: { Authorization: `Bearer ${token}` },
          formData: {},
          success(res) {
            try {
              const data = JSON.parse(res.data)
              if (data.code !== 200) {
                reject(new Error(data.msg || '上传失败'))
                return
              }
              resolve(data)
            } catch (e) {
              reject(new Error('上传响应解析失败'))
            }
          },
          fail(err) {
            reject(new Error(err.errMsg || '上传失败'))
          }
        })
      })

      // 2. 提交资料
      await request({
        url: '/miniapp/teacher/material',
        method: 'POST',
        data: {
          title: form.title.trim(),
          subjectName: subjects[subjectIndex].dictValue,
          gradeName: grades[gradeIndex].dictValue,
          price: priceVal,
          intro: form.intro || '',
          filePath: uploadRes.fileName,
          fileName: uploadRes.originalFilename || file.name,
          fileSize: file.size
        }
      })

      wx.showToast({ title: '发布成功', icon: 'success' })
      this.setData({ showForm: false, file: null })
      await this.loadList()
    } catch (error) {
      wx.showToast({ title: error.message || '发布失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },

  remove(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    wx.showModal({
      title: '删除资料',
      content: '删除后不可恢复，确认删除吗？',
      success: (res) => {
        if (!res.confirm) return
        this.doRemove(id)
      }
    })
  },

  async doRemove(id) {
    try {
      await request({
        url: `/miniapp/teacher/material/${id}`,
        method: 'DELETE'
      })
      wx.showToast({ title: '已删除', icon: 'success' })
      this.loadList()
    } catch (error) {
      wx.showToast({ title: error.message || '删除失败', icon: 'none' })
    }
  }
})
