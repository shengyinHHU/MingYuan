const labels = { '0': '待复核', '1': '已复核', '2': '待更正' }
function state(row) { return String(row.reviewStatus || '0') }
function pendingCount(rows) { return new Set((rows || []).filter(row => state(row) === '0').map(row => `${row.scheduleId}:${String(row.classDate).slice(0,10)}`)).size }
function isAdmin() { return (wx.getStorageSync('roles') || []).includes('admin') && !!wx.getStorageSync('token') }
module.exports = { state, pendingCount, isAdmin, label: row => labels[state(row)] || '待复核' }
