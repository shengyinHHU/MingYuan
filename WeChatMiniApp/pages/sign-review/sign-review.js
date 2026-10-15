const { request } = require('../../utils/request')
const reviewState = require('../../utils/sign-review')
const overview = require('../../utils/attendance-overview')
Page({
  data: { mode:'overview',courseKeyword:'',courses:[],courseIndex:-1,courseId:'',overviewLoading:false,overviewError:'',overviewReady:false,dates:[],students:[],shownStudents:[],studentKeyword:'',courseStats:{},occurrenceCount:0,from:'',to:'',summaryOpen:false,summaryLoading:false,summaryError:'',summaryScope:'course',summaryStudent:null,summaryStats:{},summaryRows:[],summaryTotal:0,summaryPage:1, loading:false, list:[], all:[], showEmpty:false, filterDate:'', state:'0', error:'', tabs:[{key:'0',label:'待复核'},{key:'2',label:'待更正'},{key:'1',label:'已复核'}] },
  onShow() { this._visible=true;if(this._owner!==wx.getStorageSync('token')){this._owner=wx.getStorageSync('token');this.setData({courseId:'',courses:[],students:[],shownStudents:[],dates:[],overviewReady:false,summaryOpen:false,summaryStudent:null,summaryRows:[],all:[],list:[]})}this.loadList();if(this.data.mode==='overview')this.loadCourses() },
  onHide() { this._visible=false; this._version=(this._version||0)+1;this._overviewVersion=(this._overviewVersion||0)+1;this._courseVersion=(this._courseVersion||0)+1;this._summaryVersion=(this._summaryVersion||0)+1;this.setData({summaryOpen:false,summaryLoading:false}) },
  onUnload() { this.onHide(); this._unloaded=true },
  onPullDownRefresh() { return (this.data.mode==='overview'?this.loadCourses():this.loadList()).finally(()=>wx.stopPullDownRefresh()) },
  currentOwner(token){return !this._unloaded&&this._visible&&reviewState.isAdmin()&&wx.getStorageSync('token')===token},
  chooseMode(e){this.setData({mode:e.currentTarget.dataset.mode});this.closeSummary();if(this.data.mode==='overview')this.loadCourses();else this.loadList()},
  courseInput(e){this.setData({courseKeyword:e.detail.value})},
  studentInput(e){const studentKeyword=e.detail.value;this.setData({studentKeyword,shownStudents:overview.filter(this.data.students,studentKeyword)})},
  async loadCourses(){
    const token=wx.getStorageSync('token'),version=this._courseVersion=(this._courseVersion||0)+1
    this._overviewVersion=(this._overviewVersion||0)+1;this.closeSummary();this.setData({overviewLoading:true,overviewReady:false,overviewError:'',dates:[],students:[],shownStudents:[]})
    if(!reviewState.isAdmin()){this.setData({overviewLoading:false,courses:[],overviewError:'请以管理员身份查看学生出勤'});return}
    try{const r=await request({url:'/miniapp/admin/sign/courses',data:{keyword:this.data.courseKeyword}});if(!this.currentOwner(token)||version!==this._courseVersion)return
      const courses=overview.courses(r.data);let index=courses.findIndex(c=>String(c.scheduleId)===String(this.data.courseId));if(index<0&&courses.length)index=0
      this.setData({courses,courseIndex:index,courseId:index<0?'':String(courses[index].scheduleId),overviewLoading:false});if(index>=0)await this.loadOverview()
    }catch(e){if(this.currentOwner(token)&&version===this._courseVersion)this.setData({overviewLoading:false,courses:[],courseIndex:-1,courseId:'',overviewError:e.message||'课程加载失败，请重试'})}
  },
  pickCourse(e){const index=Number(e.detail.value),course=this.data.courses[index];if(!course)return;this.setData({courseIndex:index,courseId:String(course.scheduleId),studentKeyword:''});this.loadOverview()},
  changeRange(e){const field=e.currentTarget.dataset.field;if(!['from','to'].includes(field))return;this.setData({[field]:e.detail.value});this.loadOverview()},
  resetRange(){this.setData({from:'',to:''});this.loadOverview()},
  async loadOverview(){
    this.closeSummary();const token=wx.getStorageSync('token'),courseId=this.data.courseId,version=this._overviewVersion=(this._overviewVersion||0)+1
    this.setData({overviewLoading:true,overviewReady:false,overviewError:'',dates:[],students:[],shownStudents:[]});if(!courseId||!reviewState.isAdmin()){this.setData({overviewLoading:false});return}
    try{const r=await request({url:'/miniapp/admin/sign/overview',data:{scheduleId:courseId,from:this.data.from,to:this.data.to}})
      if(!this.currentOwner(token)||version!==this._overviewVersion||courseId!==this.data.courseId)return
      const d=r.data||{},students=(d.rows||[]).map(overview.row);this.setData({students,shownStudents:overview.filter(students,this.data.studentKeyword),dates:d.dates||[],courseStats:d.stats||{},occurrenceCount:d.occurrenceCount||0,overviewReady:true})
    }catch(e){if(this.currentOwner(token)&&version===this._overviewVersion)this.setData({overviewError:e.message||'出勤总览加载失败，请重试'})}
    finally{if(this.currentOwner(token)&&version===this._overviewVersion)this.setData({overviewLoading:false})}
  },
  openCell(e){const {key,date}=e.currentTarget.dataset,row=this.data.students.find(r=>r.studentKey===key),index=this.data.dates.indexOf(date);if(!row||index<0||!row.cells[index].recorded)return;const c=this.data.courses[this.data.courseIndex];this.openDetail({currentTarget:{dataset:{scheduleId:this.data.courseId,date,course:c?.courseClassName,room:''}}})},
  openStudent(e){if(!reviewState.isAdmin())return;const student=this.data.students.find(r=>r.studentKey===e.currentTarget.dataset.key);if(!student||!this.data.overviewReady)return;this.setData({summaryOpen:true,summaryStudent:student,summaryScope:'course',summaryRows:[],summaryStats:{},summaryTotal:0,summaryPage:1});this.loadSummary()},
  closeSummary(){this._summaryVersion=(this._summaryVersion||0)+1;this.setData({summaryOpen:false,summaryLoading:false,summaryError:'',summaryStudent:null,summaryRows:[],summaryStats:{}})},
  chooseSummaryScope(e){const scope=e.currentTarget.dataset.scope;if(!this.data.summaryStudent?.linked&&scope==='all')return;this.setData({summaryScope:scope,summaryPage:1,summaryRows:[]});this.loadSummary()},
  moreSummary(){if(this.data.summaryLoading||!this.data.summaryHasMore)return;this.setData({summaryPage:this.data.summaryPage+1});this.loadSummary(true)},
  async loadSummary(append=false){
    if(!this.data.summaryOpen||!this.data.summaryStudent||!reviewState.isAdmin())return
    if(!append)this.setData({summaryStats:{},summaryTotal:0,summaryHasMore:false})
    const token=wx.getStorageSync('token'),version=this._summaryVersion=(this._summaryVersion||0)+1,key=this.data.summaryStudent.studentKey,scope=this.data.summaryScope,pageNum=this.data.summaryPage
    this.setData({summaryLoading:true,summaryError:''})
    try{const r=await request({url:'/miniapp/admin/sign/student-summary',data:{scheduleId:this.data.courseId,studentKey:key,allCourses:scope==='all',from:this.data.from,to:this.data.to,pageNum}})
      if(!this.currentOwner(token)||version!==this._summaryVersion||!this.data.summaryOpen||this.data.summaryStudent.studentKey!==key)return
      const d=r.data;this.setData({summaryStats:d.stats,summaryTotal:d.total,summaryHasMore:pageNum*30<d.total,summaryRows:overview.mergeLessons(append?this.data.summaryRows:[],(d.rows||[]).map(overview.summary))})
    }catch(e){if(this.currentOwner(token)&&version===this._summaryVersion){this.setData({summaryError:e.message||'统计加载失败，请重试'});if(append)this.setData({summaryPage:Math.max(1,pageNum-1)})}}
    finally{if(this.currentOwner(token)&&version===this._summaryVersion)this.setData({summaryLoading:false})}
  },
  retrySummary(){this.setData({summaryPage:1,summaryRows:[]});this.loadSummary()},
  noop(){},
  onDateFilter(e) { this.setData({filterDate:e.detail.value});this.loadList() },
  resetFilter() { this.setData({filterDate:''});this.loadList() },
  chooseState(e) { this.setData({state:e.currentTarget.dataset.state});this.filterList() },
  filterList() { const list=this.data.all.filter(row=>reviewState.state(row)===this.data.state);this.setData({list,showEmpty:!list.length}) },
  async loadList() {
    const version=this._version=(this._version||0)+1,token=wx.getStorageSync('token')
    const current=()=>!this._unloaded&&this._visible&&version===this._version&&wx.getStorageSync('token')===token&&reviewState.isAdmin()
    this.setData({loading:true,list:[],all:[],showEmpty:false,error:''})
    if(!reviewState.isAdmin()){this.setData({loading:false,error:'请以管理员身份查看学生出勤'});return}
    try {
      const res=await request({url:'/miniapp/admin/sign/list',data:this.data.filterDate?{classDate:this.data.filterDate}:{}})
      if(!current())return
      const all=(res.data||[]).map(item=>({...item,course:item.courseClassName||[item.gradeName,item.subjectName].filter(Boolean).join(''),dateText:String(item.classDate).slice(0,10),reviewText:reviewState.label(item),reviewClass:reviewState.state(item)==='1'?'done':reviewState.state(item)==='2'?'returned':'pending',room:[item.campusName,item.classroomName].filter(Boolean).join(' · ')}))
      this.setData({all});this.filterList()
    } catch(e) { if(current())this.setData({error:e.message||'加载失败，请重试'}) }
    finally { if(current())this.setData({loading:false}) }
  },
  openDetail(e) {
    if(!reviewState.isAdmin())return
    const {scheduleId,date,course,room}=e.currentTarget.dataset
    wx.navigateTo({url:`/pages/sign-review-detail/sign-review-detail?scheduleId=${scheduleId}&classDate=${date}&course=${encodeURIComponent(course||'')}&room=${encodeURIComponent(room||'')}`})
  }
})
