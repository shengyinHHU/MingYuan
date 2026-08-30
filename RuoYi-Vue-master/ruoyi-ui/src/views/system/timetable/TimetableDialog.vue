<template>
  <el-dialog
    :title="form.mode==='edit' ? '编辑排课' : '新增排课'"
    :visible.sync="visible"
    :close-on-click-modal="false"
    width="620px"
    top="8vh"
    append-to-body>

    <el-form ref="form" :model="form" :rules="rules" label-width="100px" size="small" @submit.native.prevent>
      <!-- 定位信息（只读展示） -->
      <div class="dim-readonly">
        <el-tag size="medium">{{ form.termName || '—' }}</el-tag>
        <el-tag size="medium" type="success">{{ form.periodName || '—' }}</el-tag>
        <el-tag size="medium" type="warning" v-if="form.classroomName">{{ form.classroomName }}（{{ form.campusName || form.classroomId }}）</el-tag>
        <el-tag size="medium" type="info">{{ form.timeSlot || '—' }}</el-tag>
      </div>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="年级" prop="gradeName">
            <el-select v-model="form.gradeName" placeholder="请选择年级" style="width:100%" filterable>
              <el-option v-for="g in dims.grades" :key="g.value" :label="g.label" :value="g.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="科目" prop="subjectName">
            <el-select v-model="form.subjectName" placeholder="请选择科目" style="width:100%" filterable>
              <el-option v-for="s in dims.subjects" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="教师" prop="teacherName">
            <el-select v-model="form.teacherName" placeholder="请选择教师" style="width:100%" filterable
                       :disabled="!dims.isAdmin && dims.isTeacher">
              <el-option v-for="t in dims.teachers" :key="t.userId" :label="t.userName" :value="t.nickName" />
            </el-select>
            <div v-if="!dims.isAdmin && dims.isTeacher" style="font-size:12px;color:#909399;line-height:1.4;margin-top:2px;">
              教师身份仅能为自己排课
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="班型" prop="classType">
            <el-select v-model="form.classType" placeholder="请选择班型" style="width:100%">
              <el-option v-for="c in dims.classTypes" :key="c.value" :label="c.label" :value="c.value" />
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="开始时间" prop="startTime">
            <el-time-picker v-model="form.startTime" value-format="HH:mm:ss" placeholder="开始时间" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="结束时间" prop="endTime">
            <el-time-picker v-model="form.endTime" value-format="HH:mm:ss" placeholder="结束时间" style="width:100%" />
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="开课日期" prop="startDate">
            <el-date-picker v-model="form.startDate" type="date" value-format="yyyy-MM-dd" placeholder="开课日期" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="结课日期" prop="endDate">
            <el-date-picker v-model="form.endDate" type="date" value-format="yyyy-MM-dd" placeholder="结课日期" style="width:100%" />
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="上课模式" prop="classPattern">
            <el-select v-model="form.classPattern" placeholder="请选择上课模式" style="width:100%">
              <el-option label="每周一次" value="WEEKLY" />
              <el-option label="上5天休1天" value="DAILY_5_1" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="招生状态" prop="recruitStatus">
            <el-select v-model="form.recruitStatus" style="width:100%">
              <el-option label="可报名" value="0" />
              <el-option label="停招" value="1" />
              <el-option label="满班" value="2" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="已报名数" prop="enrolledCount">
            <el-input-number v-model="form.enrolledCount" :min="0" :max="999" style="width:100%" />
          </el-form-item>
        </el-col>

        <el-col :span="24">
          <el-form-item label="课程班名称" prop="courseClassName">
            <el-input v-model="form.courseClassName" placeholder="如：五年级数学校优班（可空，保存时按年级+科目+班型自动生成）" maxlength="60" show-word-limit />
          </el-form-item>
        </el-col>

        <el-col :span="24">
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <div slot="footer" class="dialog-footer">
      <el-button @click="visible=false">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { addTimetableSchedule, updateTimetableSchedule, getTimetableSchedule } from '@/api/system/timetable'

export default {
  name: 'TimetableDialog',
  props: {
    dims: { type: Object, required: true },
    preset: { type: Object, default: null }
  },
  data() {
    return {
      visible: false,
      submitting: false,
      form: this.buildEmptyForm(),
      rules: {
        gradeName:    [{ required: true, message: '请选择年级', trigger: 'change' }],
        subjectName:  [{ required: true, message: '请选择科目', trigger: 'change' }],
        teacherName:  [{ required: true, message: '请选择/填写教师', trigger: 'change' }],
        classType:    [{ required: true, message: '请选择班型', trigger: 'change' }],
        classroomId:  [{ required: true, message: '教室缺失，请重新选择单元格' }],
        timeSlot:     [{ required: true, message: '时段缺失，请重新选择单元格' }]
      }
    }
  },
  methods: {
    buildEmptyForm() {
      return {
        scheduleId: null,
        courseYear: new Date().getFullYear(),
        termName: '', periodName: '',
        classroomId: null, classroomName: '', campusName: '',
        timeSlot: '',
        gradeName: '', subjectName: '', teacherName: '', classType: '',
        startTime: '', endTime: '',
        startDate: '', endDate: '', classPattern: 'WEEKLY',
        recruitStatus: '0',
        enrolledCount: 0,
        courseClassName: '',
        remark: ''
      }
    },
    open() {
      this.form = this.buildEmptyForm()
      if (this.preset && this.preset.defaults) {
        Object.assign(this.form, this.preset.defaults || {})
      }
      if (this.preset && this.preset.mode === 'edit' && this.form.scheduleId) {
        this.loadDetail(this.form.scheduleId)
      }
      this.visible = true
      this.$nextTick(() => {
        this.$refs.form && this.$refs.form.clearValidate()
      })
    },
    async loadDetail(id) {
      try {
        const res = await getTimetableSchedule(id)
        Object.assign(this.form, res.data || {})
      } catch (e) { /* 由 request 拦截器统一处理 */ }
    },
    autoFillClassName() {
      if (this.form.courseClassName) return
      const parts = [this.form.gradeName, this.form.subjectName, this.form.classType]
        .filter(Boolean)
      this.form.courseClassName = parts.join('')
    },
    async submit() {
      const valid = await new Promise(res => this.$refs.form.validate(ok => res(ok)))
      if (!valid) return
      this.autoFillClassName()
      this.submitting = true
      try {
        const payload = { ...this.form }
        // 后端只认 classroomId，去掉 classroomName/campusName 这两个前端辅助字段
        delete payload.classroomName
        delete payload.campusName
        if (this.form.scheduleId) {
          await updateTimetableSchedule(payload)
          this.$message.success('修改成功')
        } else {
          await addTimetableSchedule(payload)
          this.$message.success('新增成功')
        }
        this.visible = false
        this.$emit('success')
      } catch (e) {
        // request 拦截器会统一弹错
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.dim-readonly {
  display: flex; gap: 8px; flex-wrap: wrap;
  padding: 10px 14px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 10px;
  .el-tag { font-weight: normal; }
}
</style>
