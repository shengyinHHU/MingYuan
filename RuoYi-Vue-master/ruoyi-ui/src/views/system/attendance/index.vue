<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="上课记录编码" prop="attendanceCode">
        <el-input
          v-model="queryParams.attendanceCode"
          placeholder="请输入上课记录编码"
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
      <el-form-item label="报名ID" prop="enrollmentId">
        <el-input
          v-model="queryParams.enrollmentId"
          placeholder="请输入报名ID"
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
      <el-form-item label="学生姓名快照" prop="studentName">
        <el-input
          v-model="queryParams.studentName"
          placeholder="请输入学生姓名快照"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="确认上课时间" prop="attendedTime">
        <el-date-picker clearable
          v-model="queryParams.attendedTime"
          type="date"
          value-format="yyyy-MM-dd"
          placeholder="请选择确认上课时间">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="确认人用户ID" prop="confirmBy">
        <el-input
          v-model="queryParams.confirmBy"
          placeholder="请输入确认人用户ID"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="确认人姓名" prop="confirmName">
        <el-input
          v-model="queryParams.confirmName"
          placeholder="请输入确认人姓名"
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
          v-hasPermi="['system:attendance:add']"
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
          v-hasPermi="['system:attendance:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['system:attendance:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['system:attendance:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="attendanceList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="上课记录ID" align="center" prop="attendanceId" />
      <el-table-column label="上课记录编码" align="center" prop="attendanceCode" />
      <el-table-column label="排课ID" align="center" prop="scheduleId" />
      <el-table-column label="报名ID" align="center" prop="enrollmentId" />
      <el-table-column label="家长用户ID" align="center" prop="parentId" />
      <el-table-column label="学生姓名快照" align="center" prop="studentName" />
      <el-table-column label="上课状态" align="center" prop="attendanceStatus" />
      <el-table-column label="确认上课时间" align="center" prop="attendedTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.attendedTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="确认人用户ID" align="center" prop="confirmBy" />
      <el-table-column label="确认人姓名" align="center" prop="confirmName" />
      <el-table-column label="状态" align="center" prop="status" />
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['system:attendance:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['system:attendance:remove']"
          >删除</el-button>
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

    <!-- 添加或修改课程上课记录对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="24">
            <el-form-item label="上课记录编码" prop="attendanceCode">
              <el-input v-model="form.attendanceCode" placeholder="请输入上课记录编码" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="排课ID" prop="scheduleId">
              <el-input v-model="form.scheduleId" placeholder="请输入排课ID" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="报名ID" prop="enrollmentId">
              <el-input v-model="form.enrollmentId" placeholder="请输入报名ID" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="家长用户ID" prop="parentId">
              <el-input v-model="form.parentId" placeholder="请输入家长用户ID" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="学生姓名快照" prop="studentName">
              <el-input v-model="form.studentName" placeholder="请输入学生姓名快照" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="确认上课时间" prop="attendedTime">
              <el-date-picker clearable
                v-model="form.attendedTime"
                type="date"
                value-format="yyyy-MM-dd"
                placeholder="请选择确认上课时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="确认人用户ID" prop="confirmBy">
              <el-input v-model="form.confirmBy" placeholder="请输入确认人用户ID" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="确认人姓名" prop="confirmName">
              <el-input v-model="form.confirmName" placeholder="请输入确认人姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="删除标志" prop="delFlag">
              <el-input v-model="form.delFlag" placeholder="请输入删除标志" />
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
import { listAttendance, getAttendance, delAttendance, addAttendance, updateAttendance } from "@/api/system/attendance"

export default {
  name: "Attendance",
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 课程上课记录表格数据
      attendanceList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        attendanceCode: null,
        scheduleId: null,
        enrollmentId: null,
        parentId: null,
        studentName: null,
        attendanceStatus: null,
        attendedTime: null,
        confirmBy: null,
        confirmName: null,
        status: null,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        attendanceCode: [
          { required: true, message: "上课记录编码不能为空", trigger: "blur" }
        ],
        scheduleId: [
          { required: true, message: "排课ID不能为空", trigger: "blur" }
        ],
        enrollmentId: [
          { required: true, message: "报名ID不能为空", trigger: "blur" }
        ],
        parentId: [
          { required: true, message: "家长用户ID不能为空", trigger: "blur" }
        ],
        studentName: [
          { required: true, message: "学生姓名快照不能为空", trigger: "blur" }
        ],
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    /** 查询课程上课记录列表 */
    getList() {
      this.loading = true
      listAttendance(this.queryParams).then(response => {
        this.attendanceList = response.rows
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
        attendanceId: null,
        attendanceCode: null,
        scheduleId: null,
        enrollmentId: null,
        parentId: null,
        studentName: null,
        attendanceStatus: null,
        attendedTime: null,
        confirmBy: null,
        confirmName: null,
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
      this.ids = selection.map(item => item.attendanceId)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "添加课程上课记录"
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      const attendanceId = row.attendanceId || this.ids
      getAttendance(attendanceId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改课程上课记录"
      })
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.attendanceId != null) {
            updateAttendance(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addAttendance(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const attendanceIds = row.attendanceId || this.ids
      this.$modal.confirm('是否确认删除课程上课记录编号为"' + attendanceIds + '"的数据项？').then(function() {
        return delAttendance(attendanceIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('system/attendance/export', {
        ...this.queryParams
      }, `attendance_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>
