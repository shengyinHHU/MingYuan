<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="课次日期" prop="classDate">
        <el-date-picker v-model="queryParams.classDate" type="date" value-format="yyyy-MM-dd"
          placeholder="选择日期" clearable style="width: 160px" />
      </el-form-item>
      <el-form-item label="复核状态" prop="reviewFilter">
        <el-select v-model="queryParams.reviewFilter" placeholder="全部" clearable style="width: 140px">
          <el-option label="待复核" value="pending" />
          <el-option label="已确认 1/2" value="half" />
          <el-option label="复核完成" value="done" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="getList">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="displayList">
      <el-table-column label="课次日期" align="center" prop="classDate" width="110" />
      <el-table-column label="课程班级" align="center" prop="courseClassName" show-overflow-tooltip />
      <el-table-column label="教师" align="center" prop="teacherName" width="90" />
      <el-table-column label="期次" align="center" prop="periodName" width="80" />
      <el-table-column label="时间" align="center" prop="timeSlot" width="120" />
      <el-table-column label="教室" align="center" width="150">
        <template slot-scope="scope">
          {{ [scope.row.campusName, scope.row.classroomName].filter(Boolean).join(' · ') }}
        </template>
      </el-table-column>
      <el-table-column label="名单" align="center" prop="totalCount" width="70" />
      <el-table-column label="实到" align="center" width="70">
        <template slot-scope="scope">
          <span style="font-weight: 600; color: #c27a08">{{ scope.row.actualCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="复核状态" align="center" width="110">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.reviewStatus === '1'" type="success">复核完成</el-tag>
          <el-tag v-else-if="(scope.row.confirmedCount || 0) > 0">已确认 {{ scope.row.confirmedCount }}/2</el-tag>
          <el-tag v-else type="warning">待复核</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="100" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-s-check" @click="openReview(scope.row)">复核</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 复核弹窗 -->
    <el-dialog :title="reviewTitle" :visible.sync="reviewOpen" width="760px" append-to-body>
      <el-alert v-if="detail.review && detail.review.reviewStatus === '1'" type="success"
        title="该课次复核已完成（2 位管理员已确认）" :closable="false" style="margin-bottom: 12px" />
      <el-alert v-else-if="detail.review && detail.review.confirmedCount > 0" type="info"
        :title="`已确认 ${detail.review.confirmedCount}/2，还需 1 位管理员确认`" :closable="false" style="margin-bottom: 12px" />

      <el-descriptions :column="3" size="small" border style="margin-bottom: 12px">
        <el-descriptions-item label="名单人数">{{ detail.review ? detail.review.totalCount : '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际到课">
          <span style="font-weight: 700; color: #c27a08">{{ detail.review ? detail.review.actualCount : '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="课次日期">{{ detail.classDate }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.details" size="small" max-height="300" style="margin-bottom: 16px">
        <el-table-column label="学生姓名" align="center" prop="studentName" />
        <el-table-column label="来源" align="center" width="90">
          <template slot-scope="scope">
            <el-tag size="mini" :type="scope.row.sourceType === '1' ? 'info' : 'warning'">{{ sourceText(scope.row.sourceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="签到状态" align="center" width="110">
          <template slot-scope="scope">
            <el-tag size="mini" :type="(scope.row.signStatus === '2' || scope.row.signStatus === '3') ? 'info' : 'success'">
              {{ statusText(scope.row.signStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip />
      </el-table>

      <el-divider content-position="left">复核记录（需 2 位管理员确认）</el-divider>

      <el-row :gutter="16">
        <el-col :span="12" v-for="slot in [1, 2]" :key="slot">
          <div class="review-slot">
            <div class="review-slot-head">
              <span>管理员{{ slot }}：{{ reviewSlotName(slot) || '待确认' }}</span>
              <span class="review-slot-time">{{ slot === 1 ? detail.review && detail.review.review1Time : detail.review && detail.review.review2Time }}</span>
            </div>
            <div class="review-slot-images">
              <el-image v-for="(img, i) in reviewSlotImages(slot)" :key="i" class="review-img"
                :src="img" :preview-src-list="reviewSlotImages(slot)" fit="cover" />
              <span v-if="!reviewSlotImages(slot).length" class="review-empty">暂无课堂图片</span>
            </div>
          </div>
        </el-col>
      </el-row>

      <el-divider content-position="left">上传课堂图片</el-divider>
      <image-upload v-model="pendingImages" :limit="9" />

      <div slot="footer" class="dialog-footer">
        <el-button @click="reviewOpen = false">关 闭</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReview">
          {{ detail.review && detail.review.confirmedCount > 0 ? '补充确认' : '确认复核' }}
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listReview, getDetail, confirmReview } from "@/api/system/signReview"

export default {
  name: "SignReview",
  data() {
    return {
      loading: false,
      showSearch: true,
      submitting: false,
      list: [],
      queryParams: {
        classDate: null,
        reviewFilter: null
      },
      reviewOpen: false,
      reviewTitle: "",
      detail: { details: [], review: {} },
      pendingImages: [],
      submittingRow: null
    }
  },
  computed: {
    displayList() {
      const f = this.queryParams.reviewFilter
      if (!f) return this.list
      return this.list.filter(row => {
        if (f === 'done') return row.reviewStatus === '1'
        if (f === 'half') return row.reviewStatus !== '1' && (row.confirmedCount || 0) > 0
        return row.reviewStatus !== '1' && (row.confirmedCount || 0) === 0
      })
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listReview(this.queryParams.classDate).then(res => {
        this.list = res.data || []
      }).finally(() => {
        this.loading = false
      })
    },
    resetQuery() {
      this.queryParams = { classDate: null, reviewFilter: null }
      this.getList()
    },
    sourceText(v) {
      return { '1': '报名', '2': '试听', '3': '调课' }[v] || '报名'
    },
    statusText(v) {
      return { '1': '到课', '2': '录播', '3': '请假', '4': '试听到课', '5': '调课到课' }[v] || v
    },
    reviewSlotName(slot) {
      const r = this.detail.review || {}
      return slot === 1 ? r.review1AdminName : r.review2AdminName
    },
    reviewSlotImages(slot) {
      const r = this.detail.review || {}
      const raw = (slot === 1 ? r.review1Images : r.review2Images) || ''
      return String(raw).split(',').filter(Boolean).map(p =>
        p.startsWith('http') ? p : process.env.VUE_APP_BASE_API + p
      )
    },
    openReview(row) {
      this.submittingRow = row
      this.reviewTitle = `课次复核 - ${row.courseClassName || ''}`
      this.pendingImages = []
      this.detail = { details: [], review: {} }
      this.reviewOpen = true
      getDetail(row.scheduleId, row.classDate).then(res => {
        this.detail = res.data || { details: [], review: {} }
      })
    },
    submitReview() {
      const row = this.submittingRow
      if (!row) return
      this.submitting = true
      confirmReview(row.scheduleId, row.classDate, this.pendingImages || []).then(res => {
        const confirmed = res.confirmedCount
        const done = res.reviewStatus === '1'
        this.$modal.msgSuccess(done ? '复核完成（2/2）' : `确认成功（${confirmed}/2）`)
        this.reviewOpen = false
        this.getList()
      }).finally(() => {
        this.submitting = false
      })
    }
  }
}
</script>

<style scoped>
.review-slot {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
  min-height: 90px;
}

.review-slot-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #303133;
  font-weight: 600;
  margin-bottom: 8px;
}

.review-slot-time {
  font-weight: 400;
  color: #909399;
}

.review-slot-images {
  display: flex;
  flex-wrap: wrap;
}

.review-img {
  width: 72px;
  height: 72px;
  margin: 0 8px 8px 0;
  border-radius: 4px;
}

.review-empty {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
