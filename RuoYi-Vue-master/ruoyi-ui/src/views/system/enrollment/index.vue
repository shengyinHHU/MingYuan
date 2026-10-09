<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="报名编码" prop="enrollmentCode">
        <el-input
          v-model="queryParams.enrollmentCode"
          placeholder="请输入报名编码"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="排课ID" prop="scheduleId">
        <el-input
          v-model="queryParams.scheduleId"
          placeholder="请输入排课ID"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="家长用户ID" prop="parentId">
        <el-input
          v-model="queryParams.parentId"
          placeholder="请输入家长用户ID"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="学生姓名" prop="studentName">
        <el-input
          v-model="queryParams.studentName"
          placeholder="请输入学生姓名"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="学生手机号" prop="studentPhone">
        <el-input
          v-model="queryParams.studentPhone"
          placeholder="请输入学生手机号"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="联系电话" prop="contactPhone">
        <el-input
          v-model="queryParams.contactPhone"
          placeholder="请输入联系电话"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="取消时间" prop="cancelTime">
        <el-date-picker clearable
          v-model="queryParams.cancelTime"
          type="date"
          value-format="yyyy-MM-dd"
          placeholder="请选择取消时间">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="取消原因" prop="cancelReason">
        <el-input
          v-model="queryParams.cancelReason"
          placeholder="请输入取消原因"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['system:enrollment:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['system:enrollment:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="cancelIds.length === 0"
          @click="handleCancelEnrollment"
          v-hasPermi="['system:enrollment:remove']"
        >取消报名</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['system:enrollment:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="enrollmentList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="报名ID" align="center" prop="enrollmentId" />
      <el-table-column label="报名编码" align="center" prop="enrollmentCode" />
      <el-table-column label="报名信息" align="center" width="130">
        <template slot-scope="scope">
          <div v-if="scope.row.subjectName" class="schedule-info">
            <div class="info-grade">{{ scope.row.gradeName || '-' }}</div>
            <div class="info-subject">
              {{ scope.row.subjectName || '-' }}
              <span v-if="scope.row.classType" class="info-type">{{ scope.row.classType }}</span>
            </div>
            <div class="info-teacher">{{ scope.row.teacherName || '-' }}</div>
            <div class="info-status" :class="'rs-' + scope.row.recruitStatus">
              {{ getRecruitLabel(scope.row.recruitStatus) }}
            </div>
          </div>
          <span v-else class="text-muted">暂无排课信息</span>
        </template>
      </el-table-column>
      <el-table-column label="学生姓名" align="center" prop="studentName" />
      <el-table-column label="学生手机号" align="center" prop="studentPhone" />
      <el-table-column label="联系电话" align="center" prop="contactPhone" />
      <el-table-column label="上课记录" align="center" width="180">
        <template slot-scope="scope">
          <div>已复核到课：{{ scope.row.attendedLessonCount || 0 }} 次</div>
          <div>待复核到课：{{ scope.row.pendingLessonCount || 0 }} 次</div>
          <div>录播：{{ scope.row.recordedLessonCount || 0 }} 次</div>
          <div>请假：{{ scope.row.leaveLessonCount || 0 }} 次</div>
        </template>
      </el-table-column>
      <el-table-column label="报名状态" align="center" prop="enrollmentStatus">
        <template slot-scope="scope">
          {{ getEnrollmentStatusLabel(scope.row.enrollmentStatus) }}
        </template>
      </el-table-column>
      <el-table-column label="支付状态" align="center" width="120">
        <template slot-scope="scope">
          <el-tag :type="getPayStatusType(scope.row.payStatus)" size="small">
            {{ scope.row.payStatus || '未支付' }}
          </el-tag>
          <div class="pay-actions" v-if="scope.row.payStatus !== '已退款'">
            <el-button
              v-if="scope.row.payStatus !== '已支付'"
              size="mini"
              type="text"
              style="color: #67C23A;"
              @click="handleConfirmPay(scope.row)"
            >确认支付</el-button>
            <el-button
              v-if="scope.row.payStatus !== '未支付'"
              size="mini"
              type="text"
              style="color: #909399;"
              @click="handleCancelPay(scope.row)"
            >取消</el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="取消时间" align="center" prop="cancelTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.cancelTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="取消原因" align="center" prop="cancelReason" />
      <el-table-column label="状态" align="center" prop="status">
        <template slot-scope="scope">
          {{ getStatusLabel(scope.row.status) }}
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['system:enrollment:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            :disabled="isEnrollmentCancelled(scope.row)"
            @click="handleCancelEnrollment(scope.row)"
            v-hasPermi="['system:enrollment:remove']"
          >取消报名</el-button>
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

    <!-- 添加或修改课程报名对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="24">
            <el-form-item label="报名编码" prop="enrollmentCode">
              <el-input v-model="form.enrollmentCode" placeholder="请输入报名编码" :disabled="form.enrollmentId != null" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="排课ID" prop="scheduleId">
              <el-input v-model="form.scheduleId" placeholder="请输入排课ID" :disabled="form.enrollmentId != null" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="家长用户ID" prop="parentId">
              <el-input v-model="form.parentId" placeholder="请输入家长用户ID" :disabled="form.enrollmentId != null" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="学生姓名" prop="studentName">
              <el-input v-model="form.studentName" placeholder="请输入学生姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="学生手机号" prop="studentPhone">
              <el-input v-model="form.studentPhone" placeholder="请输入学生手机号" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="form.contactPhone" placeholder="请输入联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="取消时间" prop="cancelTime">
              <el-date-picker clearable
                v-model="form.cancelTime"
                disabled
                type="date"
                value-format="yyyy-MM-dd"
                placeholder="请选择取消时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="取消原因" prop="cancelReason">
              <el-input v-model="form.cancelReason" placeholder="请输入取消原因" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="删除标志" prop="delFlag">
              <el-input v-model="form.delFlag" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listEnrollment, getEnrollment, cancelEnrollment, addEnrollment, updateEnrollment } from "@/api/system/enrollment"

export default {
  name: "Enrollment",
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      cancelIds: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 课程报名表格数据
      enrollmentList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        enrollmentCode: null,
        scheduleId: null,
        parentId: null,
        studentName: null,
        studentPhone: null,
        contactPhone: null,
        enrollmentStatus: null,
        payStatus: null,
        cancelTime: null,
        cancelReason: null,
        status: null,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        enrollmentCode: [
          { required: true, message: "报名编码不能为空", trigger: "blur" }
        ],
        scheduleId: [
          { required: true, message: "排课ID不能为空", trigger: "blur" }
        ],
        parentId: [
          { required: true, message: "家长用户ID不能为空", trigger: "blur" }
        ],
        studentName: [
          { required: true, message: "学生姓名不能为空", trigger: "blur" }
        ],
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    /** 查询课程报名列表 */
    getList() {
      this.loading = true
      listEnrollment(this.queryParams).then(response => {
        this.enrollmentList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        enrollmentId: null,
        enrollmentCode: null,
        scheduleId: null,
        parentId: null,
        studentName: null,
        studentPhone: null,
        contactPhone: null,
        enrollmentStatus: null,
        payStatus: null,
        cancelTime: null,
        cancelReason: null,
        status: null,
        delFlag: null,
        createBy: null,
        createTime: null,
        updateBy: null,
        updateTime: null,
        remark: null
      }
      this.resetForm("form")
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.enrollmentId)
      this.cancelIds = selection.filter(item => !this.isEnrollmentCancelled(item)).map(item => item.enrollmentId)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "添加课程报名"
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      const enrollmentId = row.enrollmentId || this.ids
      getEnrollment(enrollmentId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改课程报名"
      })
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.enrollmentId != null) {
            updateEnrollment(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addEnrollment(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    isEnrollmentCancelled(row) {
      return ['2', '已取消'].includes(String(row.enrollmentStatus)) || String(row.delFlag) === '2'
    },
    /** 取消报名，保留记录及已出勤历史 */
    handleCancelEnrollment(row) {
      const enrollmentIds = row.enrollmentId ? [row.enrollmentId] : this.cancelIds
      if (!enrollmentIds.length || (row.enrollmentId && this.isEnrollmentCancelled(row))) return
      this.$modal.confirm('确认取消选中的 ' + enrollmentIds.length + ' 条报名？报名和已出勤历史将保留，未使用考勤设为无效，并释放课程名额。').then(function() {
        return cancelEnrollment(enrollmentIds.join(','))
      }).then(response => {
        this.getList()
        this.$modal.msgSuccess(response.msg || "取消报名成功")
      }).catch(() => {})
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('system/enrollment/export', {
        ...this.queryParams
      }, `enrollment_${new Date().getTime()}.xlsx`)
    },
    /** 招生状态标签 */
    getRecruitLabel(status) {
      if (status === '1') return '停招'
      if (status === '2') return '满班'
      if (status === '0') return '招生中'
      return '-'
    },
    /** 报名状态显示文字 */
    getEnrollmentStatusLabel(status) {
      const labels = { '0': '待确认', '1': '报名成功', '2': '已取消' }
      return labels[String(status)] || status || '-'
    },
    /** 记录状态显示文字 */
    getStatusLabel(status) {
      const labels = { '0': '正常', '1': '停用' }
      return labels[String(status)] || status || '-'
    },
    /** 支付状态标签颜色 */
    getPayStatusType(status) {
      if (status === '已支付') return 'success'
      if (status === '已退款') return 'warning'
      return 'info'
    },
    /** 确认支付 */
    handleConfirmPay(row) {
      this.$modal.confirm('确认将 "' + row.studentName + '" 的报名订单标记为已支付？').then(() => {
        const data = {
          enrollmentId: row.enrollmentId,
          payStatus: '已支付'
        }
        updateEnrollment(data).then(response => {
          this.$modal.msgSuccess("确认支付成功")
          this.getList()
        })
      }).catch(() => {})
    },
    /** 取消支付 */
    handleCancelPay(row) {
      this.$modal.confirm('确认取消 "' + row.studentName + '" 的支付状态？').then(() => {
        const data = {
          enrollmentId: row.enrollmentId,
          payStatus: '未支付'
        }
        updateEnrollment(data).then(response => {
          this.$modal.msgSuccess("取消成功")
          this.getList()
        })
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.schedule-info {
  text-align: left;
  padding: 4px 0;
  .info-grade {
    font-size: 12px;
    color: #909399;
  }
  .info-subject {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    margin: 2px 0;
    .info-type {
      display: inline-block;
      margin-left: 4px;
      padding: 0 6px;
      font-size: 11px;
      color: #409EFF;
      background: #ECF5FF;
      border-radius: 3px;
    }
  }
  .info-teacher {
    font-size: 12px;
    color: #606266;
  }
  .info-status {
    font-size: 11px;
    margin-top: 2px;
    &.rs-0 { color: #67C23A; }
    &.rs-1 { color: #909399; }
    &.rs-2 { color: #E6A23C; }
  }
}
.text-muted {
  color: #C0C4CC;
  font-size: 12px;
}
.pay-actions {
  margin-top: 4px;
  display: flex;
  gap: 4px;
  justify-content: center;
}
</style>
