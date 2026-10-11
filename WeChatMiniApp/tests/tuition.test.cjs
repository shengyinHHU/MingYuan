const test = require('node:test')
const assert = require('node:assert/strict')
const path = require('node:path')
const fs = require('node:fs')
const root = path.resolve(__dirname, '..')
let definition, calls, storage
const app = { globalData: { baseUrl: 'http://127.0.0.1:8080' } }
global.getApp = () => app
global.Page = value => { definition = value }
function env(handler, role = 'parent') {
  calls = []; storage = storage || new Map()
  app.globalData.roles = [role]; app.globalData.userInfo = { userId: '9007199254740993' }; app.globalData.permissions = []
  global.wx = { getStorageSync: key => key === 'token' ? 'jwt' : storage.get(key), setStorageSync: (k, v) => storage.set(k, structuredClone(v)), removeStorageSync: k => storage.delete(k), showToast() {}, showModal: o => o.success({ confirm: true }), reLaunch() {}, navigateTo() {}, stopPullDownRefresh() {}, request(o) { calls.push({ url: o.url, method: o.method, data: structuredClone(o.data) }); handler(o) } }
}
function ok(o, data) { o.success({ statusCode: 200, data: { code: 200, data } }) }
function page(name) {
  const file = path.join(root, 'pages', name, name + '.js')
  assert.ok(fs.existsSync(file), `${name} is implemented`)
  delete require.cache[require.resolve(file)]; require(file)
  const p = { ...definition, data: structuredClone(definition.data), setData(values) { for (const [key, value] of Object.entries(values)) { const parts = key.split('.'); let obj = this.data; while (parts.length > 1) obj = obj[parts.shift()]; obj[parts[0]] = value } } }
  return p
}
const bill = { enrollmentId: '9007199254740995', billingStatus: 'OPEN', financeMode: 'MOCK', mockAllowed: true, originalAmount: '1000.00', discountAmount: '100.00', payableAmount: '900.00', receivedAmount: '0.00', refundedAmount: '0.00', netAmount: '0.00', dueAmount: '900.00', refundableAmount: '0.00', financialVersion: 2, payments: [], refunds: [], receipts: [], coupons: [] }
function helper() { assert.ok(fs.existsSync(path.join(root, 'utils/tuition.js')), 'tuition helper is implemented'); return require('../utils/tuition') }
test('historical unknown money stays unknown and refunds use server due amount', () => {
  env(o => ok(o, {})); const t = helper()
  const history = t.bill({ billingStatus: 'HISTORY_PENDING', originalAmount: null, receivedAmount: null, dueAmount: null })
  assert.equal(history.receivedText, '待核实'); assert.equal(history.canPay, false)
  const refunded = t.bill({ ...bill, receivedAmount: '900.00', refundedAmount: '300.00', netAmount: '600.00', dueAmount: '0.00', payments: [{ paymentId: '9', paymentStatus: 'SUCCESS' }] })
  assert.equal(refunded.dueText, '¥0.00'); assert.equal(refunded.canPay, false)
})
test('payment retry after ambiguous create reuses account scoped immutable payload across pages', async () => {
  storage = new Map(); let creates = 0, payload
  env(o => {
    if (o.url.endsWith('/quote')) ok(o, { ...bill, userCouponId: '9007199254740997' })
    else if (o.url.endsWith('/payments')) { creates++; if (creates === 1) { payload = structuredClone(o.data); o.fail({ errMsg: 'socket closed' }) } else { assert.deepEqual(o.data, payload); ok(o, { paymentId: '9007199254740999', channel: 'MOCK', paymentStatus: 'PENDING' }) } }
    else if (o.url.endsWith('/mock-confirm')) ok(o, { paymentStatus: 'SUCCESS' })
    else if (o.url.includes('/payments/')) ok(o, { paymentId: '9007199254740999', channel: 'MOCK', paymentStatus: 'SUCCESS' })
    else ok(o, bill)
  })
  const first = page('tuition-detail'); first.onLoad({ id: bill.enrollmentId }); await first.onShow(); await first.pay()
  assert.equal(creates, 1); assert.ok(storage.size > 0)
  const retry = page('tuition-detail'); retry.onLoad({ id: bill.enrollmentId }); await retry.onShow(); retry.setData({ selectedCouponId: 'changed', quote: { payableAmount: '1.00', financialVersion: 99 } }); await retry.pay()
  assert.equal(creates, 2); assert.equal(payload.expectedPayableAmount, '900.00'); assert.equal(payload.userCouponId, '9007199254740997'); assert.equal(storage.size, 0)
  assert.equal(calls.some(c => 'parentId' in c.data), false)
})
test('real positive bills cannot invoke mock creation while zero quote uses FREE without mock-confirm', async () => {
  storage = new Map(); let zero = false
  env(o => o.url.endsWith('/quote') ? ok(o, { ...bill, payableAmount: zero ? '0.00' : '900.00' }) : o.url.endsWith('/payments') ? ok(o, { paymentId: '88', channel: 'FREE', paymentStatus: 'SUCCESS' }) : ok(o, { ...bill, financeMode: 'REAL', mockAllowed: false }))
  const p = page('tuition-detail'); p.onLoad({ id: bill.enrollmentId }); await p.onShow(); await p.pay(); assert.equal(calls.some(c => c.url.endsWith('/payments')), false)
  zero = true; await p.onShow(); await p.pay(); assert.equal(calls.find(c => c.url.endsWith('/payments')).data.channel, 'FREE'); assert.equal(calls.some(c => c.url.endsWith('/mock-confirm')), false)
})
test('teacher cannot read admin bills and missing operation permission prevents writes', async () => {
  env(o => ok(o, {}), 'teacher'); const p = page('admin-tuition'); p.onLoad({}); await p.onShow(); assert.equal(calls.length, 0)
  env(o => o.url.endsWith('/getInfo') ? o.success({ statusCode: 200, data: { code: 200, roles: ['admin'], permissions: ['system:tuition:list', 'system:tuition:query'] } }) : ok(o, bill), 'admin')
  const a = page('admin-tuition'); a.onLoad({ id: bill.enrollmentId }); await a.onShow(); a.setData({ price: '1.00' }); await a.savePrice(); assert.equal(calls.some(c => c.method === 'PUT'), false)
})
test('refund validates positive exact amount and retries same application after lost response', async () => {
  storage = new Map(); let sent
  env(o => { if (o.method === 'POST') { if (!sent) { sent = structuredClone(o.data); o.fail({ errMsg: 'timeout' }) } else { assert.deepEqual(o.data, sent); ok(o, { refundId: '9' }) } } else if (o.url.endsWith('/refunds')) o.success({ statusCode: 200, data: { code: 200, rows: [], total: 0 } }); else ok(o, { ...bill, refundableAmount: '900.00', payments: [{ paymentId: '9007199254740999', paymentStatus: 'SUCCESS', channel: 'MOCK', amount: '900.00' }] }) })
  const p = page('tuition-refund'); p.onLoad({ id: bill.enrollmentId }); await p.onShow(); p.setData({ amount: '1.001', reason: '原因' }); await p.apply(); assert.equal(sent, undefined)
  p.setData({ amount: '300.00' }); await p.apply(); p.setData({ amount: '600.00' }); await p.apply(); assert.equal(sent.requestedAmount, '300.00'); assert.equal(sent.paymentId, '9007199254740999'); assert.equal(storage.size, 0)
})
test('receipt only opens authenticated successful PDF and blocks a JSON error download', async () => {
  env(o => ok(o, {})); let downloaded, opened = 0, pdf = false
  wx.downloadFile = o => { downloaded = o; o.success({ statusCode: 200, tempFilePath: '/tmp/receipt.pdf' }) }; wx.openDocument = o => { opened++; o.success() }; wx.getFileSystemManager = () => ({ readFile(o) { o.success({ data: Buffer.from(pdf ? '%PDF-1.7\n' : '{"code":403,"msg":"无权限"}') }) } })
  const t = helper(); await assert.rejects(t.downloadReceipt('9007199254740999'), /PDF|无权限/); assert.equal(opened, 0)
  pdf = true; await t.downloadReceipt('9007199254740999'); assert.equal(downloaded.header.Authorization, 'Bearer jwt'); assert.equal(opened, 1)
})
test('pending UNKNOWN result is queried and never mock confirmed or recreated', async () => {
  storage = new Map()
  env(o => ok(o, o.url.includes('/payments/') ? { paymentId: '72', paymentStatus: 'UNKNOWN' } : { ...bill, payments: [{ paymentId: '72', channel: 'MOCK', paymentStatus: 'UNKNOWN' }] }))
  const p = page('tuition-detail'); p.onLoad({ id: bill.enrollmentId }); await p.onShow(); await p.resumePayment()
  assert.equal(calls.filter(c => c.method === 'POST').length, 0); assert.equal(p.data.detail.activePayment.paymentStatus, 'UNKNOWN')
})
test('stale coupon quote cannot replace the latest selected coupon', async () => {
  storage = new Map(); const quotes = []
  env(o => { if (o.url.endsWith('/quote')) quotes.push(o); else ok(o, bill) })
  const p = page('tuition-detail'); p.onLoad({ id: bill.enrollmentId }); const loading = p.onShow(); await new Promise(r => setImmediate(r)); ok(quotes[0], { ...bill }); await loading
  const old = p.quoteCoupon('1'), latest = p.quoteCoupon('2'); ok(quotes[2], { ...bill, userCouponId: '2', payableAmount: '800.00' }); await latest; ok(quotes[1], { ...bill, userCouponId: '1', payableAmount: '900.00' }); await old
  assert.equal(p.data.selectedCouponId, '2'); assert.equal(p.data.quote.payableAmount, '800.00')
})
test('parent bill pagination, receipt and coupon lists consume TableDataInfo without forging owner', async () => {
  storage = new Map()
  env(o => o.success({ statusCode: 200, data: { code: 200, rows: o.url.endsWith('/coupons') ? [{ userCouponId: '991', couponName: '课程券', discountAmount: '100.00', minSpendAmount: '800.00', displayStatus: 'EXPIRED' }] : o.url.endsWith('/receipts') ? [{ receiptId: '990', documentType: 'WAIVER', receiptStatus: 'ISSUED', fileStatus: 'FAILED', amount: '0.00' }] : [{ ...bill, enrollmentId: String(o.data.pageNum) }], total: 2 } }))
  const list = page('tuition-list'); list.onLoad(); await list.onShow(); await list.onReachBottom(); assert.deepEqual(list.data.rows.map(r => r.enrollmentId), ['1', '2'])
  const receipt = page('tuition-receipt'); receipt.onLoad({ id: bill.enrollmentId }); await receipt.onShow(); assert.equal(receipt.data.rows[0].canDownload, false); assert.equal(receipt.data.rows[0].title, '费用减免确认单')
  const coupons = page('my-coupons'); coupons.onLoad(); await coupons.onShow(); assert.equal(coupons.data.rows[0].statusText, '已过期')
  assert.equal(calls.some(c => 'parentId' in c.data), false)
})
test('admin authorized price and offline confirmation preserve decimal amount and big IDs', async () => {
  storage = new Map()
  env(o => o.url.endsWith('/getInfo') ? o.success({ statusCode: 200, data: { code: 200, roles: ['admin'], permissions: ['*:*:*'] } }) : ok(o, { ...bill, financeMode: 'REAL', mockAllowed: false, payments: [{ paymentId: '9007199254740999', paymentStatus: 'PENDING', channel: 'BANK_TRANSFER', amount: '900.00', evidenceKey: 'private-key', providerTradeNo: 'transfer-1', paidTime: '2026-10-07 12:00:00' }] }), 'admin')
  const p = page('admin-tuition'); p.onLoad({ id: bill.enrollmentId }); await p.onShow(); p.setData({ price: '1200.00' }); await p.savePrice()
  const priced = calls.find(c => c.method === 'PUT'); assert.equal(priced.data.originalAmount, '1200.00'); assert.equal(priced.data.financialVersion, 2)
  await p.paymentAction({ currentTarget: { dataset: { id: '9007199254740999', action: 'confirm' } } }); const confirmed = calls.find(c => c.url.endsWith('/confirm')); assert.ok(confirmed.url.includes('9007199254740999')); assert.equal(confirmed.data.evidenceKey, 'private-key')
})
test('fee waived bill cannot produce a cash refund application', async () => {
  storage = new Map(); env(o => o.url.endsWith('/refunds') ? o.success({ statusCode: 200, data: { code: 200, rows: [], total: 0 } }) : ok(o, { ...bill, payableAmount: '0.00', refundableAmount: '0.00', payments: [{ paymentId: '9', channel: 'FREE', amount: '0.00', paymentStatus: 'SUCCESS' }] }))
  const p = page('tuition-refund'); p.onLoad({ id: bill.enrollmentId }); await p.onShow(); p.setData({ amount: '1.00', reason: '退课' }); await p.apply(); assert.equal(calls.some(c => c.method === 'POST'), false)
})
test('home exposes parent finance routes and admin finance route without changing shop routes', () => {
  env(o => ok(o, {})); const p = page('index')
  p.applyLoginRole(); assert.ok(p.data.quickActions.some(a => a.route === '/pages/tuition-list/tuition-list')); assert.ok(p.data.quickActions.some(a => a.route === '/pages/my-coupons/my-coupons')); assert.ok(p.data.quickActions.some(a => a.route === '/pages/material/material'))
  wx.getStorageSync = key => key === 'token' ? 'jwt' : key === 'roles' ? ['admin'] : ''
  p.applyLoginRole(); assert.ok(p.data.quickActions.some(a => a.route === '/pages/admin-tuition/admin-tuition'))
})
test('enrollment detail always links to financial bill and normalizes cancelled Chinese status', () => {
  env(o => ok(o, {})); let navigated
  wx.navigateTo = o => { navigated = o.url }
  const p = page('enrollment'); const result = p.decorateEnrollments([{ enrollmentId: '9007199254740995', enrollmentStatus: '已取消' }]); assert.equal(result[0].statusText, '已取消')
  p.openTuition({ currentTarget: { dataset: { id: '9007199254740995' } } }); assert.equal(navigated, '/pages/tuition-detail/tuition-detail?id=9007199254740995')
})
test('only an unpaid current bill may cancel enrollment; paid, history and unknown are guarded', async () => {
  storage = new Map(); let current = bill
  env(o => ok(o, current))
  const p = page('tuition-detail'); p.onLoad({ id: bill.enrollmentId }); await p.onShow(); await p.cancelEnrollment()
  assert.ok(calls.some(c => c.method === 'DELETE' && c.url.endsWith('/miniapp/parent/enrollment/9007199254740995')))
  for (const blocked of [{ ...bill, payStatus: '已支付', payments: [{ paymentId: '9', paymentStatus: 'SUCCESS' }] }, { ...bill, billingStatus: 'HISTORY_PENDING' }, { ...bill, payments: [{ paymentId: '9', paymentStatus: 'UNKNOWN' }] }]) {
    current = blocked; await p.onShow(); const before = calls.filter(c => c.method === 'DELETE').length; await p.cancelEnrollment(); assert.equal(calls.filter(c => c.method === 'DELETE').length, before)
  }
})
test('lost payment confirmation is reconciled from status before showing success', async () => {
  storage = new Map(); let confirmed = false
  env(o => { if (o.url.endsWith('/quote')) ok(o, bill); else if (o.url.endsWith('/payments')) ok(o, { paymentId: '91', channel: 'MOCK', paymentStatus: 'PENDING' }); else if (o.url.endsWith('/mock-confirm')) { confirmed = true; o.fail({ errMsg: 'lost confirmation response' }) } else if (o.url.includes('/payments/')) ok(o, { paymentId: '91', paymentStatus: 'SUCCESS' }); else ok(o, confirmed ? { ...bill, receivedAmount: '900.00', dueAmount: '0.00', payments: [{ paymentId: '91', paymentStatus: 'SUCCESS', channel: 'MOCK' }] } : bill) })
  const p = page('tuition-detail'); p.onLoad({ id: bill.enrollmentId }); await p.onShow(); await p.pay(); assert.equal(p.data.detail.receivedText, '¥900.00'); assert.equal(p.data.detail.canPay, false); assert.equal(p.data.error, '')
})
test('leaving during payment confirmation never sends a financial mutation', async () => {
  storage = new Map(); let modal
  env(o => o.url.endsWith('/quote') ? ok(o, bill) : ok(o, bill)); wx.showModal = o => { modal = o }
  const p = page('tuition-detail'); p.onLoad({ id: bill.enrollmentId }); await p.onShow(); const paying = p.pay(); p.onHide(); modal.success({ confirm: true }); await paying
  assert.equal(calls.some(c => c.url.endsWith('/payments')), false)
})
test('returning after an abandoned confirmation restores actionable current page state', async () => {
  storage = new Map(); let modal
  env(o => ok(o, bill)); wx.showModal = o => { modal = o }
  const p = page('tuition-detail'); p.onLoad({ id: bill.enrollmentId }); await p.onShow(); const paying = p.pay(); p.onHide(); await p.onShow(); modal.success({ confirm: true }); await paying
  assert.equal(p.data.submitting, false); assert.equal(calls.some(c => c.url.endsWith('/payments')), false)
})
test('a superseded detail refresh cannot replace the newer financial state', async () => {
  storage = new Map(); const reads = []
  env(o => { if (o.url.endsWith('/quote')) ok(o, bill); else reads.push(o) })
  const p = page('tuition-detail'); p.onLoad({ id: bill.enrollmentId }); const first = p.onShow(); const newest = p.load()
  ok(reads[1], { ...bill, receivedAmount: '900.00', payments: [{ paymentId: '9', paymentStatus: 'SUCCESS' }] }); await newest; ok(reads[0], bill); await first
  assert.equal(p.data.detail.receivedText, '¥900.00'); assert.equal(p.data.detail.canPay, false)
})
test('malformed success payment response retains the original attempt without querying undefined ID', async () => {
  storage = new Map()
  env(o => { if (o.url.endsWith('/payments')) ok(o, {}); else ok(o, bill) })
  const p = page('tuition-detail'); p.onLoad({ id: bill.enrollmentId }); await p.onShow(); await p.pay()
  assert.equal(p.data.retrying, true); assert.match(p.data.error, /尚未确定/); assert.equal(calls.some(c => c.url.includes('/undefined')), false)
})
test('refund return after abandoned modal resets submission without posting an old application', async () => {
  storage = new Map(); let modal
  env(o => o.url.endsWith('/refunds') ? o.success({ statusCode: 200, data: { code: 200, rows: [], total: 0 } }) : ok(o, { ...bill, refundableAmount: '900.00', payments: [{ paymentId: '9', channel: 'MOCK', paymentStatus: 'SUCCESS' }] })); wx.showModal = o => { modal = o }
  const p = page('tuition-refund'); p.onLoad({ id: bill.enrollmentId }); await p.onShow(); p.setData({ amount: '100.00', reason: '原因' }); const applying = p.apply(); p.onHide(); await p.onShow(); modal.success({ confirm: true }); await applying
  assert.equal(p.data.submitting, false); assert.equal(calls.some(c => c.method === 'POST'), false)
})
test('admin returning after abandoned price modal resets busy state and rejects old mutation', async () => {
  storage = new Map(); let modal
  env(o => o.url.endsWith('/getInfo') ? o.success({ statusCode: 200, data: { code: 200, roles: ['admin'], permissions: ['*:*:*'] } }) : ok(o, bill), 'admin'); wx.showModal = o => { modal = o }
  const p = page('admin-tuition'); p.onLoad({ id: bill.enrollmentId }); await p.onShow(); p.setData({ price: '100.00' }); const pricing = p.savePrice(); p.onHide(); await p.onShow(); modal.success({ confirm: true }); await pricing
  assert.equal(p.data.submitting, false); assert.equal(calls.some(c => c.method === 'PUT'), false)
})
