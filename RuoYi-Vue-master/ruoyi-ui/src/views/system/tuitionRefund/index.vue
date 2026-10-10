<template>
  <div class="app-container">
    <el-alert
      title="开发阶段允许管理员审核本人申请，上线真实收款前须恢复双人复核。审核通过后仍需执行并核对实际到账；未知结果保留退费额度，先核对再处理。"
      type="warning"
      :closable="false"
      class="mb8"
    />
    <el-form v-show="showSearch" :model="queryParams" :inline="true" size="small">
      <el-form-item label="退费号">
        <el-input v-model="queryParams.refundNo" clearable />
      </el-form-item>
      <el-form-item label="报名ID">
        <el-input v-model="queryParams.enrollmentId" clearable />
      </el-form-item>
      <el-form-item label="学生">
        <el-input v-model="queryParams.studentName" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.refundStatus" clearable>
          <el-option v-for="s in statuses" :key="s" :value="s" :label="label(s)" />
        </el-select>
      </el-form-item>
      <el-form-item label="模式">
        <el-select v-model="queryParams.financeMode" @change="handleQuery">
          <el-option value="REAL" label="真实" />
          <el-option value="MOCK" label="模拟（本地）" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <el-row class="mb8">
      <el-button
        v-hasPermi="['system:tuitionRefund:apply']"
        size="mini"
        type="primary"
        plain
        @click="openApply"
      >
        代家长申请退费
      </el-button>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>
    <el-alert v-if="listError" :title="listError" type="error" :closable="false" />
    <el-table v-loading="loading" :data="rows">
      <el-table-column label="退费号" prop="refundNo" min-width="180" />
      <el-table-column label="学生" prop="studentName" width="100" />
      <el-table-column label="缴费号" prop="paymentNo" min-width="170" />
      <el-table-column label="类型" width="110">
        <template slot-scope="s">{{ label(s.row.refundKind) }}</template>
      </el-table-column>
      <el-table-column label="申请金额" width="110">
        <template slot-scope="s">{{ money(s.row.requestedAmount) }}</template>
      </el-table-column>
      <el-table-column label="批准金额" width="110">
        <template slot-scope="s">{{ money(s.row.approvedAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="180">
        <template slot-scope="s">
          <el-tag :type="statusType(s.row.refundStatus)">{{ label(s.row.refundStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="模式" width="80">
        <template slot-scope="s">{{ s.row.financeMode === 'MOCK' ? '模拟' : '真实' }}</template>
      </el-table-column>
      <el-table-column label="申请原因" prop="reason" min-width="160" />
      <el-table-column label="完成时间" prop="completedTime" width="165" />
      <el-table-column label="操作" fixed="right" width="100">
        <template slot-scope="s">
          <el-button
            v-hasPermi="['system:tuitionRefund:query']"
            type="text"
            @click="handleDetail(s.row)"
          >
            详情／处理
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination
      v-show="total > 0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />
    <el-dialog title="退费详情与审核依据" :visible.sync="detailOpen" width="850px" append-to-body>
      <div v-loading="detailLoading">
        <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" />
        <template v-if="detail">
          <el-alert
            v-if="detail.financeMode === 'MOCK'"
            title="本地模拟退费／不涉及真实资金"
            type="warning"
            :closable="false"
            class="mb8"
          />
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="退费号">{{ detail.refundNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              {{ label(detail.refundStatus) }}
            </el-descriptions-item>
            <el-descriptions-item label="学生／报名ID">
              {{ detail.studentName }}／{{ detail.enrollmentId }}
            </el-descriptions-item>
            <el-descriptions-item label="退费类型">
              {{ label(detail.refundKind) }}
            </el-descriptions-item>
            <el-descriptions-item label="原始实收">
              {{ money(detail.receivedAmount || detail.paymentAmount) }} 元
            </el-descriptions-item>
            <el-descriptions-item label="剩余可新申请">
              {{ money(detail.refundableAmount) }} 元
            </el-descriptions-item>
            <el-descriptions-item label="申请金额">
              {{ money(detail.requestedAmount) }} 元
            </el-descriptions-item>
            <el-descriptions-item label="批准金额">
              {{ money(detail.approvedAmount) }} 元
            </el-descriptions-item>
            <el-descriptions-item label="申请人">
              {{ detail.applicantName || detail.applicantId }}
              <div>{{ detail.createTime || '—' }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="审核人">
              {{ detail.reviewerName || detail.reviewerId || '—' }}
              <div>{{ detail.reviewTime || '—' }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="执行人">
              {{ detail.executorName || detail.executorId || '—' }}
              <div>{{ detail.processingTime || '—' }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="实际完成时间">
              {{ detail.completedTime || '尚未确认到账' }}
            </el-descriptions-item>
            <el-descriptions-item label="申请原因" :span="2">
              {{ detail.reason }}
            </el-descriptions-item>
            <el-descriptions-item label="审核计算说明" :span="2">
              {{ detail.reviewRemark || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="失败／未知原因" :span="2">
              {{ detail.failureReason || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="计算快照" :span="2">
              <pre class="snapshot">{{ snapshot(detail.calculationSnapshot) }}</pre>
            </el-descriptions-item>
          </el-descriptions>
          <div class="finance-actions">
            <el-button
              v-hasPermi="['system:tuitionRefund:review']"
              v-if="detail.refundStatus === 'PENDING_REVIEW'"
              :disabled="!canReview(detail, actorId)"
              type="primary"
              size="small"
              @click="openOperation('review')"
            >
              审核通过／驳回
            </el-button>
            <el-button
              v-hasPermi="['system:tuitionRefund:apply']"
              v-if="detail.refundStatus === 'PENDING_REVIEW' && String(detail.applicantId) === actorId"
              size="small"
              :disabled="saving"
              @click="cancel"
            >撤回申请</el-button>
            <el-button
              v-hasPermi="['system:tuitionRefund:execute']"
              v-if="detail.refundStatus === 'APPROVED'"
              size="small"
              @click="execute"
            >
              执行退费
            </el-button>
            <el-button
              v-hasPermi="['system:tuitionRefund:confirm']"
              v-if="['PROCESSING', 'UNKNOWN'].includes(detail.refundStatus)"
              size="small"
              type="primary"
              @click="openOperation('confirm')"
            >
              核对到账结果
            </el-button>
            <el-button
              v-hasPermi="['system:tuitionRefund:query']"
              size="small"
              @click="downloadEvidence"
            >
              退款凭证
            </el-button>
            <el-button size="small" @click="handleDetail(detail)">刷新</el-button>
          </div>
        </template>
      </div>
    </el-dialog>
    <el-dialog
      :title="
        operation === 'apply'
          ? '代家长申请退费'
          : operation === 'review'
          ? '审核退费（独立复核）'
          : '核对实际退款结果'
      "
      :visible.sync="operationOpen"
      width="620px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" class="mb8" />
      <el-form :model="form" label-width="125px" size="small" :disabled="saving">
        <template v-if="operation === 'apply'">
          <el-form-item label="原报名ID">
            <el-input v-model="form.enrollmentId" placeholder="填写报名ID后查询成功缴费">
              <el-button slot="append" :loading="billLoading" @click="loadBill">查询</el-button>
            </el-input>
          </el-form-item>
          <el-form-item label="原成功缴费">
            <el-select v-model="form.paymentId">
              <el-option
                v-for="p in successfulPayments"
                :key="p.paymentId"
                :value="p.paymentId"
                :label="p.paymentNo + '／实收 ' + money(p.amount) + ' 元'"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="可新申请金额">
            {{ bill ? money(bill.refundableAmount) + ' 元（后端最终核验）' : '请先查询报名' }}
          </el-form-item>
          <el-form-item label="退费类型">
            <el-radio-group v-model="form.refundKind">
              <el-radio label="PARTIAL">不退课</el-radio>
              <el-radio label="WITHDRAWAL">退课并退费</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="申请金额（元）">
            <el-input v-model="form.requestedAmount" />
          </el-form-item>
          <el-form-item label="申请原因">
            <el-input v-model="form.reason" type="textarea" maxlength="500" show-word-limit />
          </el-form-item>
        </template>
        <template v-if="operation === 'review'">
          <el-form-item label="审核决定">
            <el-radio-group v-model="form.approved">
              <el-radio :label="true">通过</el-radio>
              <el-radio :label="false">驳回</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.approved" label="批准金额（元）">
            <el-input v-model="form.approvedAmount" />
          </el-form-item>
          <el-form-item label="人工计算／说明">
            <el-input
              v-model="form.reviewRemark"
              type="textarea"
              maxlength="500"
              placeholder="结合合同、签到与实际授课核定；减少金额或驳回须说明"
              show-word-limit
            />
          </el-form-item>
        </template>
        <template v-if="operation === 'confirm'">
          <el-form-item label="实际结果">
            <el-select v-model="form.outcome">
              <el-option value="SUCCESS" label="已确认实际到账" />
              <el-option value="UNKNOWN" label="结果未知／保留额度" />
              <el-option value="FAILED" label="已确认没有退款" />
            </el-select>
          </el-form-item>
          <template v-if="form.outcome === 'SUCCESS'">
            <el-form-item label="实际到账时间">
              <el-date-picker
                v-model="form.completedTime"
                type="datetime"
                value-format="yyyy-MM-dd HH:mm:ss"
              />
            </el-form-item>
            <el-form-item label="完整退款流水号">
              <el-input
                v-model="form.providerRefundNo"
                maxlength="64"
                placeholder="现金可空，转账填写完整退款流水号"
              />
            </el-form-item>
            <el-form-item label="私有退款凭证">
              <el-upload
                action="#"
                :http-request="handleEvidenceUpload"
                :before-upload="beforeEvidence"
                :show-file-list="false"
                :disabled="uploading"
                accept=".pdf,.png,.jpg,.jpeg"
              >
                <el-button :loading="uploading" size="small">上传 PDF／图片（≤10 MB）</el-button>
              </el-upload>
              {{ form.evidenceKey ? '已上传，提交时绑定退款' : '请上传实际退款凭证' }}
            </el-form-item>
          </template>
          <el-form-item v-else label="核对情况／原因">
            <el-input v-model="form.reason" type="textarea" maxlength="255" />
          </el-form-item>
        </template>
      </el-form>
      <span slot="footer">
        <el-button :disabled="saving || uploading" @click="operationOpen = false">取消</el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="uploading || billLoading"
          @click="submitOperation"
        >
          提交
        </el-button>
      </span>
    </el-dialog>
  </div>
</template>
<script>
import {
  listRefunds,
  getRefund,
  applyRefund,
  reviewRefund,
  cancelRefund,
  executeRefund,
  confirmRefund,
  refundEvidence,
} from '@/api/system/tuitionRefund'
import { getTuition, uploadEvidence } from '@/api/system/tuition'
import {
  money,
  amount,
  compareAmount,
  required,
  label,
  statusType,
  requestKey,
  canReview,
  refundConfirmation,
  validateEvidenceFile,
  evidenceKey,
  checkedBlob,
} from '@/utils/tuitionFinance'
import { saveAs } from 'file-saver'
export default {
  name: 'TuitionRefund',
  data() {
    return {
      queryParams: { pageNum: 1, pageSize: 10, financeMode: 'REAL' },
      statuses: [
        'PENDING_REVIEW',
        'APPROVED',
        'PROCESSING',
        'UNKNOWN',
        'SUCCESS',
        'REJECTED',
        'CANCELLED',
        'FAILED',
      ],
      rows: [],
      total: 0,
      loading: false,
      listError: '',
      showSearch: true,
      detail: null,
      detailOpen: false,
      detailLoading: false,
      detailError: '',
      operation: '',
      operationOpen: false,
      form: {},
      formError: '',
      saving: false,
      uploading: false,
      bill: null,
      successfulPayments: [],
      billLoading: false,
      detailRequest: 0,
    }
  },
  computed: {
    actorId() {
      return String(this.$store.state.user.id)
    },
  },
  created() {
    this.getList()
    if (this.$route.query.paymentId) this.openApply()
  },
  watch: {
    '$route.query.paymentId'(id) {
      if (id) this.openApply()
    },
  },
  methods: {
    money,
    label,
    statusType,
    canReview,
    snapshot(value) {
      if (!value) return '—'
      if (typeof value !== 'string') return JSON.stringify(value, null, 2)
      try {
        return JSON.stringify(JSON.parse(value), null, 2)
      } catch (_) {
        return value
      }
    },
    async getList() {
      this.loading = true
      this.listError = ''
      try {
        const r = await listRefunds(this.queryParams)
        this.rows = r.rows || []
        this.total = r.total || 0
      } catch (e) {
        this.rows = []
        this.total = 0
        this.listError = e.message || '查询失败'
      } finally {
        this.loading = false
      }
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      return this.getList()
    },
    resetQuery() {
      this.queryParams = { pageNum: 1, pageSize: 10, financeMode: 'REAL' }
      return this.getList()
    },
    async handleDetail(row) {
      const sequence = ++this.detailRequest
      this.detail = null
      this.detailOpen = true
      this.detailError = ''
      this.detailLoading = true
      try {
        const r = await getRefund(String(row.refundId))
        if (sequence === this.detailRequest) this.detail = r.data
      } catch (e) {
        if (sequence === this.detailRequest) this.detailError = e.message || '详情查询失败'
      } finally {
        if (sequence === this.detailRequest) this.detailLoading = false
      }
    },
    async openApply() {
      this.operation = 'apply'
      this.operationOpen = true
      this.formError = ''
      this.bill = null
      this.successfulPayments = []
      this.form = {
        enrollmentId: this.$route.query.enrollmentId || '',
        paymentId: this.$route.query.paymentId || '',
        refundKind: 'PARTIAL',
        requestedAmount: '',
        reason: '',
        idempotencyKey: requestKey(),
      }
      if (this.form.enrollmentId) await this.loadBill()
    },
    async loadBill() {
      this.billLoading = true
      this.bill = null
      this.successfulPayments = []
      this.formError = ''
      try {
        const id = required(this.form.enrollmentId, '原报名ID')
        const r = await getTuition(id)
        this.bill = r.data
        this.successfulPayments = (r.data.payments || []).filter(
          (p) => p.paymentStatus === 'SUCCESS' && compareAmount(p.amount, '0') > 0
        )
        if (!this.successfulPayments.some((p) => p.paymentId === this.form.paymentId))
          this.form.paymentId =
            this.successfulPayments.length === 1 ? this.successfulPayments[0].paymentId : ''
        if (!this.successfulPayments.length)
          throw new Error('没有可退现金的成功缴费，请通过报名取消流程办理无现金退课')
      } catch (e) {
        this.formError = e.message || '查询失败'
      } finally {
        this.billLoading = false
      }
    },
    openOperation(operation) {
      this.operation = operation
      this.operationOpen = true
      this.formError = ''
      this.form =
        operation === 'review'
          ? { approved: true, approvedAmount: this.detail.requestedAmount, reviewRemark: '' }
          : {
              outcome: 'SUCCESS',
              completedTime: '',
              providerRefundNo: '',
              evidenceKey: '',
              reason: '',
            }
    },
    beforeEvidence(file) {
      try {
        return validateEvidenceFile(file)
      } catch (e) {
        this.formError = e.message
        return false
      }
    },
    async handleEvidenceUpload(options) {
      this.uploading = true
      this.formError = ''
      try {
        const r = await uploadEvidence(options.file, 'REFUND', this.detail.refundId)
        this.$set(this.form, 'evidenceKey', evidenceKey(r))
        options.onSuccess(r)
      } catch (e) {
        this.formError = e.message || '上传失败'
        options.onError(e)
      } finally {
        this.uploading = false
      }
    },
    async submitOperation() {
      if (this.saving || this.uploading || this.billLoading) return
      this.saving = true
      this.formError = ''
      try {
        if (this.operation === 'apply') {
          if (
            !this.bill ||
            String(this.bill.enrollmentId) !== String(this.form.enrollmentId) ||
            !this.successfulPayments.some((p) => p.paymentId === this.form.paymentId)
          )
            throw new Error('请先查询并选择原成功缴费')
          const requestedAmount = amount(this.form.requestedAmount, true)
          if (compareAmount(requestedAmount, this.bill.refundableAmount) > 0)
            throw new Error('申请超过剩余可新申请额度')
          await applyRefund({
            paymentId: this.form.paymentId,
            refundKind: this.form.refundKind,
            requestedAmount,
            reason: required(this.form.reason, '申请原因'),
            idempotencyKey: this.form.idempotencyKey,
          })
        }
        if (this.operation === 'review') {
          if (!canReview(this.detail, String(this.$store.state.user.id)))
            throw new Error('仅待审核申请允许审核，请刷新状态')
          const approvedAmount = this.form.approved ? amount(this.form.approvedAmount, true) : null
          if (this.form.approved && compareAmount(approvedAmount, this.detail.requestedAmount) > 0)
            throw new Error('批准金额不能超过申请金额')
          if (!this.form.approved || compareAmount(approvedAmount, this.detail.requestedAmount) < 0)
            required(this.form.reviewRemark, '减少金额或驳回的说明')
          await reviewRefund(this.detail.refundId, {
            approved: this.form.approved,
            approvedAmount,
            reviewRemark: this.form.reviewRemark || '',
          })
        }
        if (this.operation === 'confirm')
          await confirmRefund(
            this.detail.refundId,
            refundConfirmation(this.form, this.detail.channel)
          )
        this.operationOpen = false
        this.$modal.msgSuccess('已提交，请核对最新退费状态')
        await this.getList()
        if (this.detail) await this.handleDetail(this.detail)
      } catch (e) {
        this.formError = e.message || '提交失败，请核对后重试'
      } finally {
        this.saving = false
      }
    },
    async cancel() {
      if (this.saving || !this.detail || this.detail.refundStatus !== 'PENDING_REVIEW' ||
          String(this.detail.applicantId) !== this.actorId) return
      this.saving = true
      try {
        await this.$modal.confirm('确认撤回本人创建的待审核退费申请？撤回不会退款。')
        await cancelRefund(this.detail.refundId)
        this.$modal.msgSuccess('申请已撤回')
        await Promise.all([this.getList(), this.handleDetail(this.detail)])
      } catch (e) {
        if (e && e.message) this.$modal.msgError(e.message)
      } finally {
        this.saving = false
      }
    },
    async execute() {
      if (this.saving) return
      this.saving = true
      try {
        await this.$modal.confirm(
          this.detail.financeMode === 'MOCK'
            ? '本地模拟退费，不涉及真实资金。确认执行？'
            : '确认开始办理线下退款？执行后须另行登记实际到账凭证。'
        )
        await executeRefund(this.detail.refundId)
        await Promise.all([this.getList(), this.handleDetail(this.detail)])
      } catch (e) {
        if (e && e.message) this.$modal.msgError(e.message)
      } finally {
        this.saving = false
      }
    },
    async downloadEvidence() {
      try {
        saveAs(
          await checkedBlob(await refundEvidence(this.detail.refundId)),
          '退款凭证_' + this.detail.refundNo
        )
      } catch (e) {
        this.$modal.msgError(e.message || '下载失败')
      }
    },
  },
}
</script>
<style scoped>
.finance-actions {
  margin-top: 16px;
}
.snapshot {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
