<template>
  <div class="tt-cell"
       :class="[
          'tt-cell-subject-' + subjectKey(schedule && schedule.subjectName),
          {'has-schedule': !!schedule, 'is-editable': editable}
       ]">
    <!-- 已有排课卡片 -->
    <div v-if="schedule" class="tt-card">
      <div class="card-grade">{{ schedule.gradeName || '-' }}</div>
      <div class="card-subject">{{ schedule.subjectName || '-' }}
        <span class="card-type-tag" v-if="schedule.classType">{{ schedule.classType }}</span>
      </div>
      <div class="card-teacher">{{ teacherDisplay(schedule.teacherName) }}</div>
      <div class="card-footer">
        <span class="card-enroll" v-if="schedule.enrolledCount!=null">报{{ schedule.enrolledCount }}</span>
        <span class="card-status" :class="'rs-'+schedule.recruitStatus">
          {{ recruitLabel(schedule.recruitStatus) }}
        </span>
      </div>
      <div v-if="editable" class="card-actions" @click.stop>
        <i class="el-icon-edit" title="编辑" @click="$emit('edit', schedule)"></i>
        <i class="el-icon-delete" title="删除" @click="$emit('remove', schedule)"></i>
      </div>
    </div>

    <!-- 空白格：hover 显示 + 号 -->
    <div v-else class="tt-empty-cell">
      <i v-if="editable" class="el-icon-plus add-icon"
         @click.stop="$emit('add', classroomId, { value: timeSlot })"></i>
      <span v-else class="empty-placeholder">—</span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'TimetableCell',
  props: {
    schedule: { type: Object, default: null },
    classroomId: { type: [Number, String], required: true },
    timeSlot: { type: String, required: true },
    editable: { type: Boolean, default: false },
    teacherNameMap: { type: Object, default: () => ({}) }
  },
  methods: {
    teacherDisplay(name) {
      if (!name) return '-'
      // 排课表 teacher_name 存的是 nickName，可视化课表按 userName 显示
      return this.teacherNameMap[name] || name
    },
    recruitLabel(s) {
      if (s === '1') return '停招'
      if (s === '2') return '满班'
      return '可报'
    },
    subjectKey(name) {
      if (!name) return 'none'
      const map = {
        '语文': 'chinese', '数学': 'math', '英语': 'english',
        '物理': 'physics', '化学': 'chemistry', '生物': 'bio',
        '历史': 'history', '地理': 'geo', '政治': 'politics',
        '科学': 'science', '信息技术': 'it'
      }
      return map[name] || 'other'
    }
  }
}
</script>
