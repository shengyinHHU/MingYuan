// Repeatable integration checks against a disposable database/backend only.
// Start that backend on 18081 or 18082; this script refuses the normal 8080 server.
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const base = process.env.SHOP_TEST_BASE_URL
assert.ok(base, 'Set SHOP_TEST_BASE_URL for the disposable backend')
const url = new URL(base)
assert.ok(['127.0.0.1', 'localhost'].includes(url.hostname))
assert.ok(['18081', '18082'].includes(url.port), 'Refusing normal project port')
const prefix = `HTTP-${Date.now()}`
let checks = 0
function check(condition, message) { assert.ok(condition, message); checks++ }
async function call(path, token, method = 'GET', body) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {}
  if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json'
  const response = await fetch(base + path, {
    method, headers,
    body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(15000)
  })
  const text = await response.text()
  let json
  try { json = JSON.parse(text) } catch { throw new Error(`${method} ${path}: non-JSON HTTP ${response.status}`) }
  return { status: response.status, ...json }
}
async function ok(path, token, method, body) {
  const result = await call(path, token, method, body)
  assert.equal(result.code, 200, `${method || 'GET'} ${path}: ${result.msg}`)
  checks++
  return result
}
async function denied(path, token, method, body) {
  const result = await call(path, token, method, body)
  check(result.code !== 200, `${path} must be rejected`)
  return result
}
async function upload(path, token, bytes, name, type) {
  const form = new FormData()
  form.append('file', new Blob([bytes], { type }), name)
  return ok(path, token, 'POST', form)
}

const admin = (await ok('/miniapp/login', null, 'POST', { devRole: 'admin', nickName: '商城验收' })).token
const buyer = (await ok('/miniapp/login', null, 'POST', { devRole: 'parent', nickName: '商城验收家长' })).token
check(admin && buyer, 'Development role login returns tokens')
await denied('/system/material/list', buyer)
await denied('/miniapp/parent/material/list', null)
const dict = (await ok('/miniapp/material/dict', buyer)).data
check(Array.isArray(dict.materialTypes) && dict.materialTypes.some(row => row.dictValue === 'workbook'), 'Product types use provisioned dictionary codes')
const subjectCode = dict.subjects[0].dictValue
const gradeCode = dict.grades[0].dictValue
const imageBytes = await readFile(new URL('../../WeChatMiniApp/images/logo.png', import.meta.url))
const cover = await upload('/common/upload', admin, imageBytes, 'shop-test-cover.png', 'image/png')
check(cover.fileName?.startsWith('/profile/'), 'Cover uses public relative upload path')

async function product(deliveryType, stock, extra = {}, publish = true) {
  const body = {
    title: `${prefix}-${deliveryType}`, subtitle: '独立测试库验收商品', subjectCode, gradeCode,
    materialType: 'workbook', textbookVersion: '测试版', deliveryType,
    price: deliveryType === 'DIGITAL' ? '7.50' : '20.00', unit: '本', purchaseLimit: 99,
    shippingFee: deliveryType === 'DIGITAL' ? '0.00' : '5.00', shipFrom: '江苏南京', dispatchDays: 2,
    intro: '仅用于自动验收', detailText: '仅用于独立测试库，非真实销售商品。',
    coverUrl: cover.fileName, shelfStatus: '2', images: [{ imageType: 'GALLERY', imageUrl: cover.fileName, sortOrder: 0 }],
    ...extra
  }
  const draft = (await ok('/system/material', admin, 'POST', body)).data
  check(typeof draft.materialId === 'string', 'Product ID remains a string')
  if (stock) await ok(`/system/material/${draft.materialId}/stock-adjustments`, admin, 'POST', { delta: stock, remark: '测试入库' })
  if (!publish) return draft
  return (await ok(`/system/material/${draft.materialId}/shelf`, admin, 'PUT', { shelfStatus: '1' })).data
}
const rejectedDrafts = []
for (const [name, deliveryType, stock, extra] of [
  ['missing-cover', 'PHYSICAL', 1, { coverUrl: '' }],
  ['zero-price', 'PHYSICAL', 1, { price: '0.00' }],
  ['missing-stock', 'PHYSICAL', 0, {}],
  ['missing-pdf', 'DIGITAL', 0, {}]
]) {
  const draft = await product(deliveryType, stock, { title: `${prefix}-${name}`, ...extra }, false)
  await denied(`/system/material/${draft.materialId}/shelf`, admin, 'PUT', { shelfStatus: '1' })
  assert.equal((await ok(`/system/material/${draft.materialId}`, admin)).data.shelfStatus, '2'); checks++
  await denied(`/miniapp/parent/material/${draft.materialId}`, buyer)
  rejectedDrafts.push(draft)
}
const physical = await product('PHYSICAL', 3)
const catalogue = (await ok(`/miniapp/parent/material/list?pageNum=1&pageSize=10&sort=priceAsc&title=${encodeURIComponent(physical.title)}`, buyer)).data
check(Array.isArray(catalogue.rows) && Number.isInteger(catalogue.total), 'Mini list uses data.rows/total')
assert.equal(catalogue.total, 1); assert.equal(catalogue.rows.length, 1); checks += 2
const listed = catalogue.rows.find(row => row.materialId === physical.materialId)
check(listed, 'Filtered catalogue contains the newly published fixture')
const parentDetail = (await ok(`/miniapp/parent/material/${physical.materialId}`, buyer)).data
for (const [view, row] of [['catalogue', listed], ['parent detail', parentDetail]]) {
  check(typeof row.materialId === 'string', `${view} keeps the published string ID`)
  for (const field of ['materialId', 'title', 'coverUrl', 'price']) {
    assert.equal(row[field], physical[field], `${view} ${field} matches the published admin record`); checks++
  }
}
check(!JSON.stringify(catalogue).includes('filePath'), 'Public catalogue omits private file keys')
const address = (await ok('/miniapp/parent/addresses', buyer, 'POST', {
  receiverName: '测试收货人', receiverPhone: '13800138000', provinceName: '江苏省', cityName: '南京市',
  districtName: '鼓楼区', detailAddress: '虚构测试楼101（非真实地址）', isDefault: '1'
})).data
check(typeof address.addressId === 'string', 'Address ID remains a string')
await denied(`/miniapp/parent/addresses/${address.addressId}`, admin, 'PUT', { ...address, receiverName: '越权修改' })
const createBody = { materialId: physical.materialId, quantity: 2, expectedPrice: '20.00', addressId: address.addressId, buyerRemark: '验收订单', clientRequestId: prefix + '-physical' }
for (const draft of rejectedDrafts) {
  await denied('/miniapp/parent/material/orders', buyer, 'POST', {
    ...createBody, materialId: draft.materialId, quantity: 1, expectedPrice: draft.price,
    clientRequestId: prefix + '-draft-' + draft.materialId
  })
}
const withoutAddress = { ...createBody, clientRequestId: prefix + '-missing-address' }
delete withoutAddress.addressId
await denied('/miniapp/parent/material/orders', buyer, 'POST', withoutAddress)
await denied('/miniapp/parent/material/orders', buyer, 'POST', {
  ...withoutAddress, address: { ...address, receiverPhone: '' }, clientRequestId: prefix + '-missing-phone'
})
assert.equal((await ok(`/system/material/${physical.materialId}`, admin)).data.stockLocked, 0); checks++
for (const quantity of [0, -1, 1.5, 100]) {
  await denied('/miniapp/parent/material/orders', buyer, 'POST', { ...createBody, quantity, clientRequestId: prefix + '-invalid-' + quantity })
}
const order = (await ok('/miniapp/parent/material/orders', buyer, 'POST', createBody)).data
check(typeof order.orderId === 'string' && order.orderStatus === 'WAIT_PAY', 'Create returns string ID and WAIT_PAY')
assert.equal(order.goodsAmount, '40.00'); assert.equal(order.shippingAmount, '5.00')
assert.equal(order.payableAmount, '45.00'); assert.equal(order.amount, '0.00'); checks += 4
assert.equal((await ok('/miniapp/parent/material/orders', buyer, 'POST', createBody)).data.orderId, order.orderId); checks++
await denied('/miniapp/parent/material/orders', buyer, 'POST', { ...createBody, quantity: 1 })
await denied(`/miniapp/parent/material/orders/${order.orderId}`, admin)
await denied('/miniapp/parent/material/buy', buyer, 'POST', { materialId: physical.materialId })
await denied('/system/materialOrder', admin, 'PUT', { orderId: order.orderId, payStatus: '1' })
await denied(`/system/materialOrder/${order.orderId}`, admin, 'DELETE')

const current = (await ok(`/system/material/${physical.materialId}`, admin)).data
await ok('/system/material', admin, 'PUT', { ...current, title: prefix + '-edited', price: '99.00' })
await ok(`/miniapp/parent/addresses/${address.addressId}`, buyer, 'PUT', { ...address, receiverName: '修改后收货人' })
await ok(`/system/material/${physical.materialId}/shelf`, admin, 'PUT', { shelfStatus: '0' })
await denied('/miniapp/parent/material/orders', buyer, 'POST', { ...createBody, quantity: 1, expectedPrice: '99.00', clientRequestId: prefix + '-off-shelf' })
const prepared = (await ok(`/miniapp/parent/material/orders/${order.orderId}/payment`, buyer, 'POST')).data
assert.equal(prepared.mode, 'MOCK'); checks++
await ok(`/miniapp/parent/material/orders/${order.orderId}/mock-pay`, buyer, 'POST')
const paid = (await ok(`/miniapp/parent/material/orders/${order.orderId}/mock-pay`, buyer, 'POST')).data
assert.equal(paid.amount, '45.00'); assert.equal(paid.orderStatus, 'WAIT_SHIP'); checks += 2
assert.equal(paid.items[0].unitPrice, '20.00'); assert.equal(paid.items[0].materialTitle, physical.title)
assert.equal(paid.receiverName, '测试收货人'); checks += 3
check(!('adminRemark' in paid) && !('assetPath' in paid.items[0]), 'Customer DTO hides internal data')
const inventory = (await ok(`/system/material/${physical.materialId}`, admin)).data
assert.equal(inventory.stockQuantity, 1); assert.equal(inventory.stockLocked, 0); assert.equal(inventory.saleCount, 2); checks += 3
const adminRows = await ok(`/system/materialOrder/list?orderCode=${order.orderCode}`, admin)
check(adminRows.rows.length === 1 && adminRows.total === 1, 'Admin paging/filter contract')
check(adminRows.rows[0].receiverPhone.includes('*'), 'Admin list masks phone')
check(!('detailAddress' in adminRows.rows[0]) && !('provinceName' in adminRows.rows[0]), 'List permission does not expose full shipping address')
assert.equal((await ok(`/system/materialOrder/${order.orderId}`, admin)).data.receiverPhone, '13800138000'); checks++
const parcel = { carrierCode: 'SF', carrierName: '顺丰（测试）', trackingNo: 'TEST000001' }
await ok(`/system/materialOrder/${order.orderId}/ship`, admin, 'POST', parcel)
await ok(`/system/materialOrder/${order.orderId}/ship`, admin, 'POST', parcel)
await denied(`/system/materialOrder/${order.orderId}/refund`, admin, 'POST', { reason: '已发货不能退' })
await ok(`/miniapp/parent/material/orders/${order.orderId}/receive`, buyer, 'POST')
assert.equal((await ok(`/miniapp/parent/material/orders/${order.orderId}/receive`, buyer, 'POST')).data.orderStatus, 'COMPLETED'); checks++
await ok(`/system/material/${physical.materialId}/shelf`, admin, 'PUT', { shelfStatus: '1' })

const cancelBody = { ...createBody, quantity: 1, expectedPrice: '99.00', clientRequestId: prefix + '-cancel' }
const adminUser = (await ok('/getInfo', admin)).user
check(String(adminUser.userId) !== order.parentId, 'Spoof fixture uses a different known user')
const spoofed = (await ok('/miniapp/parent/material/orders', buyer, 'POST', {
  ...cancelBody, parentId: String(adminUser.userId), openid: 'forged-openid-test-only', clientRequestId: prefix + '-spoof'
})).data
assert.equal(spoofed.parentId, order.parentId); assert.equal(spoofed.orderStatus, 'WAIT_PAY'); checks += 2
await denied(`/miniapp/parent/material/orders/${spoofed.orderId}`, admin)
// The design reserves this future WeChat callback; no handler is implemented.
// Checking the real order afterwards proves the request did not record payment.
const fakeNotify = []
for (const token of [null, buyer]) {
  const rejected = await denied('/miniapp/payment/wechat/notify', token, 'POST', {
    orderId: spoofed.orderId, orderCode: spoofed.orderCode, parentId: String(adminUser.userId),
    openid: 'forged-openid-test-only', transaction_id: 'fake-transaction-test-only', trade_state: 'SUCCESS'
  })
  fakeNotify.push({ authenticated: Boolean(token), httpStatus: rejected.status, applicationCode: rejected.code })
}
const afterNotify = (await ok(`/miniapp/parent/material/orders/${spoofed.orderId}`, buyer)).data
assert.equal(afterNotify.orderStatus, 'WAIT_PAY'); assert.equal(afterNotify.amount, '0.00')
assert.equal(afterNotify.payment.paymentStatus, 'CREATED'); checks += 3
await ok(`/miniapp/parent/material/orders/${spoofed.orderId}/cancel`, buyer, 'POST')
const cancelled = (await ok('/miniapp/parent/material/orders', buyer, 'POST', cancelBody)).data
await ok(`/miniapp/parent/material/orders/${cancelled.orderId}/cancel`, buyer, 'POST')
await ok(`/miniapp/parent/material/orders/${cancelled.orderId}/cancel`, buyer, 'POST')
await denied(`/miniapp/parent/material/orders/${cancelled.orderId}/mock-pay`, buyer, 'POST')
assert.equal((await ok(`/system/material/${physical.materialId}`, admin)).data.availableStock, 1); checks++

const scarce = await product('PHYSICAL', 1)
const race = await Promise.all([0, 1].map(i => call('/miniapp/parent/material/orders', buyer, 'POST', {
  ...createBody, materialId: scarce.materialId, quantity: 1, clientRequestId: prefix + '-race-' + i
})))
assert.equal(race.filter(r => r.code === 200).length, 1); checks++
await ok(`/miniapp/parent/material/orders/${race.find(r => r.code === 200).data.orderId}/cancel`, buyer, 'POST')

const pdfBytes = Buffer.from('%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n')
const file = (await upload('/system/material/file', admin, pdfBytes, 'integration-test.pdf', 'application/pdf')).data
for (const [path, token] of [['/profile/not-real.pdf', null], ['/common/download/resource?resource=' + encodeURIComponent(file.filePath), admin]]) {
  assert.equal((await fetch(base + path, { headers: token ? { Authorization: `Bearer ${token}` } : {}, signal: AbortSignal.timeout(15000) })).status, 403, 'Public PDF bypass is blocked'); checks++
}
const digital = await product('DIGITAL', 0, file)
const digitalBody = { materialId: digital.materialId, quantity: 1, expectedPrice: '7.50', clientRequestId: prefix + '-digital' }
const digitalOrder = (await ok('/miniapp/parent/material/orders', buyer, 'POST', digitalBody)).data
await denied(`/miniapp/parent/material/download/${digitalOrder.orderId}`, buyer)
await ok(`/miniapp/parent/material/orders/${digitalOrder.orderId}/mock-pay`, buyer, 'POST')
const newPdf = Buffer.from(pdfBytes.toString().replace('Catalog', 'Catalog /Version /1.5'))
const replacement = (await upload('/system/material/file', admin, newPdf, 'new-version.pdf', 'application/pdf')).data
const digitalCurrent = (await ok(`/system/material/${digital.materialId}`, admin)).data
await ok('/system/material', admin, 'PUT', { ...digitalCurrent, ...replacement })
const download = await fetch(base + `/miniapp/parent/material/download/${digitalOrder.orderId}`, { headers: { Authorization: `Bearer ${buyer}` }, signal: AbortSignal.timeout(15000) })
assert.equal(download.status, 200); assert.equal(await download.text(), pdfBytes.toString()); checks += 2
await denied(`/miniapp/parent/material/download/${digitalOrder.orderId}`, admin)
await ok(`/system/materialOrder/${digitalOrder.orderId}/refund`, admin, 'POST', { reason: '模拟退款验收' })
await ok(`/system/materialOrder/${digitalOrder.orderId}/refund`, admin, 'POST', { reason: '模拟退款重试' })
await denied(`/miniapp/parent/material/download/${digitalOrder.orderId}`, buyer)
await denied('/system/material', buyer, 'POST', { title: '伪装管理员' })
const expiryCandidate = (await ok('/miniapp/parent/material/orders', buyer, 'POST', { ...cancelBody, clientRequestId: prefix + '-expiry' })).data
console.log(JSON.stringify({ passed: true, assertions: checks, physicalOrder: order.orderCode, digitalOrder: digitalOrder.orderCode, expiryCandidate: expiryCandidate.orderCode, fakeNotify, unchangedOrderStatus: afterNotify.orderStatus, backend: base }))
