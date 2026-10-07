import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

// Run only against the owned disposable backend; never against the existing service.
const base=process.env.SHOP_HTTP_URL || 'http://127.0.0.1:18081';
assert.equal(new URL(base).port,'18081','This mutation smoke test is restricted to disposable port18081');
let checks=0;
const check=(actual,expected)=>{assert.deepEqual(actual,expected);checks++;};
async function call(path,token,method='GET',body) {
  const res=await fetch(base+path,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});
  const json=await res.json();return json;
}
async function ok(path,token,method='GET',body){const result=await call(path,token,method,body);assert.equal(result.code,200,path+': '+result.msg);checks++;return result;}
async function denied(path,token,method='GET',body){const result=await call(path,token,method,body);assert.notEqual(result.code,200);checks++;return result;}
async function login(role){return (await ok('/miniapp/login',null,'POST',{devRole:role,nickName:'商城隔离测试'})).token;}
const admin=await login('admin'),parent=await login('parent'),teacher=await login('teacher');
await denied('/system/material/list',parent);
await denied('/system/materialOrder/list',teacher);
const dict=(await ok('/miniapp/material/dict',parent)).data;
const subjectCode=dict.subjects[0].dictValue,gradeCode=dict.grades[0].dictValue;
const stamp=randomUUID().slice(0,8);
let p=(await ok('/system/material',admin,'POST',{
 title:'HTTP纸质测试'+stamp,subjectCode,gradeCode,deliveryType:'PHYSICAL',price:'20.00',shippingFee:'5.00',stockQuantity:3,
 coverUrl:'/profile/test-cover.png',detailText:'隔离测试详情',shelfStatus:'1',unit:'份',purchaseLimit:9,
 images:[{imageType:'GALLERY',imageUrl:'/profile/gallery.jpg',sortOrder:1},{imageType:'DETAIL',imageUrl:'/profile/detail.png',sortOrder:2}]
})).data;
check(typeof p.materialId,'string');check(p.price,'20.00');check(p.images.length,2);
const listing=await ok('/system/material/list?title='+encodeURIComponent('HTTP纸质测试'+stamp),admin);
check(listing.rows.length,1);check(listing.total,1);assert(!('data' in listing));checks++;
const publicListing=(await ok('/miniapp/parent/material/list?title='+encodeURIComponent('HTTP纸质测试'+stamp),parent)).data;
check(publicListing.rows.length,1);check(publicListing.total,1);
check((await ok('/miniapp/parent/material/'+p.materialId,parent)).data.bought,false);
await denied('/miniapp/parent/material/list?sort=price%3BDROP',parent);
const address={receiverName:'张三',receiverPhone:'13800138000',provinceName:'江苏省',cityName:'南京市',districtName:'鼓楼区',detailAddress:'隔离测试1号楼',isDefault:1};
const saved=(await ok('/miniapp/parent/addresses',parent,'POST',address)).data;
check(typeof saved.addressId,'string');
check((await ok('/miniapp/parent/addresses',parent)).data.filter(a=>a.isDefault===1).length,1);
const b={materialId:p.materialId,quantity:2,expectedPrice:'20.00',addressId:saved.addressId,clientRequestId:randomUUID()};
const o=(await ok('/miniapp/parent/material/orders',parent,'POST',b)).data;
check(typeof o.orderId,'string');check(o.payableAmount,'45.00');check(o.amount,'0.00');check(o.orderStatus,'WAIT_PAY');check(o.totalQuantity,2);
check((await ok('/miniapp/parent/material/orders',parent,'POST',b)).data.orderId,o.orderId);
await denied('/miniapp/parent/material/orders',parent,'POST',{...b,quantity:1});
await denied('/miniapp/parent/material/orders',parent,'POST',{...b,quantity:1.5,clientRequestId:randomUUID()});
check((await ok('/miniapp/parent/material/orders/'+o.orderId+'/payment',parent,'POST')).data.mode,'MOCK');
await ok('/miniapp/parent/material/orders/'+o.orderId+'/mock-pay',parent,'POST');
const paid=(await ok('/miniapp/parent/material/orders/'+o.orderId+'/mock-pay',parent,'POST')).data;
check(paid.amount,'45.00');check(paid.orderStatus,'WAIT_SHIP');check(paid.payment.channel,'MOCK');check(paid.payment.wxTransactionId,null);
assert(!('adminRemark' in paid));assert(!('assetPath' in paid.items[0]));checks+=2;
p=(await ok('/system/material/'+p.materialId,admin)).data;check(p.stockQuantity,1);check(p.stockLocked,0);check(p.saleCount,2);
const internal=(await ok('/system/materialOrder/'+o.orderId,admin)).data;check(internal.receiverPhone,'13800138000');
const listedOrder=(await ok('/system/materialOrder/list?orderCode='+o.orderCode,admin)).rows[0];
check(listedOrder.receiverPhone,'138****8000');
for(const key of ['provinceName','cityName','districtName','detailAddress','buyerRemark','adminRemark','logs']) {
 assert(!Object.hasOwn(listedOrder,key),'Admin list must omit detail-only field '+key);checks++;
}
for(const [receiverPhone,expected] of [['123456','1****6'],['1234567','1****7'],['123-4567','1****7']]) {
 const shortBody={materialId:p.materialId,quantity:1,expectedPrice:'20.00',address:{...address,receiverPhone},clientRequestId:randomUUID()};
 const shortOrder=(await ok('/miniapp/parent/material/orders',parent,'POST',shortBody)).data;
 check((await ok('/system/materialOrder/list?orderCode='+shortOrder.orderCode,admin)).rows[0].receiverPhone,expected);
 check((await ok('/system/materialOrder/'+shortOrder.orderId,admin)).data.receiverPhone,receiverPhone);
 await ok('/miniapp/parent/material/orders/'+shortOrder.orderId+'/cancel',parent,'POST');
}
await ok('/miniapp/parent/addresses/'+saved.addressId,parent,'PUT',{...address,receiverName:'李四'});
check((await ok('/miniapp/parent/material/orders/'+o.orderId,parent)).data.receiverName,'张三');
await denied('/miniapp/parent/material/orders/'+o.orderId,teacher);
await denied('/miniapp/parent/material/buy',parent,'POST',{materialId:p.materialId});
await denied('/system/materialOrder',admin,'PUT',{orderId:o.orderId,payStatus:'2'});
await denied('/system/materialOrder/'+o.orderId,admin,'DELETE');
const parcel={carrierCode:'SF',carrierName:'顺丰',trackingNo:'00123'};
check((await ok('/system/materialOrder/'+o.orderId+'/ship',admin,'POST',parcel)).data.orderStatus,'WAIT_RECEIVE');
await ok('/system/materialOrder/'+o.orderId+'/ship',admin,'POST',parcel);
await denied('/system/materialOrder/'+o.orderId+'/refund',admin,'POST',{reason:'已发货'});
check((await ok('/miniapp/parent/material/orders/'+o.orderId+'/receive',parent,'POST')).data.orderStatus,'COMPLETED');
await ok('/miniapp/parent/material/orders/'+o.orderId+'/receive',parent,'POST');
const form=new FormData();form.append('file',new Blob(['%PDF-1.7\\nprivate fixture'],{type:'application/pdf'}),'fixture.pdf');
const uploadedResponse=await fetch(base+'/miniapp/teacher/material/file',{method:'POST',headers:{Authorization:'Bearer '+teacher},body:form});
const uploaded=await uploadedResponse.json();check(uploaded.code,200);assert(uploaded.data.filePath.startsWith('shop/'));checks++;
const draft=(await ok('/miniapp/teacher/material',teacher,'POST',{title:'HTTP教师草稿'+stamp,deliveryType:'DIGITAL',...uploaded.data})).data;
check(draft.shelfStatus,'2');
await denied('/miniapp/teacher/material',teacher,'PUT',{materialId:draft.materialId,version:draft.version,shelfStatus:'1'});
await denied('/miniapp/parent/material/'+draft.materialId,parent);
const publicPdf=await fetch(base+'/profile/'+uploaded.data.filePath);check(publicPdf.status,403);
const commonPdf=await fetch(base+'/common/download/resource?resource='+encodeURIComponent(uploaded.data.filePath),{headers:{Authorization:'Bearer '+admin}});check(commonPdf.status,403);
const oldPdf=await fetch(base+'/profile/upload/legacy.pdf');check(oldPdf.status,403);
const exported=await fetch(base+'/system/materialOrder/export',{method:'POST',headers:{Authorization:'Bearer '+admin}});check(exported.status,200);
const magic=new Uint8Array(await exported.arrayBuffer());check([...magic.slice(0,2)],[80,75]);
console.log(JSON.stringify({result:'PASS',checks,backend:base,physicalOrderStatus:'COMPLETED',mode:'MOCK',originalService:'untouched'}));
