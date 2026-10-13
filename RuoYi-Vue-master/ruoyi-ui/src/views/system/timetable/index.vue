<template>
  <div class="app-container timetable-page">
    <!-- 顶部筛选栏 -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="64px" class="tt-toolbar">
      <el-form-item label="年份">
        <el-input-number v-model="queryParams.courseYear" :min="2020" :max="2035" :step="1" controls-position="right" style="width:120px" />
      </el-form-item>
      <el-form-item label="学期">
        <el-select v-model="queryParams.termName" placeholder="请选择学期" style="width:140px" @change="handleTermChange">
          <el-option v-for="d in dims.terms" :key="d.value" :label="d.extra ? d.label + '（数据）' : d.label" :value="d.value" />
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
        <el-button type="success" icon="el-icon-upload2" size="mini" @click="openImport" v-hasPermi="['system:courseschedule:add']">课表导入</el-button>
        <el-button type="warning" icon="el-icon-sort" size="mini" @click="openSwap" v-hasPermi="['system:courseschedule:edit']">交换教室列</el-button>
      </el-form-item>
    </el-form>

    <!-- 课表主体 -->
    <div ref="ttContainer" class="tt-container" :class="{fullscreen: isFullscreen}">
      <!-- 图例 / 问题汇总 -->
      <div class="tt-legend">
        <span class="lg-item"><i class="el-icon-warning lg-warn"></i> 红色格子 = 信息不完整（时间·学期·期次·年级·科目·教师名·地点 缺失）／格式未识别（导入时识别不到，写入「未识别xx」）／冲突（同一时间、同一地点多门课程）</span>
        <span v-if="issueStats.conflictCells" class="lg-item lg-conflict">
          <i class="el-icon-warning"></i> 冲突 {{ issueStats.conflictCells }} 格
        </span>
        <span v-if="issueStats.incomplete" class="lg-item lg-incomplete">
          <i class="el-icon-warning"></i> 信息不完整 {{ issueStats.incomplete }} 条
        </span>
        <span v-if="issueStats.unrecognized" class="lg-item lg-unrecognized">
          <i class="el-icon-warning"></i> 格式未识别 {{ issueStats.unrecognized }} 条
        </span>
        <span class="lg-item lg-dim">纵轴 = 时段，横轴 = 教室（可横向滚动）</span>
      </div>

      <div v-loading="loading" class="tt-grid-wrapper">
        <table class="tt-table" cellpadding="0" cellspacing="0">
          <thead>
            <!-- 校区表头行 -->
            <tr>
              <th class="tt-corner" rowspan="2">
                <div class="corner-line1">时段 ↓</div>
                <div class="corner-line2">教室 →</div>
              </th>
              <th v-for="g in campusSpans" :key="'cp-'+g.name" :colspan="g.count" class="tt-th-campus">
                — {{ g.name }} —<span class="campus-count">（{{ g.count }} 间）</span>
              </th>
              <th v-if="hasUnspecifiedRoom" class="tt-th-unspec" rowspan="2">未指定教室</th>
            </tr>
            <!-- 教室表头行 -->
            <tr>
              <th v-for="cls in flatRooms" :key="'rm-'+cls.classroomId" class="tt-th-room">
                <span class="room-name" :title="cls.classroomName + (cls.sheetCode ? '（课表编码 ' + cls.sheetCode + '）' : '')">{{ cls.classroomName }}</span>
                <span v-if="cls.sheetCode" class="room-code">{{ cls.sheetCode }}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="ts in rowSlots" :key="'ts-'+ts" class="tt-slot-row" :class="{'is-placeholder-row': isPlaceholder(ts)}">
              <td class="tt-time-cell">
                <div class="time-label" :class="{'text-missing': isPlaceholder(ts)}">{{ ts }}</div>
                <div v-if="slotHasIssue(ts)" class="time-warn"><i class="el-icon-warning"></i></div>
              </td>
              <td v-for="cls in flatRooms" :key="'c-'+ts+'-'+cls.classroomId" class="tt-grid-cell">
                <TimetableCell
                  :schedules="cellList(cls.classroomId, ts)"
                  :classroomId="cls.classroomId"
                  :timeSlot="ts"
                  :editable="canEditCell(cls.classroomId, ts)"
                  :canEditSchedule="canEditSchedule"
                  :teacherNameMap="teacherNameMap"
                  @edit="onEdit"
                  @add="onAdd"
                  @remove="onRemove"
                />
              </td>
              <td v-if="hasUnspecifiedRoom" :key="'u-'+ts" class="tt-grid-cell tt-unspec-cell">
                <TimetableCell
                  :schedules="cellList(null, ts)"
                  :classroomId="''"
                  :timeSlot="ts"
                  :editable="canEditCell(null, ts)"
                  :canEditSchedule="canEditSchedule"
                  :teacherNameMap="teacherNameMap"
                  @edit="onEdit"
                  @add="onAdd"
                  @remove="onRemove"
                />
              </td>
            </tr>

            <!-- 未指定时段（导入后时间不明确的排课） -->
            <tr v-if="hasUnspecifiedSlot" class="tt-slot-row tt-unspec-row">
              <td class="tt-time-cell">
                <div class="time-label text-missing">未指定时段</div>
              </td>
              <td v-for="cls in flatRooms" :key="'us-'+cls.classroomId" class="tt-grid-cell">
                <TimetableCell
                  :schedules="cellList(cls.classroomId, '')"
                  :classroomId="cls.classroomId"
                  :timeSlot="''"
                  :editable="canEditCell(cls.classroomId, '')"
                  :canEditSchedule="canEditSchedule"
                  :teacherNameMap="teacherNameMap"
                  @edit="onEdit"
                  @add="onAdd"
                  @remove="onRemove"
                />
              </td>
              <td v-if="hasUnspecifiedRoom" class="tt-grid-cell tt-unspec-cell">
                <TimetableCell
                  :schedules="cellList(null, '')"
                  :classroomId="''"
                  :timeSlot="''"
                  :editable="canEditCell(null, '')"
                  :canEditSchedule="canEditSchedule"
                  :teacherNameMap="teacherNameMap"
                  @edit="onEdit"
                  @add="onAdd"
                  @remove="onRemove"
                />
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!flatRooms.length" class="tt-empty">
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

    <!-- 交换教室列 弹窗 -->
    <el-dialog
      title="交换两个教室的全部课程"
      :visible.sync="swap.open"
      width="600px"
      top="10vh"
      append-to-body
      :close-on-click-modal="false">
      <div class="swap-scope">
        <span class="swap-scope-label">交换范围：</span>
        <el-tag size="small">{{ queryParams.termName || '—' }}</el-tag>
        <el-tag size="small" type="success">{{ queryParams.periodName || '—' }}</el-tag>
        <span class="swap-scope-tip">仅交换该学期 / 期次下的排课，其它学期期次不受影响</span>
      </div>

      <el-form size="small" label-width="80px">
        <el-form-item label="教室 A">
          <el-select v-model="swap.a" filterable clearable placeholder="请选择教室" style="width:100%">
            <el-option-group v-for="(list, campus) in (dims.classrooms || {})" :key="'sa-' + campus" :label="campus">
              <el-option v-for="c in list" :key="'sa-' + c.classroomId" :label="roomLabel(c)" :value="c.classroomId" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="教室 B">
          <el-select v-model="swap.b" filterable clearable placeholder="请选择教室" style="width:100%">
            <el-option-group v-for="(list, campus) in (dims.classrooms || {})" :key="'sb-' + campus" :label="campus">
              <el-option v-for="c in list" :key="'sb-' + c.classroomId" :label="roomLabel(c)" :value="c.classroomId" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="">
          <el-button size="mini" icon="el-icon-sort" :disabled="swap.a === null && swap.b === null" @click="swapAB">对调 A / B</el-button>
        </el-form-item>
      </el-form>

      <div v-loading="swap.countsLoading" class="swap-preview">
        <div class="swap-row">
          <span class="swap-tag">A</span>
          <span class="swap-name">{{ swap.a === null ? '未选择' : roomLabelById(swap.a) }}</span>
          <span class="swap-num">{{ swap.a === null ? '—' : swapCountA }}</span>
          <span class="swap-unit">门课</span>
        </div>
        <div class="swap-row">
          <span class="swap-tag swap-tag-b">B</span>
          <span class="swap-name">{{ swap.b === null ? '未选择' : roomLabelById(swap.b) }}</span>
          <span class="swap-num">{{ swap.b === null ? '—' : swapCountB }}</span>
          <span class="swap-unit">门课</span>
        </div>
      </div>

      <div class="swap-warn">
        <i class="el-icon-warning"></i>
        交换后：A 教室的课整体搬到 B，B 的搬到 A（时段、教师、课程内容都不变）。该操作直接改排课数据；
        <b>导入来源位置不会跟着变</b> —— 之后若用同一份课表文件重新「覆盖更新」导入，教室会被改回去。
      </div>

      <div slot="footer" class="dialog-footer">
        <el-button size="mini" @click="swap.open = false">取 消</el-button>
        <el-button type="primary" size="mini" :loading="swap.loading" :disabled="!canSwap" @click="doSwap">确认交换</el-button>
      </div>
    </el-dialog>

    <!-- 课表导入弹窗 -->
    <el-dialog title="课表导入" :visible.sync="imp.open" width="920px" append-to-body :close-on-click-modal="false">
      <el-form size="small" label-width="90px">
        <el-form-item label="课程年份">
          <el-input-number v-model="imp.courseYear" :min="2020" :max="2035" :step="1" controls-position="right" style="width:130px" :disabled="imp.previewing || imp.rows.length>0" />
          <span style="margin-left:14px;color:#8993a4;font-size:12px">导入的排课归属到该年份，解析后可在预览中核对</span>
        </el-form-item>
        <el-form-item label="课表文件">
          <el-upload
            ref="impUpload"
            action="#"
            :auto-upload="false"
            :limit="1"
            accept=".xlsx,.xls"
            :file-list="imp.fileList"
            :on-change="impFileChange"
            :on-remove="impFileRemove"
            :disabled="imp.previewing">
            <el-button size="mini" icon="el-icon-document">选择文件</el-button>
            <span slot="tip" style="margin-left:12px;color:#8993a4;font-size:12px">支持：①矩阵式（行=时段、列=教室 W1/B1）；②学校原始课表（学期写在单元格里，如总表 B2「暑假」、初一年级 C3「秋季」）；③平铺表（含「排课编码/学期/期次或上课日」表头）。学期优先取 Sheet 名，其次取表内单元格/「学期」列</span>
          </el-upload>
        </el-form-item>
      </el-form>

      <div style="margin:-6px 0 10px 90px">
        <el-button type="primary" size="mini" icon="el-icon-view" :loading="imp.previewing" @click="doPreview">解析预览</el-button>
        <template v-if="imp.rows.length">
          <span style="margin-left:14px;font-size:13px">
            共 <b>{{ imp.rows.length }}</b> 条课程，
            <span :style="{color: imp.warnCount ? '#e6a23c' : '#67c23a'}">{{ imp.warnCount }} 条有告警</span>
            <template v-if="imp.sheetNames.length">，Sheet：{{ imp.sheetNames.join('、') }}</template>
          </span>
        </template>
      </div>

      <el-table v-if="imp.rows.length" :data="imp.pageRows" size="mini" border max-height="330" v-loading="imp.confirming"
                :row-class-name="impRowClass">
        <el-table-column prop="sheetName" label="Sheet" width="90" show-overflow-tooltip />
        <el-table-column prop="termName" label="学期" width="55" />
        <el-table-column prop="periodName" label="期次" width="65" />
        <el-table-column label="时段" width="95">
          <template slot-scope="s">
            <span v-if="s.row.timeSlot">{{ s.row.timeSlot }}</span>
            <span v-else style="color:#f56c6c">未识别</span>
          </template>
        </el-table-column>
        <el-table-column label="教室（课表编码）" width="145">
          <template slot-scope="s">
            <span v-if="s.row.classroomName">
              {{ s.row.campusName }}·{{ s.row.classroomName }}
              <span v-if="s.row.sheetCode" class="imp-room-code">{{ s.row.sheetCode }}</span>
            </span>
            <span v-else style="color:#f56c6c">未匹配({{ s.row.classroomCode }})</span>
          </template>
        </el-table-column>
        <el-table-column prop="courseClassName" label="课程（原文）" min-width="170" show-overflow-tooltip />
        <el-table-column label="年级/科目/班型" width="130">
          <template slot-scope="s">{{ [s.row.gradeName, s.row.subjectName, s.row.classType].filter(Boolean).join(' ') || '-' }}</template>
        </el-table-column>
        <el-table-column label="教师" width="90">
          <template slot-scope="s">
            <span v-if="s.row.teacherName">{{ s.row.teacherName }}</span>
            <span v-else style="color:#f56c6c">未识别</span>
          </template>
        </el-table-column>
        <el-table-column label="告警" min-width="120">
          <template slot-scope="s">
            <span v-if="s.row.warning" class="imp-warn-text">
              <i class="el-icon-warning"></i> {{ s.row.warning }}
            </span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="imp.rows.length" style="margin-top:8px;text-align:right">
        <el-pagination small layout="prev, pager, next, sizes" :total="imp.rows.length" :page-size.sync="imp.pageSize" :current-page.sync="imp.pageNo" :page-sizes="[20,50,100,500]" />
      </div>

      <div slot="footer" style="text-align:left">
        <el-checkbox v-if="imp.rows.length" v-model="imp.updateSupport" style="margin-right:16px">已存在同时段排课时覆盖更新</el-checkbox>
        <el-button type="warning" size="mini" icon="el-icon-magic-stick" :loading="imp.oneClicking" @click="doOneClick">一键导入</el-button>
        <el-button type="success" size="mini" :disabled="!imp.rows.length" :loading="imp.confirming" @click="doConfirm">确认导入</el-button>
        <el-button size="mini" @click="imp.open = false">取 消</el-button>
        <div v-if="!imp.rows.length" style="margin-top:6px;color:#8993a4;font-size:12px">一键导入：自动同步课表、教师（缺失自动建号，密码123456）、教室（缺失自动补建），无需先解析预览</div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import TimetableCell from './TimetableCell.vue'
import TimetableDialog from './TimetableDialog.vue'
import { isPlaceholder, scheduleIssues, issueTip } from './timetableIssue'
import {
  getTimetableDimensions,
  getTimetableGrid,
  delTimetableSchedule,
  importTimetablePreview,
  importTimetableConfirm,
  importTimetableOneClick,
  swapTimetableClassrooms
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
        classrooms: {}, classroomList: [], periodMap: {},
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
      // key: classroomId@timeSlot（缺失部分用 "-" 占位）-> 排课数组（同格可多条）
      cellMap: {},
      dialogPreset: null,
      // 交换教室列：a/b 为教室主键，counts 为该学期期次下每个教室的课程数（key 为字符串）
      swap: {
        open: false,
        a: null,
        b: null,
        loading: false,
        countsLoading: false,
        counts: {}
      },
      imp: {
        open: false,
        file: null,
        fileList: [],
        courseYear: new Date().getFullYear(),
        previewing: false,
        confirming: false,
        oneClicking: false,
        rows: [],
        sheetNames: [],
        warnCount: 0,
        updateSupport: false,
        pageNo: 1,
        pageSize: 50,
        pageRows: []
      }
    }
  },
  computed: {
    // nickName -> userName 映射
    teacherNameMap() {
      const m = {}
      ;(this.dims.teachers || []).forEach(t => { m[t.nickName] = t.userName })
      return m
    },
    availablePeriods() {
      const term = this.queryParams.termName
      const fixed = (term === '春季' || term === '秋季')
        ? [
            { value: '周六', label: '周六' },
            { value: '周日', label: '周日' }
          ]
        : [
            { value: '一期', label: '一期' },
            { value: '二期', label: '二期' },
            { value: '三期', label: '三期' }
          ]
      // 数据里实际存在的期次（含导入未识别时写入的「未识别期次」）也一并列出
      const extra = (this.dims.periodMap && this.dims.periodMap[term]) || []
      const out = fixed.slice()
      extra.forEach(p => {
        if (p && !out.some(o => o.value === p)) out.push({ value: p, label: p })
      })
      return out
    },
    visibleCampusGroup() {
      const all = this.dims.classrooms || {}
      if (!this.queryParams.campusName) return all
      const out = {}
      out[this.queryParams.campusName] = all[this.queryParams.campusName] || []
      return out
    },
    // 横轴：扁平化的教室列表（按校区顺序）
    flatRooms() {
      const groups = this.visibleCampusGroup || {}
      const out = []
      Object.keys(groups).forEach(name => { (groups[name] || []).forEach(c => out.push(c)) })
      return out
    },
    // 校区表头 colspan
    campusSpans() {
      const groups = this.visibleCampusGroup || {}
      return Object.keys(groups).map(name => ({ name, count: (groups[name] || []).length }))
    },
    hasUnspecifiedRoom() {
      return Object.keys(this.cellMap).some(k => k.startsWith('-@'))
    },
    hasUnspecifiedSlot() {
      return Object.keys(this.cellMap).some(k => k.endsWith('@-'))
    },
    /**
     * 纵轴时段行 = 字典固定时段 ∪ 数据中实际出现的时段
     * 学校原始课表里存在 8:00-9:30 / 周六 09:00-11:00 等非标准时段，
     * 若只渲染字典时段，这些课程会整行消失，因此这里做并集。
     */
    rowSlots() {
      const dict = (this.dims.timeSlots || []).map(t => t.value).filter(Boolean)
      const dictSet = new Set(dict)
      const extra = new Set()
      Object.keys(this.cellMap).forEach(k => {
        const idx = k.indexOf('@')
        const slot = idx >= 0 ? k.slice(idx + 1) : ''
        if (slot && slot !== '-' && !dictSet.has(slot)) extra.add(slot)
      })
      const extras = Array.from(extra).sort((a, b) => a.localeCompare(b, 'zh-Hans-CN'))
      return dict.concat(extras)
    },
    // 冲突格数 / 信息不完整条数 / 格式未识别条数（用于图例汇总）
    issueStats() {
      let conflictCells = 0
      let incomplete = 0
      let unrecognized = 0
      Object.keys(this.cellMap).forEach(k => {
        const list = this.cellMap[k] || []
        if (list.length > 1) conflictCells++
        list.forEach(s => {
          const is = this.issueList(s)
          if (is.some(x => x.kind === 'missing')) incomplete++
          if (is.some(x => x.kind === 'unrecognized')) unrecognized++
        })
      })
      return { conflictCells, incomplete, unrecognized }
    },
    // ============ 交换教室列 ============
    swapCountA() {
      return this.swap.a === null ? 0 : (this.swap.counts[String(this.swap.a)] || 0)
    },
    swapCountB() {
      return this.swap.b === null ? 0 : (this.swap.counts[String(this.swap.b)] || 0)
    },
    canSwap() {
      return this.swap.a !== null && this.swap.b !== null
        && String(this.swap.a) !== String(this.swap.b)
        && !!this.queryParams.termName && !!this.queryParams.periodName
    }
  },
  created() {
    this.init()
  },
  watch: {
    'imp.pageNo'() { this.impSlice() },
    'imp.pageSize'() { this.impSlice() }
  },
  methods: {
    // ============ 单元格取值 ============
    cellKey(classroomId, timeSlot) {
      const c = (classroomId === null || classroomId === undefined || classroomId === '') ? '-' : String(classroomId)
      const s = timeSlot ? String(timeSlot) : '-'
      return c + '@' + s
    },
    cellList(classroomId, timeSlot) {
      return this.cellMap[this.cellKey(classroomId, timeSlot)] || []
    },
    /** 导入时识别不到写入的占位值（未识别学期/期次/时段/年级/科目/课程） */
    isPlaceholder,
    issueTip,
    /** 单条排课的问题列表（信息不完整 + 格式未识别），与单元格组件同一套规则 */
    issueList(sch) {
      return scheduleIssues(sch, this.teacherNameMap)
    },
    /** 该时段行是否存在「时间缺失 / 时段未识别」的排课（用于行首红色感叹号） */
    slotHasIssue(ts) {
      const suffix = '@' + ts
      return Object.keys(this.cellMap).some(k => {
        if (!k.endsWith(suffix)) return false
        return (this.cellMap[k] || []).some(s => this.issueList(s).some(x => x.field === 'time'))
      })
    },
    // ============ 课表导入 ============
    openImport() {
      this.imp.open = true
      this.imp.file = null
      this.imp.fileList = []
      this.imp.rows = []
      this.imp.sheetNames = []
      this.imp.warnCount = 0
      this.imp.updateSupport = false
      this.imp.pageNo = 1
      this.imp.pageRows = []
      this.imp.courseYear = this.queryParams.courseYear || new Date().getFullYear()
    },
    impFileChange(file, fileList) {
      this.imp.file = file.raw
      this.imp.fileList = fileList.slice(-1)
      this.imp.rows = []
      this.imp.pageRows = []
    },
    impFileRemove() {
      this.imp.file = null
      this.imp.fileList = []
      this.imp.rows = []
      this.imp.pageRows = []
    },
    impSlice() {
      const size = this.imp.pageSize || 50
      const start = (this.imp.pageNo - 1) * size
      this.imp.pageRows = this.imp.rows.slice(start, start + size)
    },
    /** 预览表：有告警的行整行标红（格式不对的格子） */
    /** 预览行标红：有告警、或关键字段缺失/未识别 —— 只要有问题就整行标红 */
    impRowClass({ row }) {
      if (!row) return ''
      const bad = !!row.warning
        || !row.timeSlot || isPlaceholder(row.timeSlot)
        || !row.termName || isPlaceholder(row.termName)
        || !row.periodName || isPlaceholder(row.periodName)
        || !row.gradeName || isPlaceholder(row.gradeName)
        || !row.subjectName || isPlaceholder(row.subjectName)
        || !row.teacherName || isPlaceholder(row.teacherName)
        || !row.classroomName
      return bad ? 'imp-row-warn' : ''
    },
    async doPreview() {
      if (!this.imp.file) return this.$message.warning('请先选择课表文件')
      this.imp.previewing = true
      try {
        const fd = new FormData()
        fd.append('file', this.imp.file)
        fd.append('courseYear', this.imp.courseYear)
        const res = await importTimetablePreview(fd)
        const d = res.data || {}
        this.imp.rows = d.rows || []
        this.imp.sheetNames = d.sheets || []
        this.imp.warnCount = d.warnCount || 0
        this.imp.pageNo = 1
        this.impSlice()
        if (!this.imp.rows.length) this.$message.warning('未解析到课程数据')
      } catch (e) {
        /* 错误由 request 拦截器统一提示 */
      } finally {
        this.imp.previewing = false
      }
    },
    async doConfirm() {
      if (!this.imp.rows.length) return
      this.imp.confirming = true
      try {
        const res = await importTimetableConfirm({ rows: this.imp.rows, updateSupport: this.imp.updateSupport })
        const switched = await this.applyImportScope(res.data)
        this.imp.open = false
        this.showImportResult(res.data, switched)
      } catch (e) {
        /* 错误由 request 拦截器统一提示 */
      } finally {
        this.imp.confirming = false
      }
    },
    async doOneClick() {
      if (!this.imp.file) return this.$message.warning('请先选择课表文件')
      this.imp.oneClicking = true
      try {
        const fd = new FormData()
        fd.append('file', this.imp.file)
        fd.append('courseYear', this.imp.courseYear)
        fd.append('updateSupport', 'true')
        const res = await importTimetableOneClick(fd)
        const switched = await this.applyImportScope(res.data)
        this.imp.open = false
        this.showImportResult(res.data, switched)
      } catch (e) {
        /* 错误由 request 拦截器统一提示 */
      } finally {
        this.imp.oneClicking = false
      }
    },
    async applyImportScope(d) {
      // 先刷新维度：导入可能新增「未识别学期 / 未识别期次」等占位值，必须让下拉能选到
      await this.loadDims().catch(() => {})
      const scope = (d && d.scope) || []
      if (!scope.length) { this.handleQuery(); return '' }
      const terms = Array.from(new Set(scope.map(x => x.termName).filter(Boolean)))
      const periods = Array.from(new Set(scope.map(x => x.periodName).filter(Boolean)))
      if (terms.length !== 1) {
        this.handleQuery()
        return `多个学期（${terms.join('、')}），请手动切换查看`
      }
      this.queryParams.termName = terms[0]
      const allowed = this.availablePeriods.map(p => p.value)
      const pick = periods.find(p => allowed.includes(p))
      this.queryParams.periodName = pick || allowed[0] || ''
      this.handleQuery()
      return this.queryParams.periodName ? `${terms[0]} / ${this.queryParams.periodName}` : terms[0]
    },
    showImportResult(d, switched) {
      d = d || {}
      const s = d.sync || {}
      const syncParts = []
      if (s.newTeachers) syncParts.push(`新建教师 ${s.newTeachers} 人`)
      if (s.newRooms) syncParts.push(`新建教室 ${s.newRooms} 间`)
      if (s.newDicts) syncParts.push(`新增字典项 ${s.newDicts} 个`)
      let msg = `新增 ${d.inserted || 0} 条，覆盖更新 ${d.updated || 0} 条，跳过 ${d.skipped || 0} 条。`
      if (d.incomplete) msg += `\n其中 ${d.incomplete} 条为格式未识别：已按原文保留（学期/期次/时段/年级/科目用「未识别xx」占位），课表上用红色感叹号标出，请核对后修正。`
      if (d.stale) msg += `\n另检测到 ${d.stale} 条历史遗留行：这些格子来自更早的导入、本次文件里已经没有了（常见于修正教室映射后重新导入），会显示成「文件里没有的课」，请核对后在课表上删除。`
      if (d.staleMsgs && d.staleMsgs.length) msg += '\n遗留行示例：\n' + d.staleMsgs.join('\n')
      if (switched) msg += `\n已切换到：${switched}`
      if (syncParts.length) msg += '\n主数据同步：' + syncParts.join('，')
      if (d.skipMsgs && d.skipMsgs.length) msg += '\n跳过示例：\n' + d.skipMsgs.join('\n')
      if (!d.inserted && !d.updated && d.skipped) msg += '\n提示：若需覆盖同位置已有课程，请在导入时勾选「覆盖更新」。'
      msg += '\n\n课表中红色感叹号格子表示：冲突（同一时间、同一地点多门课）、时间／科目／教师名／地点不明确，或导入时格式未识别。'
      this.$alert(msg, '导入完成', { type: 'success' })
    },
    async loadDims() {
      const res = await getTimetableDimensions()
      this.dims = { ...this.dims, ...(res.data || {}) }
      return this.dims
    },
    async init() {
      this.loading = true
      try {
        await this.loadDims()
        this.queryParams.courseYear = this.dims.currentYear || this.queryParams.courseYear
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
        if (el && el.requestFullscreen) el.requestFullscreen().catch(() => {})
      } else if (document.fullscreenElement) {
        document.exitFullscreen().catch(() => {})
      }
    },
    // ============ 权限 ============
    canEditCell(classroomId, timeSlot) {
      if (this.dims.isAdmin) return true
      if (!this.dims.isTeacher) return false
      // 教师：空格可新增；已有课时只要其中有一条是自己的即可继续操作
      const list = this.cellList(classroomId, timeSlot)
      if (!list.length) return true
      return list.some(s => this.canEditSchedule(s))
    },
    canEditSchedule(sch) {
      if (!sch) return false
      if (this.dims.isAdmin) return true
      return this.dims.isTeacher && String(sch.teacherId) === String(this.dims.currentUserId)
    },
    // ============ 交互 ============
    onEdit(sch) {
      if (!this.canEditSchedule(sch)) {
        this.$message.info('仅管理员或负责教师可编辑此排课')
        return
      }
      this.dialogPreset = { mode: 'edit', defaults: { ...sch } }
      this.$nextTick(() => {
        this.$refs.dialog && this.$refs.dialog.open()
      })
    },
    onAdd(classroomId, ts) {
      const cls = this.dims.classroomList.find(room => String(room.classroomId) === String(classroomId)) || null
      const slot = (ts && ts.value) || ''
      if (!this.canEditCell(classroomId, slot)) {
        this.$message.warning('当前身份无权限在此格新增排课')
        return
      }
      this.dialogPreset = {
        mode: 'add',
        defaults: {
          courseYear: Number(this.queryParams.courseYear),
          termName: this.queryParams.termName,
          periodName: this.queryParams.periodName,
          classroomId: cls ? cls.classroomId : null,
          classroomName: cls ? cls.classroomName : '',
          campusName: cls ? cls.campusName : '',
          timeSlot: slot,
          teacherName: this.dims.isAdmin ? '' : this.dims.currentUserNick,
          teacherId: this.dims.isAdmin ? null : this.dims.currentUserId
        }
      }
      this.$nextTick(() => {
        this.$refs.dialog && this.$refs.dialog.open()
      })
    },
    // ============ 交换教室列 ============
    /** 教室下拉展示名：教室名（课表编码） */
    roomLabel(c) {
      if (!c) return '—'
      return c.classroomName + (c.sheetCode ? '（' + c.sheetCode + '）' : '')
    },
    roomLabelById(id) {
      const c = (this.dims.classroomList || []).find(r => String(r.classroomId) === String(id))
      return c ? this.roomLabel(c) : '—'
    },
    async openSwap() {
      if (!this.queryParams.termName || !this.queryParams.periodName) {
        this.$message.warning('请先选择学期和期次')
        return
      }
      this.swap.a = null
      this.swap.b = null
      this.swap.counts = {}
      this.swap.open = true
      await this.loadSwapCounts()
    },
    /**
     * 统计各教室课程数。
     * 只按「年份 + 学期 + 期次」取数（忽略校区/科目/年级/教师等筛选），
     * 保证弹窗里显示的门数与后端实际交换的范围一致，不会被当前筛选条件误导。
     */
    async loadSwapCounts() {
      this.swap.countsLoading = true
      try {
        const res = await getTimetableGrid({
          courseYear: this.queryParams.courseYear,
          termName: this.queryParams.termName,
          periodName: this.queryParams.periodName
        })
        const map = (res.data && res.data.cellMap) || {}
        const counts = {}
        Object.keys(map).forEach(k => {
          const cid = k.slice(0, k.indexOf('@'))
          if (!cid || cid === '-') return
          counts[cid] = (counts[cid] || 0) + ((map[k] || []).length)
        })
        this.swap.counts = counts
      } catch (e) {
        this.swap.counts = {}
      } finally {
        this.swap.countsLoading = false
      }
    },
    swapAB() {
      const t = this.swap.a
      this.swap.a = this.swap.b
      this.swap.b = t
    },
    async doSwap() {
      if (!this.canSwap || this.swap.loading) return
      const nameA = this.roomLabelById(this.swap.a)
      const nameB = this.roomLabelById(this.swap.b)
      const scope = `${this.queryParams.termName} / ${this.queryParams.periodName}`
      try {
        await this.$confirm(
          `确认在「${scope}」下交换：\n\n${nameA}（${this.swapCountA} 门课） ⇄ ${nameB}（${this.swapCountB} 门课）\n\n交换后两间教室的课程会整体对调。`,
          '交换教室列', { type: 'warning', confirmButtonText: '确认交换', cancelButtonText: '取消' }
        )
      } catch (e) {
        return // 用户取消
      }
      this.swap.loading = true
      try {
        const res = await swapTimetableClassrooms({
          courseYear: Number(this.queryParams.courseYear),
          termName: this.queryParams.termName,
          periodName: this.queryParams.periodName,
          classroomIdA: this.swap.a,
          classroomIdB: this.swap.b
        })
        const n = (res.data && res.data.swapped) || 0
        this.swap.open = false
        if (n === 0) {
          this.$message.warning('这两间教室在该学期 / 期次下都没有课程，未做任何改动')
        } else {
          this.$message.success(`交换完成，共调整 ${n} 条排课`)
        }
        this.handleQuery()
      } catch (e) {
        /* 错误由 request 拦截器统一提示 */
      } finally {
        this.swap.loading = false
      }
    },
    async onRemove(sch) {
      if (!this.canEditSchedule(sch)) {
        this.$message.info('仅管理员或负责教师可删除此排课')
        return
      }
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
