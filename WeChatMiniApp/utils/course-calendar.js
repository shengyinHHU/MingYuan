// 教师日历只展示可确定的课次；不从考勤反推未来排课。
const DAY_NAMES = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const MINUTE_HEIGHT = 2 // rpx，00:00 至 24:00 全部保留

function parseDate(value) {
  const match = String(value || '').match(/^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?:$|[ T])/)
  if (!match) return null
  const date = new Date(+match[1], +match[2] - 1, +match[3])
  return date.getFullYear() === +match[1] && date.getMonth() === +match[2] - 1 && date.getDate() === +match[3] ? date : null
}
function formatDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
function addDays(date, days) { const result = new Date(date); result.setDate(result.getDate() + days); return result }
function weekStart(value) {
  const date = parseDate(value) || new Date()
  date.setHours(0, 0, 0, 0)
  return addDays(date, -((date.getDay() + 6) % 7))
}
function timeMinutes(value) {
  const match = String(value || '').match(/^(\d{2}):(\d{2})(?::(\d{2}))?$/)
  if (!match || +match[2] > 59 || +(match[3] || 0) > 59 || +match[1] > 24 || (+match[1] === 24 && (+match[2] || +(match[3] || 0)))) return null
  return +match[1] * 60 + +match[2] + +(match[3] || 0) / 60
}
function expandCourse(course) {
  const start = parseDate(course.startDate), end = parseDate(course.endDate)
  if (!start || !end) return { dates: [], reason: '排课起止日期待配置' }
  if (start > end) return { dates: [], reason: '排课起止日期顺序异常' }
  if ((end - start) / 86400000 > 3660) return { dates: [], reason: '排课日期跨度异常，请核对' }
  const pattern = course.classPattern || (['周六', '周日'].includes(course.periodName) ? 'WEEKLY' : '')
  if (!['WEEKLY', 'DAILY_5_1'].includes(pattern)) return { dates: [], reason: '重复规则待确认' }
  if (pattern === 'WEEKLY' && !DAY_NAMES.includes(course.periodName)) return { dates: [], reason: '每周上课的星期待确认' }
  const adjustments = new Map()
  for (const adjustment of course.adjustments || []) {
    const original = parseDate(adjustment.originalDate)
    const adjusted = adjustment.adjustedDate ? parseDate(adjustment.adjustedDate) : null
    if (!original || (adjustment.adjustedDate && !adjusted)) return { dates: [], reason: '调课日期异常，请核对' }
    const key = formatDate(original)
    if (adjustments.has(key)) return { dates: [], reason: '同一课次存在多条调课记录，请核对' }
    adjustments.set(key, adjusted)
  }
  const dates = new Set()
  for (let date = start, index = 0; date <= end; date = addDays(date, 1), index++) {
    const included = pattern === 'WEEKLY' ? date.getDay() === DAY_NAMES.indexOf(course.periodName) : index % 6 < 5
    if (!included) continue
    const key = formatDate(date)
    const actual = adjustments.has(key) ? adjustments.get(key) : date
    if (actual) dates.add(formatDate(actual))
  }
  return { dates: Array.from(dates).sort(), reason: '' }
}

// 同日重叠课程分列展示，避免后一条覆盖前一条。
function positionBlocks(blocks) {
  blocks.sort((a, b) => a.startMinute - b.startMinute || a.endMinute - b.endMinute || String(a.scheduleId).localeCompare(String(b.scheduleId)))
  let group = [], ends = [], groupEnd = -1
  function finish() {
    group.forEach(block => {
      block.overlap = ends.length > 1
      block.style = `top:${block.startMinute * MINUTE_HEIGHT}rpx;height:${(block.endMinute - block.startMinute) * MINUTE_HEIGHT}rpx;left:${block.lane * 100 / ends.length}%;width:${100 / ends.length}%;`
    })
  }
  blocks.forEach(block => {
    if (block.startMinute >= groupEnd) { finish(); group = []; ends = []; groupEnd = -1 }
    let lane = ends.findIndex(end => end <= block.startMinute)
    if (lane < 0) lane = ends.length
    ends[lane] = block.endMinute
    block.lane = lane
    group.push(block)
    groupEnd = Math.max(groupEnd, block.endMinute)
  })
  finish()
  return blocks
}

function buildWeek(courses, selectedDate) {
  const monday = weekStart(selectedDate), sunday = addDays(monday, 6)
  const today = formatDate(new Date()), first = formatDate(monday), last = formatDate(sunday)
  const days = Array.from({ length: 7 }, (_, index) => {
    const date = addDays(monday, index), key = formatDate(date)
    return { date: key, label: DAY_NAMES[date.getDay()], shortDate: key.slice(5), today: key === today, blocks: [] }
  })
  const byDate = new Map(days.map(day => [day.date, day]))
  const pending = [], stopped = []
  let plannedCount = 0, lessonCount = 0
  courses.forEach(course => {
    if (String(course.status) === '1') { stopped.push(course); return }
    const expanded = expandCourse(course)
    const start = timeMinutes(course.startTime), end = timeMinutes(course.endTime)
    const reason = expanded.reason || (start === null || end === null || start >= end ? '上课起止时间待核对' : '')
    if (reason) { pending.push({ ...course, pendingReason: reason }); return }
    plannedCount += expanded.dates.length
    expanded.dates.forEach(date => {
      const day = byDate.get(date)
      if (!day) return
      day.blocks.push({ ...course, date, key: `${course.scheduleId}:${date}`, startMinute: start, endMinute: end,
        timeText: `${String(course.startTime).slice(0, 5)}–${String(course.endTime).slice(0, 5)}`,
        modeText: course.classMode === '2' ? '一对一' : '班课' })
      lessonCount++
    })
  })
  days.forEach(day => positionBlocks(day.blocks))
  const hours = Array.from({ length: 25 }, (_, hour) => ({ label: `${String(hour).padStart(2, '0')}:00`, top: hour * 120 }))
  return { weekStart: first, weekEnd: last, weekLabel: `${first} 至 ${last}`, weekDays: days, hours, pendingCourses: pending,
    stoppedCourses: stopped, weekLessonCount: lessonCount, plannedLessonCount: plannedCount,
    overlapCount: days.reduce((count, day) => count + day.blocks.filter(block => block.overlap).length, 0) }
}
module.exports = { parseDate, formatDate, addDays, weekStart, timeMinutes, expandCourse, positionBlocks, buildWeek }
