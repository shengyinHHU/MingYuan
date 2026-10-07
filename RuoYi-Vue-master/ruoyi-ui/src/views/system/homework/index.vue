<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryForm" :model="queryParams" size="small" :inline="true" label-width="68px">
      <el-form-item label="作业标题" prop="title">
        <el-input v-model="queryParams.title" placeholder="请输入作业标题" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="发布状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择发布状态" clearable>
          <el-option label="草稿" value="0" />
          <el-option label="已发布" value="1" />
          <el-option label="已关闭" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="归档状态" prop="archiveFlag">
        <el-select v-model="queryParams.archiveFlag" placeholder="请选择状态" clearable>
          <el-option label="正常" value="0" />
          <el-option label="已归档" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="homeworkList">
      <el-table-column label="ID" prop="homeworkId" width="70" align="center" />
      <el-table-column label="作业标题" prop="title" min-width="180" show-overflow-tooltip />
      <el-table-column label="科目" prop="subjectName" width="100" align="center" />
      <el-table-column label="教师" prop="teacherName" width="120" align="center" />
      <el-table-column label="截止时间" prop="deadline" width="160" align="center" />
      <el-table-column label="发布状态" prop="status" width="100" align="center">
        <template slot-scope="scope">
          <el-tag :type="scope.row.status === '1' ? 'success' : 'info'">{{ statusText(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="归档状态" width="100" align="center">
        <template slot-scope="scope">
          <el-tag :type="isArchived(scope.row) ? 'warning' : 'success'">{{ isArchived(scope.row) ? '已归档' : '正常' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="160" align="center" />
      <el-table-column label="操作" width="260" align="center" fixed="right">
        <template slot-scope="scope">
          <el-button type="text" size="mini" icon="el-icon-view" @click="handleView(scope.row)">详情</el-button>
          <el-button v-if="!isArchived(scope.row)" type="text" size="mini" icon="el-icon-folder" v-hasPermi="['system:homework:archive']" @click="handleArchive(scope.row)">归档</el-button>
          <el-button v-else type="text" size="mini" icon="el-icon-refresh-left" v-hasPermi="['system:homework:restore']" @click="handleRestore(scope.row)">恢复</el-button>
          <el-button type="text" size="mini" icon="el-icon-delete" class="danger-text" v-hasPermi="['system:homework:remove']" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="作业详情" :visible.sync="detailOpen" width="700px" append-to-body>
      <el-descriptions v-if="currentHomework" :column="2" border size="small">
        <el-descriptions-item label="作业标题">{{ currentHomework.title }}</el-descriptions-item>
        <el-descriptions-item label="科目">{{ currentHomework.subjectName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="教师">{{ currentHomework.teacherName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ currentHomework.deadline || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总分">{{ currentHomework.totalScore == null ? '-' : currentHomework.totalScore }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ currentHomework.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交数">{{ currentHomework.submissionCount == null ? 0 : currentHomework.submissionCount }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ currentHomework.description || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">提交统计</el-divider>
      <el-table v-loading="statsLoading" :data="submissionStats" size="small" border>
        <el-table-column label="学生" prop="studentName" min-width="120" />
        <el-table-column label="是否提交" width="100" align="center">
          <template slot-scope="scope">{{ String(scope.row.submitted) === '1' ? '已提交' : '未提交' }}</template>
        </el-table-column>
        <el-table-column label="最终得分" prop="final_score" width="100" align="center" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script>
import { listHomework, getHomeworkSubmissionStats, archiveHomework, restoreHomework, delHomework } from '@/api/system/homework'

export default {
  name: 'Homework',
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      homeworkList: [],
      detailOpen: false,
      currentHomework: null,
      statsLoading: false,
      submissionStats: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        title: null,
        status: null,
        archiveFlag: null
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listHomework(this.queryParams).then(response => {
        this.homeworkList = response.rows || []
        this.total = response.total || 0
        this.loading = false
      })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm('queryForm')
      this.handleQuery()
    },
    isArchived(row) {
      return String(row.archiveFlag) === '1' || String(row.archiveStatus) === '1'
    },
    statusText(status) {
      return { '0': '草稿', '1': '已发布', '2': '已关闭' }[String(status)] || '未知'
    },
    handleView(row) {
      this.currentHomework = row
      this.submissionStats = []
      this.detailOpen = true
      this.statsLoading = true
      getHomeworkSubmissionStats(row.homeworkId).then(response => {
        this.submissionStats = Array.isArray(response.data) ? response.data : (response.rows || response.data || [])
      }).finally(() => {
        this.statsLoading = false
      })
    },
    handleArchive(row) {
      this.$modal.confirm('是否确认归档作业“' + row.title + '”？').then(() => archiveHomework(row.homeworkId)).then(() => {
        this.$modal.msgSuccess('归档成功')
        this.getList()
      }).catch(() => {})
    },
    handleRestore(row) {
      this.$modal.confirm('是否确认恢复作业“' + row.title + '”？').then(() => restoreHomework(row.homeworkId)).then(() => {
        this.$modal.msgSuccess('恢复成功')
        this.getList()
      }).catch(() => {})
    },
    handleDelete(row) {
      this.$modal.confirm('是否确认删除作业“' + row.title + '”？删除后不可恢复。').then(() => delHomework(row.homeworkId)).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.getList()
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.danger-text {
  color: #f56c6c;
}
</style>
