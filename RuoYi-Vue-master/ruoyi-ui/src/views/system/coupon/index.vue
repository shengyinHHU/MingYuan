<template>
  <div class="app-container">
    <el-alert
      title="现金抵扣券仅用于课程；每模板每家长一张，每批最多100人，单批成功或失败。全选超过100人时分批发放。已发模板规则冻结，暂停只停止新发放。"
      type="info"
      :closable="false"
      class="mb8"
    />
    <el-tabs v-model="activeTab" @tab-click="loadTab">
      <el-tab-pane label="券模板" name="templates">
        <el-form :inline="true" :model="queryParams" size="small">
          <el-form-item label="券名称">
            <el-input v-model="queryParams.couponName" clearable />
          </el-form-item>
          <el-form-item label="模板状态">
            <el-select v-model="queryParams.templateStatus" clearable>
              <el-option
                v-for="s in ['DRAFT', 'ACTIVE', 'PAUSED']"
                :key="s"
                :label="label(s)"
                :value="s"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" @click="handleQuery">搜索</el-button>
            <el-button @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <el-row class="mb8">
          <el-button
            v-hasPermi="['system:coupon:add']"
            type="primary"
            plain
            size="mini"
            icon="el-icon-plus"
            @click="openTemplate()"
          >
            新建模板
          </el-button>
          <el-button size="mini" icon="el-icon-refresh" @click="getList">刷新</el-button>
        </el-row>
        <el-alert v-if="listError" :title="listError" type="error" :closable="false" />
        <el-table v-loading="loading" :data="rows">
          <el-table-column label="模板编号" prop="templateCode" min-width="150" />
          <el-table-column label="券名称" prop="couponName" min-width="130" />
          <el-table-column label="面额（元）" width="110">
            <template slot-scope="s">{{ money(s.row.discountAmount) }}</template>
          </el-table-column>
          <el-table-column label="原价门槛（元）" width="130">
            <template slot-scope="s">{{ money(s.row.minSpendAmount) }}</template>
          </el-table-column>
          <el-table-column label="课程范围" width="130">
            <template slot-scope="s">{{ s.row.scopeScheduleId || '全部课程' }}</template>
          </el-table-column>
          <el-table-column label="有效时间" width="180">
            <template slot-scope="s">
              {{ s.row.validFrom }}
              <br />
              {{ s.row.validUntil }}
            </template>
          </el-table-column>
          <el-table-column label="已发／总量" width="100">
            <template slot-scope="s">
              {{ s.row.issuedQuantity || 0 }}／{{ s.row.totalQuantity }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template slot-scope="s">
              <el-tag :type="statusType(s.row.templateStatus)">
                {{ label(s.row.templateStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" prop="description" min-width="130" />
          <el-table-column label="操作" width="250" fixed="right">
            <template slot-scope="s">
              <el-button
                v-hasPermi="['system:coupon:edit']"
                type="text"
                @click="openTemplate(s.row)"
              >
                {{ s.row.issuedQuantity > 0 ? '状态' : '编辑' }}
              </el-button>
              <el-button
                v-hasPermi="['system:coupon:edit']"
                :disabled="saving"
                type="text"
                @click="toggleTemplate(s.row)"
              >
                {{ s.row.templateStatus === 'ACTIVE' ? '暂停' : '启用' }}
              </el-button>
              <el-button
                v-hasPermi="['system:coupon:grant']"
                :disabled="
                  parentSelectionLocked() ||
                  s.row.templateStatus !== 'ACTIVE' ||
                  s.row.issuedQuantity >= s.row.totalQuantity
                "
                type="text"
                @click="openGrant(s.row)"
              >
                批量发券
              </el-button>
              <el-button
                v-hasPermi="['system:coupon:query']"
                type="text"
                @click="showGrants(s.row)"
              >
                发放记录
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
      </el-tab-pane>
      <el-tab-pane label="发放记录" name="grants">
        <el-form :inline="true" :model="grantQuery" size="small">
          <el-form-item label="模板ID">
            <el-input v-model="grantQuery.templateId" clearable />
          </el-form-item>
          <el-form-item label="家长ID">
            <el-input v-model="grantQuery.parentId" clearable />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="grantQuery.couponStatus" clearable>
              <el-option
                v-for="s in ['AVAILABLE', 'LOCKED', 'USED', 'REVOKED']"
                :key="s"
                :value="s"
                :label="label(s)"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              icon="el-icon-search"
              @click="
                grantQuery.pageNum = 1
                getGrants()
              "
            >
              搜索
            </el-button>
            <el-button @click="resetGrants">重置</el-button>
          </el-form-item>
        </el-form>
        <el-alert v-if="grantError" :title="grantError" type="error" :closable="false" />
        <el-table v-loading="grantsLoading" :data="grants">
          <el-table-column label="券编号" prop="couponCode" min-width="180" />
          <el-table-column label="券名称" prop="couponName" min-width="130" />
          <el-table-column label="家长" width="130">
            <template slot-scope="s">
              {{ s.row.parentName }}
              <br />
              {{ s.row.parentId }}
            </template>
          </el-table-column>
          <el-table-column label="抵扣／门槛（元）" width="150">
            <template slot-scope="s">
              {{ money(s.row.discountAmount) }}／{{ money(s.row.minSpendAmount) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template slot-scope="s">
              <el-tag :type="statusType(s.row.couponStatus)">
                {{ label(s.row.displayStatus || s.row.couponStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="有效至" prop="validUntil" width="165" />
          <el-table-column label="锁定／使用报名ID" width="140">
            <template slot-scope="s">
              {{ s.row.lockedEnrollmentId || s.row.usedEnrollmentId || '—' }}
            </template>
          </el-table-column>
          <el-table-column label="发放原因" prop="grantReason" min-width="130" />
          <el-table-column label="撤回原因" prop="revokeReason" min-width="130" />
          <el-table-column label="操作" width="95">
            <template slot-scope="s">
              <el-button
                v-hasPermi="['system:coupon:revoke']"
                :disabled="s.row.couponStatus !== 'AVAILABLE'"
                type="text"
                @click="openRevoke(s.row)"
              >
                撤回
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <pagination
          v-show="grantTotal > 0"
          :total="grantTotal"
          :page.sync="grantQuery.pageNum"
          :limit.sync="grantQuery.pageSize"
          @pagination="getGrants"
        />
      </el-tab-pane>
    </el-tabs>
    <el-dialog
      :title="form.templateId ? '编辑优惠券模板' : '建立优惠券模板'"
      :visible.sync="templateOpen"
      width="570px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" class="mb8" />
      <el-alert
        v-if="form.issuedQuantity > 0"
        title="已有发放，仅可修改启用／暂停状态；规则变更请新建模板。"
        type="warning"
        :closable="false"
        class="mb8"
      />
      <el-form :model="form" label-width="120px" size="small" :disabled="saving">
        <div>
          <el-form-item label="券名称">
            <el-input
              v-model="form.couponName"
              :disabled="form.issuedQuantity > 0"
              maxlength="100"
            />
          </el-form-item>
          <el-form-item label="使用说明">
            <el-input
              v-model="form.description"
              :disabled="form.issuedQuantity > 0"
              type="textarea"
              maxlength="500"
            />
          </el-form-item>
          <el-form-item label="抵扣面额（元）">
            <el-input v-model="form.discountAmount" :disabled="form.issuedQuantity > 0" />
          </el-form-item>
          <el-form-item label="原价门槛（元）">
            <el-input v-model="form.minSpendAmount" :disabled="form.issuedQuantity > 0" />
          </el-form-item>
          <el-form-item label="指定排课ID">
            <el-input
              v-model="form.scopeScheduleId"
              :disabled="form.issuedQuantity > 0"
              placeholder="留空表示全部课程"
            />
          </el-form-item>
          <el-form-item label="生效时间">
            <el-date-picker
              v-model="form.validFrom"
              :disabled="form.issuedQuantity > 0"
              type="datetime"
              value-format="yyyy-MM-dd HH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="到期时间">
            <el-date-picker
              v-model="form.validUntil"
              :disabled="form.issuedQuantity > 0"
              type="datetime"
              value-format="yyyy-MM-dd HH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="发行总量">
            <el-input-number
              v-model="form.totalQuantity"
              :disabled="form.issuedQuantity > 0"
              :min="1"
              :max="2147483647"
              :precision="0"
            />
          </el-form-item>
        </div>
        <el-form-item label="模板状态">
          <el-select v-model="form.templateStatus">
            <el-option
              v-for="s in ['DRAFT', 'ACTIVE', 'PAUSED']"
              :key="s"
              :value="s"
              :label="label(s)"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button :disabled="saving" @click="templateOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitTemplate">保存</el-button>
      </span>
    </el-dialog>
    <el-dialog
      title="给现有家长账号批量发券"
      :visible.sync="grantOpen"
      width="810px"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="!saving && !selectingAll"
      :show-close="!saving && !selectingAll"
    >
      <el-alert
        :title="
          '模板：' +
          (grantTemplate ? grantTemplate.couponName : '') +
          '；每批最多100人，失败后从未确认批次重试并保留原名单及标识。'
        "
        type="info"
        :closable="false"
        class="mb8"
      />
      <el-alert
        v-if="grantFormError"
        :title="grantFormError"
        type="error"
        :closable="false"
        class="mb8"
      />
      <el-form :inline="true" size="small" :disabled="parentSelectionLocked()">
        <el-form-item label="查找家长">
          <el-input
            v-model="parentQuery.search"
            maxlength="64"
            placeholder="账号／姓名"
            @input="parentFiltersChanged"
            @keyup.enter.native="searchParents"
          />
        </el-form-item>
        <el-form-item label="报名年级">
          <el-input
            v-model="parentQuery.gradeName"
            maxlength="20"
            clearable
            placeholder="如：五年级"
            @input="parentFiltersChanged"
          />
        </el-form-item>
        <el-form-item label="学科">
          <el-input
            v-model="parentQuery.subjectName"
            maxlength="20"
            clearable
            placeholder="如：英语"
            @input="parentFiltersChanged"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchParents">查询</el-button>
          <el-button :loading="selectingAll" @click="selectAllParents"
            >一键全选筛选结果</el-button
          >
          <el-button @click="clearParentSelection">清空选择</el-button>
        </el-form-item>
      </el-form>
      <div class="mb8">
        表头勾选：全选当前页；跨页选择会保留。“一键全选筛选结果”覆盖全部匹配家长。
      </div>
      <fieldset
        :disabled="parentSelectionLocked()"
        style="border: 0; margin: 0; padding: 0"
      >
        <el-table
          ref="parentsTable"
          v-loading="parentsLoading"
          :data="parents"
          :row-key="parentRowKey"
          @selection-change="selectParents"
        >
          <el-table-column
            type="selection"
            :reserve-selection="true"
            :selectable="parentSelectable"
            width="55"
          />
          <el-table-column label="家长ID" prop="userId" />
          <el-table-column label="账号" prop="userName" />
          <el-table-column label="姓名" prop="nickName" />
          <el-table-column label="命中孩子／报名年级／学科" min-width="240">
            <template slot-scope="s">
              <div v-for="child in s.row.matchedChildren || []" :key="child.enrollmentId">
                {{ child.studentName }}／{{ child.gradeName }}／{{ child.subjectName }}
              </div>
            </template>
          </el-table-column>
        </el-table>
      </fieldset>
      <pagination
        v-show="parentTotal > 0 && !parentSelectionLocked()"
        :total="parentTotal"
        :page.sync="parentQuery.pageNum"
        :limit.sync="parentQuery.pageSize"
        @pagination="getParents"
      />
      <el-form label-width="85px" size="small" :disabled="parentSelectionLocked()">
        <el-form-item label="已选家长">
          {{ grantForm.parentIds.length }} 人；报名年级：{{
            parentQuery.gradeName || '全部'
          }}；学科：{{ parentQuery.subjectName || '全部' }}；家长：{{
            parentQuery.search || '全部'
          }}
        </el-form-item>
        <el-form-item label="发放原因">
          <el-input v-model="grantForm.reason" type="textarea" maxlength="255" />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="grantBatch"
        :title="grantProgress()"
        type="warning"
        :closable="false"
        class="mb8"
      />
      <span slot="footer">
        <el-button :disabled="saving || selectingAll" @click="grantOpen = false"
          >关闭</el-button
        >
        <el-button v-if="grantBatch" :disabled="saving" @click="cancelGrantBatch"
          >放弃原批次并重新选择</el-button
        >
        <el-button
          type="primary"
          :loading="saving"
          :disabled="parentsLoading || selectingAll"
          @click="submitGrant"
        >
          {{ grantBatch ? '重试未确认批次' : '确认分批发放' }}
        </el-button>
      </span>
    </el-dialog>
    <el-dialog
      title="撤回未使用优惠券"
      :visible.sync="revokeOpen"
      width="480px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-alert v-if="revokeError" :title="revokeError" type="error" :closable="false" />
      <el-form label-width="90px">
        <el-form-item label="券编号">{{ revokeForm.couponCode }}</el-form-item>
        <el-form-item label="撤回原因">
          <el-input v-model="revokeForm.reason" type="textarea" maxlength="255" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button :disabled="saving" @click="revokeOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRevoke">撤回</el-button>
      </span>
    </el-dialog>
  </div>
</template>
<script>
import {
  listTemplates,
  addTemplate,
  editTemplate,
  grantCoupon,
  listGrants,
  revokeCoupon,
  listParents,
} from '@/api/system/coupon'
import {
  money,
  amount,
  label,
  statusType,
  required,
  requestKey,
  grantBody,
} from '@/utils/tuitionFinance'
export default {
  name: 'Coupon',
  data() {
    return {
      activeTab: 'templates',
      queryParams: { pageNum: 1, pageSize: 10 },
      rows: [],
      total: 0,
      loading: false,
      listError: '',
      templateOpen: false,
      form: {},
      formError: '',
      saving: false,
      grantQuery: { pageNum: 1, pageSize: 10 },
      grants: [],
      grantTotal: 0,
      grantsLoading: false,
      grantError: '',
      grantOpen: false,
      grantTemplate: null,
      grantForm: { parentIds: [], reason: '', idempotencyKey: '' },
      grantFormError: '',
      parents: [],
      parentTotal: 0,
      parentQuery: {
        pageNum: 1,
        pageSize: 10,
        search: '',
        gradeName: '',
        subjectName: '',
      },
      parentsLoading: false,
      selectingAll: false,
      parentRequest: 0,
      parentFilterSnapshot: '',
      syncingParentSelection: false,
      grantBatch: null,
      revokeOpen: false,
      revokeForm: {},
      revokeError: '',
    }
  },
  created() {
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
        const r = await listTemplates(this.queryParams)
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
      this.queryParams = { pageNum: 1, pageSize: 10 }
      return this.getList()
    },
    loadTab() {
      if (this.activeTab === 'grants') this.getGrants()
      else this.getList()
    },
    async getGrants() {
      this.grantsLoading = true
      this.grantError = ''
      try {
        const r = await listGrants(this.grantQuery)
        this.grants = r.rows || []
        this.grantTotal = r.total || 0
      } catch (e) {
        this.grants = []
        this.grantTotal = 0
        this.grantError = e.message || '查询失败'
      } finally {
        this.grantsLoading = false
      }
    },
    resetGrants() {
      this.grantQuery = { pageNum: 1, pageSize: 10 }
      return this.getGrants()
    },
    showGrants(row) {
      this.grantQuery = { pageNum: 1, pageSize: 10, templateId: row.templateId }
      this.activeTab = 'grants'
      return this.getGrants()
    },
    openTemplate(row) {
      this.form = row
        ? { ...row }
        : {
            couponName: '',
            description: '',
            discountAmount: '',
            minSpendAmount: '0.00',
            scopeScheduleId: '',
            validFrom: '',
            validUntil: '',
            totalQuantity: 1,
            templateStatus: 'DRAFT',
            issuedQuantity: 0,
          }
      this.formError = ''
      this.templateOpen = true
    },
    async submitTemplate() {
      if (this.saving) return
      this.saving = true
      this.formError = ''
      try {
        let body = { templateStatus: this.form.templateStatus }
        if (!(this.form.issuedQuantity > 0)) {
          const validFrom = required(this.form.validFrom, '生效时间')
          const validUntil = required(this.form.validUntil, '到期时间')
          if (validUntil <= validFrom) throw new Error('到期时间必须晚于生效时间')
          body = {
            ...body,
            couponName: required(this.form.couponName, '券名称'),
            description: this.form.description || '',
            discountAmount: amount(this.form.discountAmount, true),
            minSpendAmount: amount(this.form.minSpendAmount),
            scopeScheduleId: this.form.scopeScheduleId
              ? String(this.form.scopeScheduleId).trim()
              : null,
            validFrom,
            validUntil,
            totalQuantity: this.form.totalQuantity,
          }
        }
        if (this.form.templateId) await editTemplate(this.form.templateId, body)
        else await addTemplate(body)
        this.templateOpen = false
        this.$modal.msgSuccess('模板已保存')
        await this.getList()
      } catch (e) {
        this.formError = e.message || '保存失败'
      } finally {
        this.saving = false
      }
    },
    async toggleTemplate(row) {
      if (this.saving) return
      this.saving = true
      try {
        const templateStatus = row.templateStatus === 'ACTIVE' ? 'PAUSED' : 'ACTIVE'
        await this.$modal.confirm(
          '确认' + label(templateStatus) + '此模板？已发优惠券继续保留原规则。'
        )
        await editTemplate(row.templateId, { templateStatus })
        await this.getList()
      } catch (e) {
        if (e && e.message) this.$modal.msgError(e.message)
      } finally {
        this.saving = false
      }
    },
    async openGrant(row) {
      if (this.parentSelectionLocked()) {
        if (this.grantBatch) {
          this.grantOpen = true
          this.grantFormError = '原批次尚未完成；请重试，或核对历史发放后明确放弃原批次。'
        }
        return
      }
      this.grantTemplate = row
      this.grantForm = {
        parentIds: [],
        reason: '',
        idempotencyKey: requestKey(),
      }
      this.grantFormError = ''
      this.grantOpen = true
      this.parentQuery = {
        pageNum: 1,
        pageSize: 10,
        search: '',
        gradeName: '',
        subjectName: '',
      }
      this.parentFiltersChanged()
      await this.getParents()
    },
    parentRowKey(row) {
      return String(row.userId || row.parentId)
    },
    selectParents(selection) {
      if (this.parentSelectionLocked() || this.syncingParentSelection) return
      const current = new Set(this.parents.map(this.parentRowKey))
      this.grantForm.parentIds = [
        ...new Set([
          ...this.grantForm.parentIds.filter((id) => !current.has(id)),
          ...selection.map(this.parentRowKey),
        ]),
      ]
    },
    parentSelectionLocked() {
      return this.saving || this.selectingAll || !!this.grantBatch
    },
    parentSelectable() {
      return !this.parentSelectionLocked()
    },
    parentFilters() {
      return {
        search: String(this.parentQuery.search || '').trim(),
        gradeName: String(this.parentQuery.gradeName || '').trim(),
        subjectName: String(this.parentQuery.subjectName || '').trim(),
      }
    },
    parentFilterKey() {
      return JSON.stringify(this.parentFilters())
    },
    clearParentSelection() {
      if (this.parentSelectionLocked()) return
      this.grantForm.parentIds = []
      this.syncingParentSelection = true
      if (this.$refs && this.$refs.parentsTable) this.$refs.parentsTable.clearSelection()
      this.syncingParentSelection = false
    },
    parentFiltersChanged() {
      if (this.parentSelectionLocked()) return
      this.parentRequest++
      this.clearParentSelection()
      this.parents = []
      this.parentTotal = 0
      this.parentsLoading = false
      this.parentQuery.pageNum = 1
      this.parentFilterSnapshot = this.parentFilterKey()
    },
    syncParentSelection() {
      if (!this.$refs || !this.$refs.parentsTable) return
      this.syncingParentSelection = true
      const table = this.$refs.parentsTable
      table.clearSelection()
      for (const row of this.parents)
        if (this.grantForm.parentIds.includes(this.parentRowKey(row)))
          table.toggleRowSelection(row, true)
      this.syncingParentSelection = false
    },
    searchParents() {
      if (this.parentSelectionLocked()) return
      this.parentQuery.pageNum = 1
      return this.getParents()
    },
    async getParents() {
      if (this.parentSelectionLocked()) return
      const filterKey = this.parentFilterKey()
      if (filterKey !== this.parentFilterSnapshot) this.parentFiltersChanged()
      const request = ++this.parentRequest
      this.parentsLoading = true
      this.grantFormError = ''
      try {
        const r = await listParents({
          ...this.parentQuery,
          q: this.parentQuery.search,
        })
        if (request !== this.parentRequest || filterKey !== this.parentFilterKey()) return
        this.parents = (r.rows || []).map((p) => ({
          ...p,
          userId: String(p.userId || p.parentId),
          nickName: p.nickName || p.parentName,
        }))
        this.parentTotal = r.total || 0
        if (this.$nextTick) await this.$nextTick()
        this.syncParentSelection()
      } catch (e) {
        if (request !== this.parentRequest) return
        this.clearParentSelection()
        this.parents = []
        this.parentTotal = 0
        this.grantFormError = e.message || '家长查询失败'
      } finally {
        if (request === this.parentRequest) this.parentsLoading = false
      }
    },
    async selectAllParents() {
      if (this.parentSelectionLocked()) return
      this.clearParentSelection()
      const filters = this.parentFilters(),
        filterKey = this.parentFilterKey()
      const request = ++this.parentRequest
      this.selectingAll = true
      this.grantFormError = ''
      try {
        const ids = new Set()
        let total = null
        for (let pageNum = 1; ; pageNum++) {
          const r = await listParents({
            ...filters,
            q: filters.search,
            pageNum,
            pageSize: 100,
          })
          if (request !== this.parentRequest || filterKey !== this.parentFilterKey())
            throw new Error('筛选已改变，请重新查询并全选')
          if (total === null) total = Number(r.total || 0)
          if (Number(r.total || 0) !== total)
            throw new Error('候选名单已改变，请重新查询并全选')
          for (const row of r.rows || []) ids.add(this.parentRowKey(row))
          if (pageNum * 100 >= total) break
          if (!(r.rows || []).length) throw new Error('候选名单不完整，请重新查询并全选')
        }
        if (ids.size !== total) throw new Error('候选名单已改变，请重新查询并全选')
        this.grantForm.parentIds = [...ids]
        this.parentFilterSnapshot = filterKey
        this.parentTotal = total
        this.syncParentSelection()
      } catch (e) {
        this.grantForm.parentIds = []
        this.parents = []
        this.parentTotal = 0
        this.grantFormError = e.message || '全选失败，请重新查询'
      } finally {
        this.selectingAll = false
        this.parentsLoading = false
      }
    },
    grantProgress() {
      if (!this.grantBatch) return ''
      const batch = this.grantBatch
      const completed = batch.batches
        .slice(0, batch.index)
        .reduce((n, body) => n + body.parentIds.length, 0)
      return (
        '已完成 ' +
        completed +
        ' 人；未确认 ' +
        (batch.total - completed) +
        ' 人。重试保留原名单、模板、原因和批次标识。'
      )
    },
    async cancelGrantBatch() {
      if (this.saving || this.selectingAll || !this.grantBatch) return
      try {
        await this.$modal.confirm(
          '放弃重试前请核对历史发放记录；未确认批次可能已成功。确认放弃原批次并重新选择？'
        )
        this.grantBatch = null
        this.grantForm.idempotencyKey = requestKey()
        this.clearParentSelection()
        this.grantFormError = ''
      } catch (_) {
        /* Keep the frozen batch when cancellation is dismissed. */
      }
    },
    async submitGrant() {
      if (this.saving || this.selectingAll || this.parentsLoading) return
      this.saving = true
      this.grantFormError = ''
      try {
        if (!this.grantBatch) {
          const parentIds = [...new Set(this.grantForm.parentIds.map(String))]
          if (!parentIds.length) throw new Error('请选择现有家长账号')
          const filters = this.parentFilters()
          const key = this.grantForm.idempotencyKey || requestKey()
          const batches = []
          for (let i = 0; i < parentIds.length; i += 100)
            batches.push({
              ...grantBody({
                ...this.grantForm,
                parentIds: parentIds.slice(i, i + 100),
                idempotencyKey:
                  parentIds.length <= 100 ? key : key.slice(0, 55) + '-' + (i / 100 + 1),
              }),
              gradeName: filters.gradeName,
              subjectName: filters.subjectName,
            })
          const snapshot = {
            templateId: this.grantTemplate.templateId,
            batches,
            index: 0,
            total: parentIds.length,
          }
          await this.$modal.confirm(
            '确认使用模板“' +
              (this.grantTemplate.couponName || snapshot.templateId) +
              '”给 ' +
              parentIds.length +
              ' 位家长分 ' +
              batches.length +
              ' 批发放？报名年级：' +
              (filters.gradeName || '全部') +
              '；学科：' +
              (filters.subjectName || '全部') +
              '；原因：' +
              batches[0].reason +
              '。任一批次失败将停止后续发放。'
          )
          this.grantBatch = snapshot
        }
        const batch = this.grantBatch
        while (batch.index < batch.batches.length) {
          await grantCoupon(batch.templateId, batch.batches[batch.index])
          batch.index++
        }
        this.grantBatch = null
        this.grantOpen = false
        this.$modal.msgSuccess('已完成 ' + batch.total + ' 位家长的全部发放')
        await this.getList()
      } catch (e) {
        if (e && e.message)
          this.grantFormError =
            e.message + (this.grantBatch ? '；' + this.grantProgress() : '')
      } finally {
        this.saving = false
      }
    },
    openRevoke(row) {
      this.revokeForm = { userCouponId: row.userCouponId, couponCode: row.couponCode, reason: '' }
      this.revokeError = ''
      this.revokeOpen = true
    },
    async submitRevoke() {
      if (this.saving) return
      this.saving = true
      this.revokeError = ''
      try {
        await revokeCoupon(
          this.revokeForm.userCouponId,
          required(this.revokeForm.reason, '撤回原因')
        )
        this.revokeOpen = false
        this.$modal.msgSuccess('优惠券已撤回，发行总量不恢复')
        await this.getGrants()
      } catch (e) {
        this.revokeError = e.message || '撤回失败'
      } finally {
        this.saving = false
      }
    },
  },
}
</script>
