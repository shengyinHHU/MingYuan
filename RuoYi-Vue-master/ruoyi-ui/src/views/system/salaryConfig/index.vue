<template>
  <div class="app-container">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
          v-hasPermi="['system:salaryConfig:add']">新增标准</el-button>
      </el-col>
      <el-form :inline="true" size="mini" style="float: right">
        <el-form-item label="教师">
          <el-input v-model="queryParams.teacherName" placeholder="教师姓名" clearable style="width: 140px"
            @keyup.enter.native="getList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" size="mini" @click="getList">搜索</el-button>
        </el-form-item>
      </el-form>
    </el-row>

    <el-table v-loading="loading" :data="configList" border size="mini">
      <el-table-column label="教师" align="center" prop="teacherName" width="100" />
      <el-table-column label="底薪(元/月)" align="center" prop="baseSalary" width="110" />
      <el-table-column label="小学班课" align="center">
        <template slot-scope="scope">{{ scope.row.ratePrimaryClass }} 元/小时</template>
      </el-table-column>
      <el-table-column label="初高班课" align="center">
        <template slot-scope="scope">{{ scope.row.rateJhClass }} 元/小时</template>
      </el-table-column>
      <el-table-column label="小学一对一" align="center">
        <template slot-scope="scope">{{ scope.row.ratePrimary1on1 }} 元/小时</template>
      </el-table-column>
      <el-table-column label="初中一对一" align="center">
        <template slot-scope="scope">{{ scope.row.rateJunior1on1 }} 元/小时</template>
      </el-table-column>
      <el-table-column label="高中一对一" align="center">
        <template slot-scope="scope">{{ scope.row.rateSenior1on1 }} 元/小时</template>
      </el-table-column>
      <el-table-column label="班课时长" align="center" width="100">
        <template slot-scope="scope">{{ scope.row.classHoursPerSession || 2 }} 小时/次</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="80">
        <template slot-scope="scope">
          <el-tag size="mini" :type="scope.row.status === '0' ? 'success' : 'info'">
            {{ scope.row.status === '0' ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="140" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['system:salaryConfig:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)"
            v-hasPermi="['system:salaryConfig:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/修改对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="560px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="150px">
        <el-form-item label="教师" prop="teacherId">
          <el-select v-model="form.teacherId" placeholder="请选择教师" style="width: 100%" :disabled="form.configId != null"
            @change="onTeacherChange">
            <el-option v-for="t in teacherOptions" :key="t.teacherId" :label="t.teacherName" :value="t.teacherId" />
          </el-select>
        </el-form-item>
        <el-form-item label="底薪（元/月）" prop="baseSalary">
          <el-input-number v-model="form.baseSalary" :min="0" :precision="2" :step="100" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-divider content-position="left">课时单价（元/小时）</el-divider>
        <el-row>
          <el-col :span="12">
            <el-form-item label="小学班课">
              <el-input-number v-model="form.ratePrimaryClass" :min="0" :precision="2" :step="10" controls-position="right" style="width: 160px" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="初高班课">
              <el-input-number v-model="form.rateJhClass" :min="0" :precision="2" :step="10" controls-position="right" style="width: 160px" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="小学一对一">
              <el-input-number v-model="form.ratePrimary1on1" :min="0" :precision="2" :step="10" controls-position="right" style="width: 160px" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="初中一对一">
              <el-input-number v-model="form.rateJunior1on1" :min="0" :precision="2" :step="10" controls-position="right" style="width: 160px" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="高中一对一">
              <el-input-number v-model="form.rateSenior1on1" :min="0" :precision="2" :step="10" controls-position="right" style="width: 160px" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班课时长(小时/次)">
              <el-input-number v-model="form.classHoursPerSession" :min="0.5" :max="6" :precision="1" :step="0.5" controls-position="right" style="width: 160px" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="open = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listConfig, listTeachers, addConfig, updateConfig, delConfig } from "@/api/system/salaryConfig"

export default {
  name: "SalaryConfig",
  data() {
    return {
      loading: false,
      configList: [],
      teacherOptions: [],
      open: false,
      title: "",
      queryParams: { teacherName: null },
      form: {},
      rules: {
        teacherId: [{ required: true, message: "请选择教师", trigger: "change" }]
      }
    }
  },
  created() {
    this.getList()
    this.getTeachers()
  },
  methods: {
    getList() {
      this.loading = true
      listConfig(this.queryParams).then(res => {
        this.configList = res.data || []
      }).finally(() => { this.loading = false })
    },
    getTeachers() {
      listTeachers().then(res => {
        this.teacherOptions = res.data || []
      })
    },
    onTeacherChange(val) {
      const t = this.teacherOptions.find(x => x.teacherId === val)
      if (t) this.form.teacherName = t.teacherName
    },
    reset() {
      this.form = {
        configId: null,
        teacherId: null,
        teacherName: null,
        baseSalary: 0,
        ratePrimaryClass: 0,
        rateJhClass: 0,
        ratePrimary1on1: 0,
        rateJunior1on1: 0,
        rateSenior1on1: 0,
        classHoursPerSession: 2.0,
        status: '0',
        remark: null
      }
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    handleAdd() {
      this.reset()
      this.title = "新增薪资标准"
      this.open = true
    },
    handleUpdate(row) {
      this.reset()
      this.form = { ...this.form, ...row }
      this.title = "修改薪资标准"
      this.open = true
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        const action = this.form.configId ? updateConfig(this.form) : addConfig(this.form)
        action.then(() => {
          this.$modal.msgSuccess("保存成功")
          this.open = false
          this.getList()
        })
      })
    },
    handleDelete(row) {
      this.$modal.confirm(`确认删除「${row.teacherName}」的薪资标准？`).then(() => {
        return delConfig(row.configId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    }
  }
}
</script>
