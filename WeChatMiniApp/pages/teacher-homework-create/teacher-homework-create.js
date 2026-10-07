const { request } = require('../../utils/request')
const app = getApp()

function uploadFile(path) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${app.globalData.baseUrl}/common/upload`, filePath: path, name: 'file',
      header: { Authorization: `Bearer ${wx.getStorageSync('token')}` },
      success(res) { try { const data = JSON.parse(res.data); data.code === 200 ? resolve(data) : reject(new Error(data.msg || '上传失败')) } catch (e) { reject(new Error('上传响应解析失败')) } },
      fail: e => reject(new Error(e.errMsg || '上传失败'))
    })
  })
}
function fileItem(data, fallbackName, size) { return { fileName: data.fileName || fallbackName, filePath: data.fileName || data.filePath || data.url, fileSize: size || 0 } }
Page({
  data: { homeworkId: null, editing: false, form: { scheduleId: '', title: '', description: '', deadline: '' }, schedules: [], deadlineDate: '', deadlineTime: '', files: [], saving: false },
  onLoad(query) { this.setData({ homeworkId: query.homeworkId || null, editing: !!query.homeworkId }); this.loadSchedules() },
  async loadHomework(id) { try { const r = await request({ url: `/miniapp/teacher/homework/${id}` }); const d = r.data || {}; const item = d.homework || d; const deadline = item.deadline || ''; const parts = String(deadline).split(' '); this.setData({ form: { scheduleId: String(item.scheduleId || ''), title: item.title || '', description: item.description || '', deadline }, deadlineDate: parts[0] || '', deadlineTime: (parts[1] || '').slice(0, 5), files: d.files || [] }) } catch (e) { wx.showToast({ title: e.message || '草稿加载失败', icon: 'none' }) } },

  async loadSchedules() { try { const res = await request({ url: '/miniapp/teacher/sign/list' }); const schedules = (res.data || []).map(item => ({ scheduleId: item.scheduleId || item.id })); this.setData({ schedules }); if (this.data.homeworkId) this.loadHomework(this.data.homeworkId); else if (schedules.length) this.setData({ 'form.scheduleId': String(schedules[0].scheduleId) }) } catch (e) { wx.showToast({ title: e.message || '班级加载失败', icon: 'none' }) } },
  input(e) { this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value }) },
  dateChange(e) { this.setData({ deadlineDate: e.detail.value, 'form.deadline': `${e.detail.value} ${this.data.deadlineTime || '23:59'}:00` }) },
  timeChange(e) { this.setData({ deadlineTime: e.detail.value, 'form.deadline': `${this.data.deadlineDate} ${e.detail.value}:00` }) },
  chooseImage() { wx.chooseImage({ count: 9, sizeType: ['compressed'], sourceType: ['album', 'camera'], success: r => r.tempFiles.forEach(f => this.upload(f.path, f.size, f.path.split('/').pop())) }) },
  choosePdf() { wx.chooseMessageFile({ count: 1, type: 'file', extension: ['pdf'], success: r => { const f = r.tempFiles[0]; if (f && f.name.toLowerCase().endsWith('.pdf')) this.upload(f.path, f.size, f.name); else wx.showToast({ title: '仅支持 PDF 文件', icon: 'none' }) } }) },
  async upload(path, size, name) { try { const data = await uploadFile(path); this.setData({ files: this.data.files.concat([fileItem(data, name, size)]) }) } catch (e) { wx.showToast({ title: e.message || '附件上传失败', icon: 'none' }) } },
  removeFile(e) { const files = this.data.files.slice(); files.splice(Number(e.currentTarget.dataset.index), 1); this.setData({ files }) },
  async save() { const { form, files } = this.data; if (!form.scheduleId) return wx.showToast({ title: '你暂无负责的班级，无法创建作业', icon: 'none' }); if (!form.title.trim()) return wx.showToast({ title: '请填写作业标题', icon: 'none' }); if (!form.deadline) return wx.showToast({ title: '请选择截止时间', icon: 'none' }); this.setData({ saving: true }); try { await request({ url: '/miniapp/teacher/homework', method: 'POST', data: { ...form, homeworkId: this.data.homeworkId, scheduleId: Number(form.scheduleId), questions: [], files } }); wx.showToast({ title: '草稿已保存', icon: 'success' }); setTimeout(() => wx.navigateBack(), 500) } catch (e) { wx.showToast({ title: e.message || '保存失败', icon: 'none' }) } finally { this.setData({ saving: false }) } }
})