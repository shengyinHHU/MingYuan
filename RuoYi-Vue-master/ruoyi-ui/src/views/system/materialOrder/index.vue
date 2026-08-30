<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="订单编码" prop="orderCode">
        <el-input
          v-model="queryParams.orderCode"
          placeholder="请输入订单编码"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="资料标题" prop="materialTitle">
        <el-input
          v-model="queryParams.materialTitle"
          placeholder="请输入资料标题"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="家长昵称" prop="parentName">
        <el-input
          v-model="queryParams.parentName"
          placeholder="请输入家长昵称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="支付状态" prop="payStatus">
        <el-select v-model="queryParams.payStatus" placeholder="请选择状态" clearable>
          <el-option label="未支付" value="0" />
          <el-option label="已支付" value="1" />
          <el-option label="已退款" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="支付时间" style="width: 240px;">
        <el-date-picker
          v-model="dateRange"
          style="width: 100%;"
          value-format="yyyy-MM-dd"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        ></el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete()"
          v-hasPermi="['system:materialOrder:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['system:materialOrder:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="materialOrderList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="订单ID" align="center" prop="orderId" width="80" />
      <el-table-column label="订单编码" align="center" prop="orderCode" width="160" />
      <el-table-column label="资料标题" align="center" prop="materialTitle" :show-overflow-tooltip="true" />
      <el-table-column label="家长昵称" align="center" prop="parentName" width="100" />
      <el-table-column label="金额" align="center" width="100">
        <template slot-scope="scope">
          <span style="color: #E6A23C; font-weight: 600;">¥{{ scope.row.amount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="支付方式" align="center" width="80">
        <template slot-scope="scope">
          <span>{{ scope.row.payWay === '1' ? '微信' : '模拟' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="支付状态" align="center" width="90">
        <template slot-scope="scope">
          <el-tag :type="getPayStatusType(scope.row.payStatus)">
            {{ getPayStatusText(scope.row.payStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="支付时间" align="center" prop="payTime" width="160" />
      <el-table-column label="微信交易号" align="center" prop="wxTransactionId" width="180" :show-overflow-tooltip="true" />
      <el-table-column label="退款原因" align="center" prop="refundReason" :show-overflow-tooltip="true" />
      <el-table-column label="退款时间" align="center" prop="refundTime" width="160" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="160" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="140">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-view"
            @click="handleDetail(scope.row)"
          >详情</el-button>
          <el-button
            v-if="scope.row.payStatus === '1'"
            size="mini"
            type="text"
            icon="el-icon-refresh-left"
            style="color: #E6A23C;"
            @click="handleRefund(scope.row)"
            v-hasPermi="['system:materialOrder:edit']"
          >退款</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog title="订单详情" :visible.sync="detailOpen" width="600px" append-to-body>
      <el-descriptions :column="2" border v-if="detailRow">
        <el-descriptions-item label="订单编码">{{ detailRow.orderCode }}</el-descriptions-item>
        <el-descriptions-item label="支付状态">
          <el-tag :type="getPayStatusType(detailRow.payStatus)">{{ getPayStatusText(detailRow.payStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="资料标题">{{ detailRow.materialTitle }}</el-descriptions-item>
        <el-descriptions-item label="家长昵称">{{ detailRow.parentName }}</el-descriptions-item>
        <el-descriptions-item label="金额">
          <span style="color: #E6A23C; font-weight: 600;">¥{{ detailRow.amount }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="支付方式">{{ detailRow.payWay === '1' ? '微信' : '模拟' }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ detailRow.payTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="微信交易号">{{ detailRow.wxTransactionId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="退款时间">{{ detailRow.refundTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="退款原因">{{ detailRow.refundReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ detailRow.createTime }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer" class="dialog-footer">
        <el-button @click="detailOpen = false">关 闭</el-button>
      </div>
    </el-dialog>

    <el-dialog title="订单退款" :visible.sync="refundOpen" width="500px" append-to-body>
      <el-form ref="refundForm" :model="refundForm" :rules="refundRules" label-width="100px">
        <el-form-item label="订单编码">
          <el-input v-model="refundForm.orderCode" disabled />
        </el-form-item>
        <el-form-item label="退款原因" prop="refundReason">
          <el-input v-model="refundForm.refundReason" type="textarea" :rows="3" placeholder="请输入退款原因" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitRefund">确认退款</el-button>
        <el-button @click="refundOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listMaterialOrder, getMaterialOrder, delMaterialOrder, updateMaterialOrder } from "@/api/system/materialOrder"

export default {
  name: "MaterialOrder",
  data() {
    return {
      loading: true,
      ids: [],
      multiple: true,
      showSearch: true,
      total: 0,
      materialOrderList: [],
      dateRange: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        orderCode: null,
        materialTitle: null,
        parentName: null,
        payStatus: null
      },
      detailOpen: false,
      detailRow: null,
      refundOpen: false,
      refundForm: {},
      refundRules: {
        refundReason: [
          { required: true, message: "请输入退款原因", trigger: "blur" }
        ]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listMaterialOrder(this.addDateRange(this.queryParams, this.dateRange)).then(response => {
        this.materialOrderList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    getPayStatusType(status) {
      const map = { '0': 'info', '1': 'success', '2': 'warning' }
      return map[status] || 'info'
    },
    getPayStatusText(status) {
      const map = { '0': '未支付', '1': '已支付', '2': '已退款' }
      return map[status] || '未知'
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.dateRange = []
      this.resetForm("queryForm")
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.orderId)
      this.multiple = !selection.length
    },
    handleDetail(row) {
      getMaterialOrder(row.orderId).then(response => {
        this.detailRow = response.data
        this.detailOpen = true
      })
    },
    handleRefund(row) {
      this.refundForm = {
        orderId: row.orderId,
        orderCode: row.orderCode,
        refundReason: ''
      }
      this.refundOpen = true
    },
    submitRefund() {
      this.$refs["refundForm"].validate(valid => {
        if (valid) {
          const data = {
            orderId: this.refundForm.orderId,
            payStatus: '2',
            refundReason: this.refundForm.refundReason
          }
          updateMaterialOrder(data).then(response => {
            this.$modal.msgSuccess("退款成功")
            this.refundOpen = false
            this.getList()
          })
        }
      })
    },
    handleDelete(row) {
      const orderIds = row.orderId || this.ids
      if (!orderIds || orderIds.length === 0) {
        this.$modal.msgWarning("请选择要删除的订单")
        return
      }
      this.$modal.confirm('是否确认删除所选订单？').then(function() {
        return delMaterialOrder(orderIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    handleExport() {
      this.download('system/materialOrder/export', {
        ...this.addDateRange(this.queryParams, this.dateRange)
      }, `materialOrder_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>
