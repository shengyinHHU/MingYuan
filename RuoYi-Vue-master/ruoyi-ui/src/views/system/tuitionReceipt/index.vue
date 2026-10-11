<template>
  <div class="app-container">
    <el-alert
      title="收据记录原始实收，不随退费改写；作废补开只更正凭据，不改变收款。模拟收据不涉及真实资金。"
      type="info"
      :closable="false"
      class="mb8"
    />
    <el-form v-show="showSearch" :model="queryParams" :inline="true" size="small">
      <el-form-item label="收据号">
        <el-input v-model="queryParams.receiptNo" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="报名ID">
        <el-input v-model="queryParams.enrollmentId" clearable />
      </el-form-item>
      <el-form-item label="学生">
        <el-input v-model="queryParams.studentName" clearable />
      </el-form-item>
      <el-form-item label="有效状态">
        <el-select v-model="queryParams.receiptStatus" clearable>
          <el-option value="ISSUED" label="有效" />
          <el-option value="VOID" label="作废" />
        </el-select>
      </el-form-item>
      <el-form-item label="文件状态">
        <el-select v-model="queryParams.fileStatus" clearable>
          <el-option
            v-for="s in ['PENDING', 'GENERATING', 'READY', 'FAILED']"
            :key="s"
            :value="s"
            :label="s === 'PENDING' ? '待生成' : label(s)"
          />
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
        v-hasPermi="['system:tuitionReceipt:issue']"
        type="primary"
        plain
        size="mini"
        @click="
          issueOpen = true
          issueError = ''
          paymentId = ''
        "
      >
        按成功缴费开具
      </el-button>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>
    <el-alert v-if="listError" :title="listError" type="error" :closable="false" />
    <el-table v-loading="loading" :data="rows">
      <el-table-column label="收据号" prop="receiptNo" min-width="180" />
      <el-table-column label="报名ID" prop="enrollmentId" min-width="120" />
      <el-table-column label="缴费ID" prop="paymentId" min-width="120" />
      <el-table-column label="学生" prop="studentName" width="100" />
      <el-table-column label="凭据类型" width="150">
        <template slot-scope="s">{{ label(s.row.documentType) }}</template>
      </el-table-column>
      <el-table-column label="金额（元）" width="120">
        <template slot-scope="s">{{ money(s.row.amount) }}</template>
      </el-table-column>
      <el-table-column label="模式" width="95">
        <template slot-scope="s">
          <el-tag :type="s.row.financeMode === 'MOCK' ? 'warning' : 'info'">
            {{ s.row.financeMode === 'MOCK' ? '模拟' : '真实' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="有效状态" width="95">
        <template slot-scope="s">
          <el-tag :type="statusType(s.row.receiptStatus)">{{ label(s.row.receiptStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="文件状态" width="130">
        <template slot-scope="s">
          <el-tag :type="statusType(s.row.fileStatus)">
            {{ s.row.fileStatus === 'PENDING' ? '待生成' : label(s.row.fileStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开具时间" prop="issuedTime" width="165" />
      <el-table-column label="生成失败原因" prop="generationError" min-width="160" />
      <el-table-column label="作废原因／替代原件ID" min-width="165">
        <template slot-scope="s">{{ s.row.voidReason || s.row.replacesReceiptId || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="245">
        <template slot-scope="s">
          <el-button
            v-hasPermi="['system:tuitionReceipt:download']"
            :disabled="s.row.fileStatus !== 'READY' || s.row.receiptStatus !== 'ISSUED'"
            type="text"
            size="mini"
            @click="downloadFile(s.row)"
          >
            下载／打印 PDF
          </el-button>
          <el-button
            v-hasPermi="['system:tuitionReceipt:issue']"
            v-if="
              s.row.receiptStatus === 'ISSUED' &&
              ['PENDING', 'FAILED', 'GENERATING'].includes(s.row.fileStatus)
            "
            :disabled="saving"
            type="text"
            size="mini"
            @click="retry(s.row)"
          >
            重试生成
          </el-button>
          <el-button
            v-hasPermi="['system:tuitionReceipt:replace']"
            v-if="s.row.receiptStatus === 'ISSUED'"
            type="text"
            size="mini"
            @click="openReplace(s.row)"
          >
            作废补开
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
    <el-dialog
      title="按成功缴费开具／获取收据"
      :visible.sync="issueOpen"
      width="480px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-alert v-if="issueError" :title="issueError" type="error" :closable="false" />
      <el-form label-width="100px">
        <el-form-item label="成功缴费ID">
          <el-input v-model="paymentId" placeholder="填写原始成功缴费ID" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button :disabled="saving" @click="issueOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitIssue">开具</el-button>
      </span>
    </el-dialog>
    <el-dialog
      title="作废并补开收据"
      :visible.sync="replaceOpen"
      width="520px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-alert
        title="旧号码、文件与金额快照保留，新收据引用旧原件。"
        type="warning"
        :closable="false"
        class="mb8"
      />
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" />
      <el-form label-width="90px">
        <el-form-item label="原收据号">{{ form.receiptNo }}</el-form-item>
        <el-form-item label="更正原因">
          <el-input v-model="form.reason" type="textarea" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button :disabled="saving" @click="replaceOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReplace">确认作废补开</el-button>
      </span>
    </el-dialog>
  </div>
</template>
<script>
import {
  listReceipts,
  issueReceipt,
  retryReceipt,
  replaceReceipt,
  receiptFile,
} from '@/api/system/tuitionReceipt'
import { money, label, statusType, required, checkedBlob } from '@/utils/tuitionFinance'
import { saveAs } from 'file-saver'
export default {
  name: 'TuitionReceipt',
  data() {
    return {
      loading: false,
      rows: [],
      total: 0,
      listError: '',
      showSearch: true,
      queryParams: { pageNum: 1, pageSize: 10, financeMode: 'REAL' },
      issueOpen: false,
      issueError: '',
      paymentId: '',
      replaceOpen: false,
      form: {},
      formError: '',
      saving: false,
    }
  },
  created() {
    if (this.$route.query.enrollmentId)
      this.queryParams.enrollmentId = this.$route.query.enrollmentId
    this.getList()
  },
  methods: {
    money,
    label,
    statusType,
    async getList() {
      this.loading = true
      this.listError = ''
      try {
        const r = await listReceipts(this.queryParams)
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
    async submitIssue() {
      if (this.saving) return
      this.saving = true
      this.issueError = ''
      try {
        await issueReceipt(required(this.paymentId, '成功缴费ID'))
        this.issueOpen = false
        this.$modal.msgSuccess('已开具或返回原有效收据')
        await this.getList()
      } catch (e) {
        this.issueError = e.message || '开具失败'
      } finally {
        this.saving = false
      }
    },
    async retry(row) {
      if (this.saving) return
      this.saving = true
      try {
        await retryReceipt(row.receiptId)
        this.$modal.msgSuccess('已重试原快照，收据号码保留')
        await this.getList()
      } catch (e) {
        this.$modal.msgError(e.message || '重试失败')
      } finally {
        this.saving = false
      }
    },
    openReplace(row) {
      this.form = { receiptId: row.receiptId, receiptNo: row.receiptNo, reason: '' }
      this.formError = ''
      this.replaceOpen = true
    },
    async submitReplace() {
      if (this.saving) return
      this.saving = true
      this.formError = ''
      try {
        const reason = required(this.form.reason, '更正原因')
        await this.$modal.confirm('确认作废此收据并开具替代收据？收款和退款金额不会改变。')
        await replaceReceipt(this.form.receiptId, reason)
        this.replaceOpen = false
        this.$modal.msgSuccess('已作废并补开')
        await this.getList()
      } catch (e) {
        if (e && e.message) this.formError = e.message
      } finally {
        this.saving = false
      }
    },
    async downloadFile(row) {
      try {
        saveAs(await checkedBlob(await receiptFile(row.receiptId)), row.receiptNo + '.pdf')
      } catch (e) {
        this.$modal.msgError(e.message || '下载失败')
      }
    },
  },
}
</script>
