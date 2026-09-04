<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="排课编码" prop="scheduleCode">
        <el-input
          v-model="queryParams.scheduleCode"
          placeholder="请输入排课编码"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="教室ID" prop="classroomId">
        <el-input
          v-model="queryParams.classroomId"
          placeholder="请输入教室ID"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="课程年份" prop="courseYear">
        <el-input
          v-model="queryParams.courseYear"
          placeholder="请输入课程年份"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="学期" prop="termName">
        <el-input
          v-model="queryParams.termName"
          placeholder="请输入学期"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="期次或上课日" prop="periodName">
        <el-input
          v-model="queryParams.periodName"
          placeholder="请输入期次或上课日"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="时段" prop="timeSlot">
        <el-input
          v-model="queryParams.timeSlot"
          placeholder="请输入时段"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="开始时间" prop="startTime">
        <el-date-picker clearable
          v-model="queryParams.startTime"
          type="date"
          value-format="yyyy-MM-dd"
          placeholder="请选择开始时间">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="结束时间" prop="endTime">
        <el-date-picker clearable
          v-model="queryParams.endTime"
          type="date"
          value-format="yyyy-MM-dd"
          placeholder="请选择结束时间">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="年级" prop="gradeName">
        <el-input
          v-model="queryParams.gradeName"
          placeholder="请输入年级"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="科目" prop="subjectName">
        <el-input
          v-model="queryParams.subjectName"
          placeholder="请输入科目"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="教师姓名" prop="teacherName">
        <el-input
          v-model="queryParams.teacherName"
          placeholder="请输入教师姓名"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="已报名人数" prop="enrolledCount">
        <el-input
          v-model="queryParams.enrolledCount"
          placeholder="请输入已报名人数"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="课程班名称" prop="courseClassName">
        <el-input
          v-model="queryParams.courseClassName"
          placeholder="请输入课程班名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="来源Sheet" prop="sourceSheet">
        <el-input
          v-model="queryParams.sourceSheet"
          placeholder="请输入来源Sheet"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="来源行" prop="sourceRow">
        <el-input
          v-model="queryParams.sourceRow"
          placeholder="请输入来源行"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="来源列" prop="sourceCol">
        <el-input
          v-model="queryParams.sourceCol"
          placeholder="请输入来源列"
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
          v-hasPermi="['system:schedule:add']"
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
          v-hasPermi="['system:schedule:edit']"
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
          v-hasPermi="['system:schedule:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['system:schedule:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="scheduleList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="排课ID" align="center" prop="scheduleId" />
      <el-table-column label="排课编码" align="center" prop="scheduleCode" />
      <el-table-column label="教室ID" align="center" prop="classroomId" />
      <el-table-column label="课程年份" align="center" prop="courseYear" />
      <el-table-column label="学期" align="center" prop="termName" />
      <el-table-column label="期次或上课日" align="center" prop="periodName" />
      <el-table-column label="时段" align="center" prop="timeSlot" />
      <el-table-column label="开始时间" align="center" prop="startTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.startTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结束时间" align="center" prop="endTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.endTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="年级" align="center" prop="gradeName" />
      <el-table-column label="科目" align="center" prop="subjectName" />
      <el-table-column label="教师姓名" align="center" prop="teacherName" />
      <el-table-column label="班型" align="center" prop="classType" />
      <el-table-column label="授课形式" align="center" width="90">
        <template slot-scope="scope">
          <el-tag size="mini" :type="scope.row.classMode === '2' ? 'warning' : 'success'">
            {{ scope.row.classMode === '2' ? '一对一' : '班课' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="已报名人数" align="center" prop="enrolledCount" />
      <el-table-column label="招生状态" align="center" prop="recruitStatus" />
      <el-table-column label="课程班名称" align="center" prop="courseClassName" />
      <el-table-column label="来源Sheet" align="center" prop="sourceSheet" />
      <el-table-column label="来源行" align="center" prop="sourceRow" />
      <el-table-column label="来源列" align="center" prop="sourceCol" />
      <el-table-column label="状态" align="center" prop="status" />
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['system:schedule:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['system:schedule:remove']"
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

    <!-- 添加或修改课程排课对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="24">
            <el-form-item label="排课编码" prop="scheduleCode">
              <el-input v-model="form.scheduleCode" placeholder="请输入排课编码" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="教室ID" prop="classroomId">
              <el-input v-model="form.classroomId" placeholder="请输入教室ID" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="课程年份" prop="courseYear">
              <el-input v-model="form.courseYear" placeholder="请输入课程年份" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="学期" prop="termName">
              <el-input v-model="form.termName" placeholder="请输入学期" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="期次或上课日" prop="periodName">
              <el-input v-model="form.periodName" placeholder="请输入期次或上课日" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="时段" prop="timeSlot">
              <el-input v-model="form.timeSlot" placeholder="请输入时段" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="开始时间" prop="startTime">
              <el-date-picker clearable
                v-model="form.startTime"
                type="date"
                value-format="yyyy-MM-dd"
                placeholder="请选择开始时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="结束时间" prop="endTime">
              <el-date-picker clearable
                v-model="form.endTime"
                type="date"
                value-format="yyyy-MM-dd"
                placeholder="请选择结束时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="年级" prop="gradeName">
              <el-input v-model="form.gradeName" placeholder="请输入年级" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="科目" prop="subjectName">
              <el-input v-model="form.subjectName" placeholder="请输入科目" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="教师姓名" prop="teacherName">
              <el-input v-model="form.teacherName" placeholder="请输入教师姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="授课形式" prop="classMode">
              <el-select v-model="form.classMode" placeholder="请选择授课形式">
                <el-option label="班课" value="1" />
                <el-option label="一对一" value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="已报名人数" prop="enrolledCount">
              <el-input v-model="form.enrolledCount" placeholder="请输入已报名人数" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="课程班名称" prop="courseClassName">
              <el-input v-model="form.courseClassName" placeholder="请输入课程班名称" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="来源Sheet" prop="sourceSheet">
              <el-input v-model="form.sourceSheet" placeholder="请输入来源Sheet" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="来源行" prop="sourceRow">
              <el-input v-model="form.sourceRow" placeholder="请输入来源行" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="来源列" prop="sourceCol">
              <el-input v-model="form.sourceCol" placeholder="请输入来源列" />
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
import { listSchedule, getSchedule, delSchedule, addSchedule, updateSchedule } from "@/api/system/schedule"

export default {
  name: "Schedule",
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
      // 课程排课表格数据
      scheduleList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        scheduleCode: null,
        classroomId: null,
        courseYear: null,
        termName: null,
        periodName: null,
        timeSlot: null,
        startTime: null,
        endTime: null,
        gradeName: null,
        subjectName: null,
        teacherName: null,
        classType: null,
        enrolledCount: null,
        recruitStatus: null,
        courseClassName: null,
        sourceSheet: null,
        sourceRow: null,
        sourceCol: null,
        status: null,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        scheduleCode: [
          { required: true, message: "排课编码不能为空", trigger: "blur" }
        ],
        classroomId: [
          { required: true, message: "教室ID不能为空", trigger: "blur" }
        ],
        termName: [
          { required: true, message: "学期不能为空", trigger: "blur" }
        ],
        periodName: [
          { required: true, message: "期次或上课日不能为空", trigger: "blur" }
        ],
        timeSlot: [
          { required: true, message: "时段不能为空", trigger: "blur" }
        ],
        gradeName: [
          { required: true, message: "年级不能为空", trigger: "blur" }
        ],
        subjectName: [
          { required: true, message: "科目不能为空", trigger: "blur" }
        ],
        courseClassName: [
          { required: true, message: "课程班名称不能为空", trigger: "blur" }
        ],
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    /** 查询课程排课列表 */
    getList() {
      this.loading = true
      listSchedule(this.queryParams).then(response => {
        this.scheduleList = response.rows
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
        scheduleId: null,
        scheduleCode: null,
        classroomId: null,
        courseYear: null,
        termName: null,
        periodName: null,
        timeSlot: null,
        startTime: null,
        endTime: null,
        gradeName: null,
        subjectName: null,
        teacherName: null,
        classType: null,
        classMode: '1',
        enrolledCount: null,
        recruitStatus: null,
        courseClassName: null,
        sourceSheet: null,
        sourceRow: null,
        sourceCol: null,
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
      this.ids = selection.map(item => item.scheduleId)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "添加课程排课"
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      const scheduleId = row.scheduleId || this.ids
      getSchedule(scheduleId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改课程排课"
      })
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.scheduleId != null) {
            updateSchedule(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addSchedule(this.form).then(response => {
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
      const scheduleIds = row.scheduleId || this.ids
      this.$modal.confirm('是否确认删除课程排课编号为"' + scheduleIds + '"的数据项？').then(function() {
        return delSchedule(scheduleIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('system/schedule/export', {
        ...this.queryParams
      }, `schedule_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>
