const { test, before } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '..')
let finance = {}
before(async () => {
  const file = path.join(root, 'src/utils/tuitionFinance.js')
  if (fs.existsSync(file))
    finance = await import(
      'data:text/javascript;base64,' + fs.readFileSync(file).toString('base64')
    )
})
test('unknown historical money stays unknown and cents never silently round', () => {
  assert.equal(typeof finance.money, 'function')
  assert.equal(finance.money(null), '待核验')
  assert.equal(finance.money('0'), '0.00')
  assert.equal(finance.money('99999999.99'), '99999999.99')
  assert.equal(finance.money('199999999.98'), '199999999.98')
  assert.equal(finance.money('1.001'), '金额异常')
  assert.equal(finance.amount('12.3'), '12.30')
  assert.throws(() => finance.amount('1.001'), /两位/)
  assert.throws(() => finance.amount('-1'), /金额/)
  assert.throws(() => finance.amount('0', true), /大于零/)
})
test('unknown or pending payment reserves the bill and blocks new collection and repricing', () => {
  assert.equal(typeof finance.canCollect, 'function')
  const bill = { billingStatus: 'OPEN', financeMode: 'REAL', payments: [] }
  assert.equal(finance.canCollect(bill), true)
  for (const status of ['UNKNOWN', 'PENDING', 'SUCCESS', 'UNRECOGNIZED']) {
    assert.equal(finance.canCollect({ ...bill, payments: [{ paymentStatus: status }] }), false)
    assert.equal(finance.canPrice({ ...bill, payments: [{ paymentStatus: status }] }), false)
  }
  assert.equal(finance.canCollect({ ...bill, billingStatus: 'HISTORY_PENDING' }), false)
  assert.equal(finance.canPrice({ ...bill, billingStatus: 'PENDING_PRICE' }), true)
  assert.equal(finance.canClosePayment({ paymentStatus: 'UNKNOWN' }), false)
})
test('authorized review UI permits self-review but still requires a pending request', () => {
  assert.equal(typeof finance.canReview, 'function')
  const refund = {
    refundStatus: 'PENDING_REVIEW',
    applicantId: '9007199254740993',
    financeMode: 'REAL',
  }
  assert.equal(finance.canReview(refund, '9007199254740993'), true)
  assert.equal(finance.canReview(refund, '9007199254740994'), true)
  assert.equal(
    finance.canReview({ ...refund, financeMode: 'MOCK', mockAllowed: true }, refund.applicantId),
    true
  )
  assert.equal(
    finance.canReview({ ...refund, financeMode: 'MOCK', mockAllowed: false }, refund.applicantId),
    true
  )
  assert.equal(finance.canReview({ ...refund, applicantId: null }, '2'), false)
  for (const refundStatus of ['APPROVED', 'PROCESSING', 'UNKNOWN', 'SUCCESS', 'CANCELLED']) {
    assert.equal(finance.canReview({ ...refund, refundStatus }, refund.applicantId), false)
  }
})
test('evidence upload accepts only small PDF/image files and validated private keys', () => {
  assert.equal(typeof finance.validateEvidenceFile, 'function')
  assert.equal(finance.validateEvidenceFile({ name: '凭证.pdf', size: 1024 }), true)
  assert.throws(() => finance.validateEvidenceFile({ name: 'x.html', size: 100 }), /PDF/)
  assert.throws(
    () => finance.validateEvidenceFile({ name: 'x.png', size: 10 * 1024 * 1024 + 1 }),
    /10/
  )
  assert.equal(
    finance.evidenceKey({ code: 200, data: { evidenceKey: 'private-token' } }),
    'private-token'
  )
  assert.throws(() => finance.evidenceKey({ code: 500, msg: '上传失败' }), /上传失败/)
})
test('refund evidence is mandatory for success and unknown outcome cannot mean success', () => {
  assert.equal(typeof finance.refundConfirmation, 'function')
  assert.throws(
    () => finance.refundConfirmation({ outcome: 'SUCCESS', completedTime: '2026-10-07 10:00:00' }),
    /凭证/
  )
  assert.deepEqual(
    finance.refundConfirmation({
      outcome: 'UNKNOWN',
      reason: '银行结果待核对',
      approvedAmount: '99',
    }),
    { outcome: 'UNKNOWN', reason: '银行结果待核对' }
  )
  assert.throws(() => finance.refundConfirmation({ outcome: 'FAILED', reason: '' }), /原因/)
  assert.deepEqual(
    finance.refundConfirmation({
      outcome: 'SUCCESS',
      evidenceKey: 'key',
      completedTime: '2026-10-07 10:00:00',
      providerRefundNo: 'B001',
    }),
    {
      outcome: 'SUCCESS',
      evidenceKey: 'key',
      completedTime: '2026-10-07 10:00:00',
      providerRefundNo: 'B001',
    }
  )
  assert.deepEqual(
    finance.refundConfirmation(
      { outcome: 'SUCCESS', evidenceKey: 'cash-proof', completedTime: '2026-10-07 10:00:00' },
      'CASH'
    ),
    {
      outcome: 'SUCCESS',
      evidenceKey: 'cash-proof',
      completedTime: '2026-10-07 10:00:00',
      providerRefundNo: null,
    }
  )
})
test('batch grant deduplicates exact long IDs, limits 100 and preserves a retry key', () => {
  assert.equal(typeof finance.grantBody, 'function')
  assert.deepEqual(
    finance.grantBody({
      parentIds: ['9007199254740993', '9007199254740993', '9007199254740994'],
      reason: '活动',
      idempotencyKey: 'same-key',
    }),
    {
      parentIds: ['9007199254740993', '9007199254740994'],
      reason: '活动',
      idempotencyKey: 'same-key',
    }
  )
  assert.throws(
    () => finance.grantBody({ parentIds: [], reason: '活动', idempotencyKey: 'same-key' }),
    /家长/
  )
  assert.throws(
    () =>
      finance.grantBody({
        parentIds: Array.from({ length: 101 }, (_, i) => String(i + 1)),
        reason: '活动',
        idempotencyKey: 'same-key',
      }),
    /100/
  )
})
test('authenticated PDF download rejects JSON failures instead of saving an error file', async () => {
  assert.equal(typeof finance.checkedBlob, 'function')
  const pdf = new Blob(['%PDF-1.7'], { type: 'application/pdf' })
  assert.equal(await finance.checkedBlob(pdf), pdf)
  await assert.rejects(
    finance.checkedBlob(
      new Blob(['{"code":403,"msg":"没有下载权限"}'], { type: 'application/json' })
    ),
    /没有下载权限/
  )
})
function component(name, dependencies = {}) {
  const filename = path.join(root, 'src/views/system', name, 'index.vue')
  assert.ok(fs.existsSync(filename), name + ' component missing')
  const script = stripImports(
    fs.readFileSync(filename, 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
  ).replace('export default', 'component =')
  const context = vm.createContext({ component: null, ...finance, ...dependencies })
  vm.runInContext(script, context)
  const definition = context.component
  const instance = {
    ...definition.data(),
    $modal: { msgSuccess() {}, msgError() {}, confirm: () => Promise.resolve() },
    $store: { state: { user: { id: '2' } } },
    $route: { query: {} },
    ...dependencies,
  }
  for (const [name, method] of Object.entries(definition.methods))
    instance[name] = method.bind(instance)
  return instance
}
function stripImports(source) {
  const tree = require('@babel/parser').parse(source, { sourceType: 'module' })
  for (const node of tree.program.body.filter((n) => n.type === 'ImportDeclaration').reverse()) {
    source = source.slice(0, node.start) + source.slice(node.end)
  }
  return source
}
test('collection submits backend quote unchanged and reuses idempotency after network failure', async () => {
  const sent = []
  const instance = component('tuition', {
    createPayment: async (id, body) => {
      sent.push({ id, body: JSON.parse(JSON.stringify(body)) })
      throw new Error('网络中断')
    },
  })
  instance.detail = { enrollmentId: '9007199254740993', billingStatus: 'OPEN', payments: [] }
  instance.quote = {
    payableAmount: '900.00',
    financialVersion: 3,
    userCouponId: '9007199254740994',
  }
  instance.operation = 'collect'
  instance.form = {
    payerName: '家长',
    channel: 'CASH',
    paidTime: '2026-10-07 10:00:00',
    evidenceKey: 'key',
    idempotencyKey: 'same-key',
    expectedPayableAmount: '1.00',
  }
  await instance.submitOperation()
  await instance.submitOperation()
  assert.equal(sent.length, 2)
  assert.equal(sent[0].id, '9007199254740993')
  assert.equal(sent[0].body.expectedPayableAmount, '900.00')
  assert.equal(sent[0].body.idempotencyKey, sent[1].body.idempotencyKey)
  assert.equal(instance.saving, false)
  assert.match(instance.formError, /网络中断/)
})
test('private detail clears stale data after denied request', async () => {
  const instance = component('tuition', {
    getTuition: () => Promise.reject(new Error('没有查询权限')),
  })
  instance.detail = { enrollmentId: '1', contactPhone: 'private' }
  await instance.handleDetail({ enrollmentId: '2' })
  assert.equal(instance.detail, null)
  assert.equal(instance.detailLoading, false)
  assert.match(instance.detailError, /没有查询权限/)
})
test('tuition summary uses server totals for selected mode, never sums the current page', async () => {
  let mode
  const instance = component('tuition', {
    listTuition: async () => ({
      rows: [{ enrollmentId: '1', receivedAmount: '100.00' }],
      total: 20,
    }),
    getTuitionSummary: async (query) => {
      mode = query.financeMode
      return {
        data: {
          receivedAmount: '999.00',
          refundedAmount: '200.00',
          netAmount: '799.00',
          dueAmount: '50.00',
          todayReceivedAmount: '100.00',
          todayRefundedAmount: '0.00',
          historyPendingCount: 4,
        },
      }
    },
  })
  instance.queryParams.financeMode = 'MOCK'
  await instance.getList()
  assert.equal(mode, 'MOCK')
  assert.equal(instance.summary.receivedAmount, '999.00')
  assert.equal(instance.summary.netAmount, '799.00')
  assert.equal(instance.rows.length, 1)
  assert.equal(instance.summary.historyPendingCount, 4)
})
test('a denied summary clears old totals while preserving an independently authorized list', async () => {
  const instance = component('tuition', {
    listTuition: async () => ({ rows: [{ enrollmentId: '1' }], total: 1 }),
    getTuitionSummary: async () => {
      throw new Error('没有汇总权限')
    },
  })
  instance.summary = { receivedAmount: 'private-total' }
  await instance.getList()
  assert.equal(instance.summary, null)
  assert.match(instance.summaryError, /汇总权限/)
  assert.equal(instance.rows.length, 1)
})
test('receipt replacement requires a reason and keeps failed operation visible', async () => {
  let writes = 0
  const instance = component('tuitionReceipt', {
    replaceReceipt: () => {
      writes++
      return Promise.reject(new Error('请刷新后重试'))
    },
  })
  instance.form = { receiptId: '9007199254740993', reason: '' }
  await instance.submitReplace()
  assert.equal(writes, 0)
  instance.form.reason = '抬头更正'
  instance.replaceOpen = true
  await instance.submitReplace()
  assert.equal(writes, 1)
  assert.equal(instance.replaceOpen, true)
  assert.equal(instance.saving, false)
})
test('real self-review submits but approval reduction still requires explanation', async () => {
  let writes = 0
  const instance = component('tuitionRefund', {
    reviewRefund: async () => {
      writes++
    },
  })
  instance.detail = {
    refundId: '9007199254740993',
    applicantId: '2',
    financeMode: 'REAL',
    refundStatus: 'PENDING_REVIEW',
    requestedAmount: '100.00',
  }
  instance.operation = 'review'
  instance.getList = async () => {}
  instance.handleDetail = async () => {}
  instance.form = { approved: true, approvedAmount: '100.00', reviewRemark: '' }
  await instance.submitOperation()
  assert.equal(writes, 1)
  instance.detail.applicantId = '3'
  instance.form.approvedAmount = '90.00'
  await instance.submitOperation()
  assert.equal(writes, 1)
  assert.match(instance.formError, /说明/)
})
test('admin withdrawal posts exact refund ID and refreshes only after confirmation', async () => {
  const sent = []
  const instance = component('tuitionRefund', {
    cancelRefund: async (id) => sent.push(id),
  })
  instance.detail = { refundId: '9007199254740993', applicantId: '2', refundStatus: 'PENDING_REVIEW' }
  instance.actorId = '2'
  instance.getList = async () => {}
  instance.handleDetail = async () => {}
  await instance.cancel()
  assert.deepEqual(sent, ['9007199254740993'])
  instance.detail.refundStatus = 'PROCESSING'
  await instance.cancel()
  assert.equal(sent.length, 1)
  instance.detail.refundStatus = 'PENDING_REVIEW'
  instance.$modal.confirm = () => Promise.reject()
  await instance.cancel()
  assert.equal(sent.length, 1)
  assert.equal(instance.saving, false)
})
test('coupon grants preserve selected parents and same batch key after failed request', async () => {
  const sent = []
  const instance = component('coupon', {
    grantCoupon: async (id, body) => {
      sent.push({ id, body: JSON.parse(JSON.stringify(body)) })
      throw new Error('断网')
    },
  })
  instance.grantTemplate = { templateId: '9007199254740993' }
  instance.grantForm = {
    parentIds: ['9007199254740994'],
    reason: '补偿',
    idempotencyKey: 'same-batch',
  }
  instance.grantOpen = true
  await instance.submitGrant()
  await instance.submitGrant()
  assert.equal(sent.length, 2)
  assert.equal(sent[0].body.parentIds[0], '9007199254740994')
  assert.equal(sent[0].body.idempotencyKey, sent[1].body.idempotencyKey)
  assert.equal(instance.grantOpen, true)
  assert.equal(instance.saving, false)
})
test('old enrollment editor strips finance fields while financial link preserves long ID', async () => {
  let body, route
  const instance = component('enrollment', {
    updateEnrollment: async (data) => {
      body = data
    },
    $router: {
      push: (value) => {
        route = value
      },
    },
  })
  instance.$refs = { form: { validate: (callback) => callback(true) } }
  instance.form = {
    enrollmentId: '9007199254740993',
    studentName: '学生',
    payStatus: '已支付',
    billingStatus: 'OPEN',
    originalAmount: '1.00',
    financialVersion: 1,
  }
  instance.getList = () => {}
  instance.submitForm()
  await Promise.resolve()
  assert.equal('payStatus' in body, false)
  assert.equal('originalAmount' in body, false)
  instance.handleFinance({ enrollmentId: '9007199254740993' })
  assert.equal(route.query.enrollmentId, '9007199254740993')
})
test('zero backend quote submits FREE without inventing cash collection', async () => {
  let saved
  const instance = component('tuition', {
    createPayment: async (_, data) => {
      saved = data
    },
  })
  instance.detail = { enrollmentId: '1', billingStatus: 'OPEN', payments: [] }
  instance.quote = {
    originalAmount: '100.00',
    discountAmount: '100.00',
    payableAmount: '0.00',
    financialVersion: 1,
    userCouponId: '2',
  }
  instance.operation = 'collect'
  instance.form = { payerName: '家长', channel: 'CASH', idempotencyKey: 'free-key' }
  instance.getList = instance.handleDetail = async () => {}
  await instance.submitOperation()
  assert.equal(saved.channel, 'FREE')
  assert.equal(saved.expectedPayableAmount, '0.00')
  assert.equal(saved.evidenceKey, null)
})
test('closing an UNKNOWN payment is blocked before any write', async () => {
  let writes = 0
  const instance = component('tuition', {
    closePayment: async () => {
      writes++
    },
  })
  instance.operation = 'close'
  instance.detail = { enrollmentId: '1' }
  instance.payment = { paymentId: '2', paymentStatus: 'UNKNOWN' }
  instance.form = { reason: '重新登记' }
  await instance.submitOperation()
  assert.equal(writes, 0)
  assert.match(instance.formError, /核对/)
})
test('local allowed MOCK bill sends only MOCK channel without real evidence', async () => {
  let saved
  const instance = component('tuition', {
    createPayment: async (_, data) => {
      saved = data
    },
  })
  instance.detail = {
    enrollmentId: '1',
    billingStatus: 'OPEN',
    financeMode: 'MOCK',
    mockAllowed: true,
    payments: [],
  }
  instance.quote = { payableAmount: '900.00', financialVersion: 3 }
  instance.operation = 'collect'
  instance.form = { payerName: '演示家长', channel: 'CASH', idempotencyKey: 'mock-key' }
  instance.getList = instance.handleDetail = async () => {}
  await instance.submitOperation()
  assert.ok(saved, 'mock collection should not require real evidence')
  assert.equal(saved.channel, 'MOCK')
  assert.equal(saved.evidenceKey, null)
})
test('pricing a real bill into MOCK requires explicit local pricing capability', async () => {
  let writes = 0
  const instance = component('tuition', {
    priceTuition: async () => {
      writes++
    },
  })
  instance.detail = {
    enrollmentId: '1',
    billingStatus: 'PENDING_PRICE',
    financeMode: 'REAL',
    financialVersion: 1,
    payments: [],
  }
  instance.operation = 'price'
  instance.form = { originalAmount: '100.00', financeMode: 'MOCK' }
  instance.getList = instance.handleDetail = async () => {}
  await instance.submitOperation()
  assert.equal(writes, 0)
  instance.detail.mockPricingAllowed = true
  await instance.submitOperation()
  assert.equal(writes, 1)
})
test('pending real confirmation uses already bound evidence without requiring another upload', async () => {
  let saved
  const instance = component('tuition', {
    confirmPayment: async (_, data) => {
      saved = data
    },
  })
  instance.detail = { enrollmentId: '1', financeMode: 'REAL' }
  instance.payment = {
    paymentId: '2',
    channel: 'CASH',
    paymentStatus: 'PENDING',
    evidenceKey: 'bound-file',
    paidTime: '2026-10-07 10:00:00',
  }
  instance.operation = 'confirm'
  instance.form = { paidTime: '2026-10-07 10:00:00', evidenceKey: '', providerTradeNo: '' }
  instance.getList = instance.handleDetail = async () => {}
  await instance.submitOperation()
  assert.ok(saved, 'bound evidence should remain usable')
  assert.equal(saved.evidenceKey, null)
})
test('coupon parent query normalizes authorized parent names and search fields', async () => {
  let query
  const instance = component('coupon', {
    listParents: async (params) => {
      query = params
      return {
        rows: [{ parentId: '9007199254740993', parentName: '家长', userName: 'parent' }],
        total: 1,
      }
    },
  })
  instance.parentQuery.search = '家长'
  await instance.getParents()
  assert.equal(query.search, '家长')
  assert.equal(query.q, '家长')
  assert.equal(instance.parents[0].userId, '9007199254740993')
  assert.equal(instance.parents[0].nickName, '家长')
})
test('issued coupon template ignores rule edits and sends only allowed status', async () => {
  let saved
  const instance = component('coupon', {
    editTemplate: async (id, body) => {
      saved = { id, body }
    },
  })
  instance.form = {
    templateId: '9007199254740993',
    issuedQuantity: 1,
    discountAmount: '1.00',
    templateStatus: 'PAUSED',
  }
  instance.getList = async () => {}
  await instance.submitTemplate()
  assert.equal(saved.id, '9007199254740993')
  assert.deepEqual(JSON.parse(JSON.stringify(saved.body)), { templateStatus: 'PAUSED' })
})
function api(name, request) {
  const calls = []
  const source = fs
    .readFileSync(path.join(root, 'src/api/system', name + '.js'), 'utf8')
    .replace(/^import .*$/gm, '')
    .replace(/export function /g, 'function ')
  const context = vm.createContext({
    FormData,
    request:
      request ||
      (async (config) => {
        calls.push(config)
        return { code: 200 }
      }),
  })
  vm.runInContext(source, context)
  return { calls, context }
}
test('finance API binds evidence to exact business long ID using multipart and authenticated request', async () => {
  const { calls, context } = api('tuition')
  await context.uploadEvidence(
    new Blob(['evidence'], { type: 'application/pdf' }),
    'HISTORY',
    '9007199254740993'
  )
  const config = calls[0]
  assert.equal(config.url, '/system/tuition/evidence')
  assert.equal(config.data.get('businessId'), '9007199254740993')
  assert.equal(config.data.get('businessType'), 'HISTORY')
  assert.equal(config.headers.isToken, undefined)
  assert.equal(config.headers['Content-Type'], 'multipart/form-data')
  await context.paymentEvidence('9007199254740993')
  assert.equal(calls[1].url, '/system/tuition/payments/9007199254740993/evidence')
  assert.equal(calls[1].responseType, 'blob')
})
test('receipt and refund use dedicated routes and blob download retains authentication', async () => {
  const receipt = api('tuitionReceipt')
  await receipt.context.receiptFile('9007199254740993')
  await receipt.context.replaceReceipt('9007199254740993', '抬头更正')
  assert.equal(receipt.calls[0].responseType, 'blob')
  assert.equal(receipt.calls[0].url, '/system/tuition/receipts/9007199254740993/file')
  assert.equal(receipt.calls[0].headers && receipt.calls[0].headers.isToken, undefined)
  assert.deepEqual(JSON.parse(JSON.stringify(receipt.calls[1].data)), { reason: '抬头更正' })
  const refund = api('tuitionRefund')
  await refund.context.confirmRefund('9007199254740993', { outcome: 'UNKNOWN', reason: '待核对' })
  assert.equal(refund.calls[0].url, '/system/tuition/refunds/9007199254740993/confirm')
  assert.deepEqual(JSON.parse(JSON.stringify(refund.calls[0].data)), {
    outcome: 'UNKNOWN',
    reason: '待核对',
  })
})
test('receipt download actually passes Bearer through the existing Axios interceptor', async () => {
  const axios = require('axios')
  const source = stripImports(fs.readFileSync(path.join(root, 'src/utils/request.js'), 'utf8'))
    .replace('export let isRelogin', 'let isRelogin')
    .replace('export function download', 'function download')
    .replace('export default service', 'serviceResult = service')
  const context = vm.createContext({
    serviceResult: null,
    axios,
    process: { env: { VUE_APP_BASE_API: '/api' } },
    getToken: () => 'finance-test-token',
    tansParams: () => '',
    cache: { session: { getJSON() {}, setJSON() {} } },
    Notification: { error() {} },
    Message() {},
    MessageBox: {},
    errorCode: {},
    store: {},
    console,
  })
  vm.runInContext(source, context)
  let authorization, target
  context.serviceResult.defaults.adapter = async (config) => {
    authorization = config.headers.Authorization
    target = config.url
    return {
      status: 200,
      statusText: 'OK',
      config,
      headers: {},
      request: { responseType: config.responseType },
      data: new Blob(['%PDF-1.7'], { type: 'application/pdf' }),
    }
  }
  const receipt = api('tuitionReceipt', context.serviceResult)
  const blob = await receipt.context.receiptFile('9007199254740993')
  assert.equal(authorization, 'Bearer finance-test-token')
  assert.equal(target, '/system/tuition/receipts/9007199254740993/file')
  assert.equal(blob.type, 'application/pdf')
})
test('every financial template and JavaScript compiles using the installed Vue2 toolchain', () => {
  const compiler = require('vue-template-compiler')
  const parser = require('@babel/parser')
  for (const name of ['tuition', 'tuitionReceipt', 'tuitionRefund', 'coupon', 'enrollment']) {
    const sfc = compiler.parseComponent(
      fs.readFileSync(path.join(root, 'src/views/system', name, 'index.vue'), 'utf8')
    )
    const result = compiler.compile(sfc.template.content)
    assert.deepEqual(result.errors, [], name + ' template errors')
    parser.parse(sfc.script.content, { sourceType: 'module', plugins: ['objectRestSpread'] })
  }
})
