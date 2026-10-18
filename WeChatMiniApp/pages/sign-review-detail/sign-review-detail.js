const { request }=require('../../utils/request')
const reviewState=require('../../utils/sign-review')
const app=getApp()
const statuses={'1':'到课','2':'录播','3':'请假','4':'试听到课','5':'调课到课'}
const actionLabels={SUBMIT:'教师提交',RETURN:'退回更正',APPROVE:'确认复核'}
Page({
  data:{baseUrl:app.globalData.baseUrl,scheduleId:null,classDate:'',course:'',room:'',rows:[],review:{},loading:false,ready:false,error:'',uploading:false,submitting:false,pendingImages:[],history:[],historyOpen:false},
  onLoad(q){this._token=wx.getStorageSync('token');this.setData({scheduleId:q.scheduleId,classDate:q.classDate||'',course:decodeURIComponent(q.course||''),room:decodeURIComponent(q.room||'')})},
  onShow(){if(this._token!==wx.getStorageSync('token')){this._token=wx.getStorageSync('token');this.setData({rows:[],review:{},pendingImages:[],history:[],ready:false,submitting:false,uploading:false})}this.loadDetail()},
  onHide(){this._version=(this._version||0)+1;this.setData({ready:false,loading:false})},
  onUnload(){this._unloaded=true;this._version=(this._version||0)+1},
  current(token=this._token){return !this._unloaded&&reviewState.isAdmin()&&wx.getStorageSync('token')===token&&token===this._token},
  async loadDetail(){
    if(!this.current()){this.setData({error:'请以管理员身份查看',ready:false,rows:[],history:[],review:{},pendingImages:[]});return}
    const token=this._token,version=this._version=(this._version||0)+1
    this.setData({loading:true,ready:false,error:''})
    try{
      const res=await request({url:'/miniapp/admin/sign/detail',data:{scheduleId:this.data.scheduleId,classDate:this.data.classDate}})
      if(!this.current(token)||version!==this._version)return
      const data=res.data||{},review=data.review||{},rows=(data.details||[]).map(r=>({...r,statusText:statuses[r.signStatus]||'未登记',sourceText:({'1':'报名','2':'试听','3':'调课'})[r.sourceType]||'报名'}))
      const history=(review.history||[]).slice().reverse().map(e=>({...e,label:actionLabels[e.action]||e.action,before:(e.before||[]).map(r=>({...r,statusText:statuses[r.signStatus]})),after:(e.after||[]).map(r=>({...r,statusText:statuses[r.signStatus]}))}))
      const images=[...new Set([...(review.review1Images||'').split(','),...(review.review2Images||'').split(','),...(review.history||[]).flatMap(e=>e.images||[])].filter(Boolean))].map(p=>p.startsWith('http')?p:app.globalData.baseUrl+p)
      this.setData({rows,review:{...review,images,statusText:reviewState.label(review)},history,ready:true})
    }catch(e){if(this.current(token)&&version===this._version)this.setData({error:e.message||'加载失败，请重试'})}
    finally{if(this.current(token)&&version===this._version)this.setData({loading:false})}
  },
  toggleHistory(){this.setData({historyOpen:!this.data.historyOpen})},
  chooseImages(){if(!this.current()||!this.data.ready||this.data.uploading||this.data.submitting||String(this.data.review.reviewStatus)!=='0')return;const token=this._token;wx.chooseMedia({count:Math.max(1,9-this.data.pendingImages.length),mediaType:['image'],success:r=>{if(this.current(token))this.uploadImages((r.tempFiles||[]).map(f=>f.tempFilePath),token)}})},
  async uploadImages(files,token){
    this.setData({uploading:true})
    try{for(const file of files){if(!this.current(token))return;const saved=await new Promise((resolve,reject)=>wx.uploadFile({url:app.globalData.baseUrl+'/common/upload',filePath:file,name:'file',header:{Authorization:'Bearer '+token},success:r=>{try{const j=JSON.parse(r.data);if(j.code!==200||!j.fileName)throw Error(j.msg||'图片上传失败');resolve(j.fileName)}catch(e){reject(e)}},fail:reject}));if(this.current(token))this.setData({pendingImages:[...this.data.pendingImages,saved].slice(0,9)})}}
    catch(e){if(this.current(token))wx.showToast({title:e.message||'图片上传失败',icon:'none'})}
    finally{if(this.current(token))this.setData({uploading:false})}
  },
  removePending(e){if(this.current()&&!this.data.submitting)this.setData({pendingImages:this.data.pendingImages.filter((_,i)=>i!==Number(e.currentTarget.dataset.index))})},
  previewImage(e){if(this.current())wx.previewImage({urls:e.currentTarget.dataset.urls,current:e.currentTarget.dataset.current})},
  returnReview(){return this.decide('RETURN')},
  confirmReview(){return this.decide('APPROVE')},
  async decide(action){
    if(!this.current()||!this.data.ready||this.data.loading||this.data.uploading||this.data.submitting||String(this.data.review.reviewStatus)!=='0')return
    const token=this._token,version=this._version,reviewVersion=this.data.review.reviewVersion||0,images=this.data.pendingImages.slice()
    this.setData({submitting:true})
    try{
      const result=await new Promise(resolve=>wx.showModal({title:action==='RETURN'?'退回教师更正':'确认学生出勤',content:action==='RETURN'?'':'确认名单及出勤结果无误？确认后将锁定本课次。',editable:action==='RETURN',placeholderText:'请填写更正原因',success:resolve,fail:()=>resolve({confirm:false})}))
      if(!result.confirm||!this.current(token)||version!==this._version)return
      const reason=(result.content||'').trim();if(action==='RETURN'&&!reason){wx.showToast({title:'请填写更正原因',icon:'none'});return}
      await request({url:'/miniapp/admin/sign/review',method:'POST',data:{scheduleId:this.data.scheduleId,classDate:this.data.classDate,reviewVersion,action,reason:action==='RETURN'?reason:undefined,images}})
      if(!this.current(token)||version!==this._version)return
      this.setData({pendingImages:[]});wx.showToast({title:action==='RETURN'?'已退回教师更正':'已复核',icon:'success'});await this.loadDetail()
    }catch(e){if(this.current(token))this.setData({error:e.message||'操作失败，请重新加载核对',ready:false})}
    finally{if(this.current(token))this.setData({submitting:false})}
  }
})
