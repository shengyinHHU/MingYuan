function subjects(row){try{const value=row.subjectNames;return Array.isArray(value)?value:value?JSON.parse(value):[]}catch(e){return[]}}
function subjectText(row){return subjects(row).join('、')||'不限学科'}
function summary(row){return Number(row.minSpendAmount)>0?`满 ${row.minSpendAmount} 减 ${row.discountAmount}`:`无门槛减 ${row.discountAmount}`}
function template(row){return {...row,templateId:String(row.templateId),summaryText:summary(row),subjectText:subjectText(row),remaining:Number(row.totalQuantity)-Number(row.issuedQuantity||0),label:`${row.couponName} · ${summary(row)} · ${subjectText(row)} · 剩余${Number(row.totalQuantity)-Number(row.issuedQuantity||0)}张`}}
module.exports={subjects,subjectText,summary,template}
