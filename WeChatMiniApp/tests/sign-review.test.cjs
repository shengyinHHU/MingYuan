const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm')
const root=path.join(__dirname,'..'),plain=x=>JSON.parse(JSON.stringify(x))
function harness(name,request,role='admin',extra={}){
 const storage={token:'admin-session',roles:[role]},notices=[],routes=[]
 const wx={getStorageSync:k=>storage[k],setStorageSync:(k,v)=>storage[k]=v,showToast:o=>notices.push(o),showModal:o=>o.success({confirm:true,content:'请核对出勤'}),navigateTo:o=>routes.push(o.url),stopPullDownRefresh(){},...extra}
 const module={exports:{}};vm.runInNewContext(fs.readFileSync(path.join(root,'utils/sign-review.js'),'utf8'),{module,wx,Set})
 let definition;vm.runInNewContext(fs.readFileSync(path.join(root,'pages',name,name+'.js'),'utf8'),{Page:p=>definition=p,wx,getApp:()=>({globalData:{baseUrl:'http://localhost'}}),require:id=>id.endsWith('/sign-review')?module.exports:id.endsWith('/teacher-course-state')?require('../utils/teacher-course-state'):id.endsWith('/access')?require('./helpers/access.cjs')(wx):id.endsWith('/child-context')?require('./helpers/child-context.cjs')(wx):{request},console,setInterval,clearInterval})
 const p={...definition,data:structuredClone(definition.data),_visible:true,_token:'admin-session'};p.setData=o=>Object.assign(p.data,o);return {p,storage,notices,routes,state:module.exports}
}
const row={scheduleId:'9',classDate:'2030-10-15',courseClassName:'化学',reviewStatus:'0',totalCount:5,actualCount:4}
const detail={details:[{signInId:'7',studentName:'学生甲',signStatus:'1',sourceType:'1'}],review:{reviewStatus:'0',reviewVersion:3,totalCount:1,actualCount:1,history:[]}}
test('student attendance entry differs from teacher WiFi review and opens the existing registered page',()=>{
 const {p}=harness('index',()=>Promise.resolve({data:[]}));assert.equal(p.data.roles.length,0)
 const source=fs.readFileSync(path.join(root,'pages/index/index.js'),'utf8');assert.match(source,/title:'学生出勤'.*route:'\/pages\/sign-review\/sign-review'/);assert.match(source,/title: '打卡复核'.*admin-checkin-review/)
 assert.ok(JSON.parse(fs.readFileSync(path.join(root,'app.json'),'utf8')).pages.includes('pages/sign-review/sign-review'))
})
test('default list shows pending sessions; completed and returned records use separate tabs',async()=>{
 const {p}=harness('sign-review',()=>Promise.resolve({data:[row,{...row,scheduleId:'10',reviewStatus:'1'},{...row,scheduleId:'11',reviewStatus:'2'}]}));await p.loadList();assert.equal(p.data.list.length,1);assert.equal(p.data.list[0].reviewText,'待复核');p.chooseState({currentTarget:{dataset:{state:'2'}}});assert.equal(p.data.list[0].scheduleId,'11');p.chooseState({currentTarget:{dataset:{state:'1'}}});assert.equal(p.data.list[0].reviewText,'已复核')
})
test('older date queries and responses after role change cannot populate administrator lists',async()=>{
 const pending=[];const {p,storage}=harness('sign-review',()=>new Promise(resolve=>pending.push(resolve)));const first=p.loadList();p.setData({filterDate:'2030-10-16'});const second=p.loadList();pending[1]({data:[{...row,scheduleId:'new'}]});await second;pending[0]({data:[row]});await first;assert.equal(p.data.list[0].scheduleId,'new');const third=p.loadList();storage.roles=['parent'];storage.token='parent';pending[2]({data:[row]});await third;assert.equal(p.data.list.length,0)
})
test('load failure offers retry and does not claim there are no pending records',async()=>{
 const {p}=harness('sign-review',()=>Promise.reject(Error('连接失败')));await p.loadList();assert.equal(p.data.error,'连接失败');assert.equal(p.data.showEmpty,false)
})
test('badge counts pending occurrences once, excludes returned and approved, and discards old identities',async()=>{
 let finish;const {p,storage,state}=harness('index',()=>new Promise(resolve=>finish=resolve));assert.equal(state.pendingCount([row,row,{...row,reviewStatus:'1'},{...row,reviewStatus:'2'}]),1);p.data.currentRole='admin';p.data.quickActions=[{key:'studentSignReview'}];const work=p.loadSignReviewPending();storage.token='other';finish({data:[row]});await work;assert.equal(p.data.quickActions[0].badgeText,undefined)
})
test('single approval sends selected occurrence and server revision; duplicate click posts once',async()=>{
 const calls=[];let modal;const {p}=harness('sign-review-detail',o=>{calls.push(o);return Promise.resolve({data:detail})},'admin',{showModal:o=>modal=o});p.setData({scheduleId:'9',classDate:'2030-10-15',ready:true,review:detail.review});const work=p.confirmReview();await p.confirmReview();modal.success({confirm:true});await work;const posts=calls.filter(c=>c.method==='POST');assert.equal(posts.length,1);assert.equal(posts[0].data.reviewVersion,3);assert.equal(posts[0].data.action,'APPROVE');assert.equal(posts[0].data.scheduleId,'9')
})
test('return requires a reason, preserves version and cannot continue after switching accounts',async()=>{
 const calls=[];let modal;const {p,storage}=harness('sign-review-detail',o=>{calls.push(o);return Promise.resolve({data:detail})},'admin',{showModal:o=>modal=o});p.setData({ready:true,review:detail.review});let work=p.returnReview();modal.success({confirm:true,content:'  '});await work;assert.equal(calls.length,0);work=p.returnReview();storage.token='parent';storage.roles=['parent'];modal.success({confirm:true,content:'更正'});await work;assert.equal(calls.length,0)
})
test('returned, approved, loading and foreign-role details cannot submit review',async()=>{
 let count=0;const {p}=harness('sign-review-detail',()=>{count++;return Promise.resolve({})});for(const status of ['1','2']){p.setData({ready:true,review:{reviewStatus:status}});await p.confirmReview();await p.returnReview()}p.setData({loading:true,review:{reviewStatus:'0'}});await p.confirmReview();assert.equal(count,0)
})
test('teacher and parent labels separate the attendance fact from the internal workflow',()=>{
 const {p}=harness('sign-in',()=>Promise.resolve({data:[]}),'teacher');assert.equal(p.decorate({...row,lastSignDate:row.classDate,reviewStatus:'2'}).reviewText,'需更正')
 const {p:parent}=harness('attendance',()=>Promise.resolve({data:[]}),'parent');const decorated=parent.decorateDetails([{attendanceStatus:'1',reviewStatus:'0'}])[0];assert.equal(decorated.statusText,'已上课');assert.equal(decorated.reviewText,'教师已登记')
})
test('teacher correction shortcut opens the specific returned occurrence instead of today',()=>{
 const {p,routes}=harness('sign-in',()=>Promise.resolve({data:[]}),'teacher');p.data.list=[{...row,course:'化学',room:'A教室',corrections:[{classDate:'2030-10-12'}]}];p.openSign({currentTarget:{dataset:{id:'9',date:'2030-10-12'}}});assert.match(routes[0],/classDate=2030-10-12/);p.openSign({currentTarget:{dataset:{id:'9',date:'2030-10-11'}}});assert.equal(routes.length,1)
})
