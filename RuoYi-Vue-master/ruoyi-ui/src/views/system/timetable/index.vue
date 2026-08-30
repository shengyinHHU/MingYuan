<template>
  <div class="app-container timetable-page">
    <!-- 顶部筛选栏 -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="64px" class="tt-toolbar">
      <el-form-item label="年份">
        <el-input-number v-model="queryParams.courseYear" :min="2020" :max="2035" :step="1" controls-position="right" style="width:120px" />
      </el-form-item>
      <el-form-item label="学期">
        <el-select v-model="queryParams.termName" placeholder="请选择学期" style="width:120px" @change="handleTermChange">
          <el-option v-for="d in dims.terms" :key="d.value" :label="d.label" :value="d.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="期次">
        <el-select v-model="queryParams.periodName" placeholder="请选择期次" style="width:110px" @change="handleQuery">
          <el-option v-for="d in availablePeriods" :key="d.value" :label="d.label" :value="d.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="校区">
        <el-select v-model="queryParams.campusName" placeholder="全部校区" clearable style="width:120px" @change="handleQuery">
          <el-option v-for="(list, name) in dims.classrooms" :key="name" :label="name" :value="name" />
        </el-select>
      </el-form-item>
      <el-form-item label="科目">
        <el-select v-model="queryParams.subjectName" placeholder="全部科目" clearable style="width:100px" @change="handleQuery">
          <el-option v-for="d in dims.subjects" :key="d.value" :label="d.label" :value="d.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="年级">
        <el-select v-model="queryParams.gradeName" placeholder="全部年级" clearable style="width:100px" @change="handleQuery">
          <el-option v-for="d in dims.grades" :key="d.value" :label="d.label" :value="d.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="教师">
        <el-select v-model="queryParams.teacherName" placeholder="全部教师" clearable filterable style="width:140px" @change="handleQuery">
          <el-option v-for="t in dims.teachers" :key="t.userId" :label="t.nickName" :value="t.nickName" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">查询</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
        <el-button icon="el-icon-full-screen" size="mini" @click="toggleFullScreen">全屏</el-button>
      </el-form-item>
    </el-form>

    <!-- 课表主体 -->
    <div ref="ttContainer" class="tt-container" :class="{fullscreen: isFullscreen}">
      <div v-loading="loading" class="tt-grid-wrapper">
        <table class="tt-table" cellpadding="0" cellspacing="0">
          <!-- 表头：时段列 -->
          <thead>
            <tr>
              <th class="tt-corner">
                <div class="corner-line1">校区 / 规格</div>
                <div class="corner-line2">教室 ↓  时段 →</div>
              </th>
              <th v-for="ts in dims.timeSlots" :key="ts.value" class="tt-th-time">
                <div class="time-label">{{ ts.value }}</div>
              </th>
            </tr>
          </thead>
          <tbody>
            <template v-for="(classroomList, campusName) in visibleCampusGroup">
              <!-- 校区分节行 -->
              <tr :key="'campus-'+campusName" class="tt-campus-row">
                <td :colspan="dims.timeSlots.length + 1" class="tt-campus-cell">
                  <span class="campus-name">— {{ campusName }} —</span>
                  <span class="campus-count">（{{ classroomList.length }} 间教室）</span>
                </td>
              </tr>
              <!-- 每个教室一行 -->
              <tr v-for="cls in classroomList" :key="cls.classroomId" class="tt-classroom-row">
                <td class="tt-classroom-cell">
                  <span :class="'size-tag size-'+sizeClass(cls.classroomSize)">{{ cls.classroomSize || '-' }}</span>
                  <span class="cls-name" :title="cls.classroomName">{{ cls.classroomName }}</span>
                </td>
                <td v-for="ts in dims.timeSlots" :key="ts.value" class="tt-grid-cell"
                    @click="onCellClick(cls, ts)">
                  <TimetableCell
                    :schedule="cellSchedule(cls.classroomId, ts.value)"
                    :classroomId="cls.classroomId"
                    :timeSlot="ts.value"
                    :editable="canEditCell(cls, ts.value)"
                    :teacherNameMap="teacherNameMap"
                    @edit="onEdit"
                    @add="onAdd"
                    @remove="onRemove"
                  />
                </td>
              </tr>
            </template>
          </tbody>
        </table>
        <div v-if="!visibleCampusGroup || Object.keys(visibleCampusGroup).length===0" class="tt-empty">
          暂无匹配的教室数据，请先在「教室信息」页面录入，或调整筛选条件
        </div>
      </div>
    </div>

    <!-- 新增/编辑 弹窗 -->
    <TimetableDialog
      ref="dialog"
      :dims="dims"
      :preset="dialogPreset"
      @success="handleQuery"
    />
  </div>
</template>

<script>
import TimetableCell from './TimetableCell.vue'
import TimetableDialog from './TimetableDialog.vue'
import {
  getTimetableDimensions,
  getTimetableGrid,
  delTimetableSchedule
} from '@/api/system/timetable'

export default {
  name: 'CourseTimetable',
  components: { TimetableCell, TimetableDialog },
  data() {
    return {
      loading: false,
      isFullscreen: false,
      dims: {
        terms: [], periods: [], timeSlots: [], classTypes: [],
        grades: [], subjects: [], teachers: [],
        classrooms: {}, classroomList: [],
        currentYear: new Date().getFullYear(),
        isAdmin: false, isTeacher: false,
        currentUserNick: '', currentUserId: null
      },
      queryParams: {
        courseYear: new Date().getFullYear(),
        termName: '',
        periodName: '',
        campusName: '',
        subjectName: '',
        gradeName: '',
        teacherName: ''
      },
      cellMap: {}, // key: classroomId@timeSlot -> schedule 对象
      dialogPreset: null
    }
  },
  computed: {
    // nickName -> userName 映射，用于可视化课表把存的 nickName 显示成 userName
    teacherNameMap() {
      const m = {}
      ;(this.dims.teachers || []).forEach(t => { m[t.nickName] = t.userName })
      return m
    },
    // 期次根据学期动态变化：暑假→一期/二期/三期；春季/秋季→周六/周日
    availablePeriods() {
      const term = this.queryParams.termName
      if (term === '春季' || term === '秋季') {
        return [
          { value: '周六', label: '周六' },
          { value: '周日', label: '周日' }
        ]
      }
      return [
        { value: '一期', label: '一期' },
        { value: '二期', label: '二期' },
        { value: '三期', label: '三期' }
      ]
    },
    visibleCampusGroup() {
      const all = this.dims.classrooms || {}
      if (!this.queryParams.campusName) return all
      const out = {}
      out[this.queryParams.campusName] = all[this.queryParams.campusName] || []
      return out
    }
  },
  created() {
    this.init()
  },
  methods: {
    sizeClass(size) {
      if (!size) return 'n'
      if (size === '大') return 'l'
      if (size === '中') return 'm'
      if (size === '小') return 's'
      return size
    },
    async init() {
      this.loading = true
      try {
        const res = await getTimetableDimensions()
        this.dims = { ...this.dims, ...(res.data || {}) }
        this.queryParams.courseYear = this.dims.currentYear || this.queryParams.courseYear
        // 默认值：第一个学期 + 该学期下的第一个期次
        if (!this.queryParams.termName && this.dims.terms.length) {
          this.queryParams.termName = this.dims.terms[0].value
        }
        if (!this.queryParams.periodName && this.availablePeriods.length) {
          this.queryParams.periodName = this.availablePeriods[0].value
        }
        await this.loadGrid()
      } catch (e) {
        // 错误由 request 拦截器统一提示
      } finally {
        this.loading = false
      }
    },
    async loadGrid() {
      if (!this.queryParams.termName || !this.queryParams.periodName) {
        this.cellMap = {}
        return
      }
      const res = await getTimetableGrid(this.queryParams)
      this.cellMap = (res.data && res.data.cellMap) || {}
    },
    handleQuery() {
      this.loading = true
      this.loadGrid().finally(() => { this.loading = false })
    },
    handleTermChange() {
      // 学期切换后，期次重置为新学期下的第一个并重新查询
      const periods = this.availablePeriods
      this.queryParams.periodName = periods.length ? periods[0].value : ''
      this.handleQuery()
    },
    resetQuery() {
      this.$refs.queryForm.resetFields()
      this.queryParams.courseYear = this.dims.currentYear
      this.queryParams.termName = this.dims.terms[0] && this.dims.terms[0].value
      this.queryParams.periodName = this.availablePeriods[0] && this.availablePeriods[0].value
      this.handleQuery()
    },
    toggleFullScreen() {
      this.isFullscreen = !this.isFullscreen
      if (this.isFullscreen) {
        const el = this.$refs.ttContainer
        if (el && el.requestFullscreen) el.requestFullscreen().catch(()=>{})
      } else if (document.fullscreenElement) {
        document.exitFullscreen().catch(()=>{})
      }
    },
    cellSchedule(classroomId, timeSlot) {
      return this.cellMap[classroomId + '@' + timeSlot] || null
    },
    canEditCell(classroomId, timeSlot) {
      if (this.dims.isAdmin) return true
      const sch = this.cellSchedule(classroomId, timeSlot)
      if (!sch) {
        // 空白格：teacher 可以新增
        return this.dims.isTeacher
      }
      // 已有课：admin 已经在上面 return true 了；teacher 需判断 createBy
      return sch.createBy === this.dims.currentUserId ||
             (this.$store.getters && this.$store.getters.name && sch.createBy === this.$store.getters.name)
    },
    onCellClick(cls, ts) {
      // 点击单元格，如果无权限编辑则提示
      const editable = this.canEditCell(cls.classroomId, ts.value)
      const sch = this.cellSchedule(cls.classroomId, ts.value)
      if (!sch) {
        if (!editable) {
          this.$message.warning('当前身份无权限在此格新增排课')
          return
        }
        this.onAdd(cls, ts)
      } else {
        if (!editable) {
          this.$message.info('仅管理员或创建人可编辑此排课')
        }
        this.onEdit(sch)
      }
    },
    onAdd(cls, ts) {
      this.dialogPreset = {
        mode: 'add',
        defaults: {
          courseYear: Number(this.queryParams.courseYear),
          termName: this.queryParams.termName,
          periodName: this.queryParams.periodName,
          classroomId: cls.classroomId,
          classroomName: cls.classroomName,
          campusName: cls.campusName,
          timeSlot: ts.value,
          // teacher 角色默认把教师名称设为自己（后端会再次校验）
          teacherName: this.dims.isAdmin ? '' : this.dims.currentUserNick
        }
      }
      this.$nextTick(() => {
        this.$refs.dialog && this.$refs.dialog.open()
      })
    },
    onEdit(sch) {
      this.dialogPreset = {
        mode: 'edit',
        defaults: { ...sch }
      }
      this.$nextTick(() => {
        this.$refs.dialog && this.$refs.dialog.open()
      })
    },
    async onRemove(sch) {
      try {
        await this.$confirm(`确认删除「${sch.gradeName || ''}${sch.subjectName || ''} ${this.teacherNameMap[sch.teacherName] || sch.teacherName || ''}」？`, '提示', { type: 'warning' })
        await delTimetableSchedule(sch.scheduleId)
        this.$message.success('删除成功')
        this.handleQuery()
      } catch (e) { /* 取消不提示 */ }
    }
  }
}
</script>

<style lang="scss" scoped src="./timetable.scss"></style>
