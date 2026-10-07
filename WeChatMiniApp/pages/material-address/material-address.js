const shop = require('../../utils/material-shop')
const empty = () => ({ receiverName: '', receiverPhone: '', provinceCode: '', cityCode: '', districtCode: '', provinceName: '', cityName: '', districtName: '', detailAddress: '', postalCode: '', isDefault: '0' })
Page({
  data: { loading: false, busy: false, error: '', addresses: [], pick: false, showForm: false, saveToBook: false, form: empty(), region: [] },
  onLoad(options) { this.setData({ pick: options.pick === '1' }); this._sequence = 0; this._operation = 0 },
  onShow() { this._hidden = false; this.setData({ busy: false }); return this.load() },
  onHide() { this._hidden = true; this._sequence++; this._operation++ },
  onUnload() { this.onHide() },
  async load() {
    const sequence = ++this._sequence
    this.setData({ loading: true, error: '', addresses: [] })
    try {
      const response = await shop.request({ url: shop.addressUrl })
      if (sequence === this._sequence && !this._hidden) this.setData({ addresses: (response.data || []).map(address => ({ ...address, isDefault: String(address.isDefault) === '1' ? '1' : '0' })) })
    } catch (error) { if (sequence === this._sequence && !this._hidden) this.setData({ error: error.message }) }
    finally { if (sequence === this._sequence && !this._hidden) this.setData({ loading: false }) }
  },
  edit(e) {
    if (this.data.busy) return
    const address = this.data.addresses.find(item => item.addressId === e.currentTarget.dataset.id)
    const form = address ? { ...address } : empty()
    this.setData({ form, saveToBook: !this.data.pick || Boolean(form.addressId), region: [form.provinceName, form.cityName, form.districtName].filter(Boolean), showForm: true }, () => { if (wx.pageScrollTo) wx.pageScrollTo({ selector: '.address-editor', duration: 200 }) })
  },
  closeForm() { if (!this.data.busy) this.setData({ showForm: false }) },
  input(e) { this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value }) },
  regionChange(e) {
    const names = e.detail.value, codes = e.detail.code || []
    this.setData({ region: names, 'form.provinceName': names[0], 'form.cityName': names[1], 'form.districtName': names[2], 'form.provinceCode': codes[0] || '', 'form.cityCode': codes[1] || '', 'form.districtCode': codes[2] || '' })
  },
  defaultChange(e) { this.setData({ 'form.isDefault': e.detail.value ? '1' : '0' }) },
  saveToBookChange(e) { if (!this.data.busy) this.setData({ saveToBook: Boolean(e.detail.value) }) },
  async save() {
    if (this._hidden || this.data.busy) return
    const normalized = Object.fromEntries(Object.keys(empty()).map(key => [key, String(this.data.form[key] || '').trim()]))
    normalized.isDefault = normalized.isDefault === '1' ? '1' : '0'
    const form = { ...this.data.form, ...normalized }
    if (!form.receiverName.trim() || !/^1\d{10}$/.test(form.receiverPhone) || !form.provinceName || !form.cityName || !form.districtName || !form.detailAddress.trim()) { shop.message(new Error('请填写姓名、11位手机号和完整地址')); return }
    const operation = ++this._operation
    this.setData({ busy: true })
    try {
      if (this.data.pick && !form.addressId && !this.data.saveToBook) {
        this.getOpenerEventChannel().emit('selected', normalized); wx.navigateBack(); return
      }
      await shop.request({ url: shop.addressUrl + (form.addressId ? `/${form.addressId}` : ''), method: form.addressId ? 'PUT' : 'POST', data: form })
      if (this._hidden || operation !== this._operation) return
      this.setData({ showForm: false }); await this.load()
    } catch (error) { if (!this._hidden && operation === this._operation) shop.message(error) }
    finally { if (!this._hidden && operation === this._operation) this.setData({ busy: false }) }
  },
  async operate(e) {
    if (this.data.busy) return
    const { id, action } = e.currentTarget.dataset
    if (!['delete', 'default'].includes(action)) return
    const operation = ++this._operation
    this.setData({ busy: true })
    try {
      if (action === 'delete' && !await shop.confirm('删除地址', '确认删除此地址？历史订单的收货地址仍会保留。')) return
      if (this._hidden || operation !== this._operation) return
      await shop.request({ url: `${shop.addressUrl}/${id}${action === 'default' ? '/default' : ''}`, method: action === 'default' ? 'POST' : 'DELETE' })
      if (!this._hidden && operation === this._operation) await this.load()
    } catch (error) { if (!this._hidden && operation === this._operation) shop.message(error) }
    finally { if (!this._hidden && operation === this._operation) this.setData({ busy: false }) }
  },
  select(e) {
    if (!this.data.pick || this.data.busy) return
    const address = this.data.addresses.find(item => item.addressId === e.currentTarget.dataset.id)
    if (address) { this.getOpenerEventChannel().emit('selected', address); wx.navigateBack() }
  }
})
