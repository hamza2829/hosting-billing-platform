import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
const env=Object.fromEntries(readFileSync('.env','utf8').trim().split(/\r?\n/).map(l=>l.split('=')));
const root='http://localhost:';const checks=[];const timings=[];const run=Date.now();
const period=(new Date().getUTCFullYear()+2)+'-01';
async function api(port,path,body,trace='smoke-'+run,extra={}) {
  const r=await fetch(root+port+path,{method:body?'POST':'GET',headers:{'Content-Type':'application/json','X-API-Key':env.API_KEY,'X-Correlation-ID':trace,...extra},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(10000)});
  const text=await r.text();let data;try{data=JSON.parse(text);}catch{data=text;}
  return {status:r.status,data,correlation:r.headers.get('X-Correlation-ID')};
}
async function until(fn,label,timeout=60000) {
  const started=Date.now();let last;
  while(Date.now()-started<timeout){try{last=await fn();if(last)return last;}catch(e){last=e.message;}await new Promise(r=>setTimeout(r,300));}
  throw Error('Timed out: '+label+' '+JSON.stringify(last));
}
function pass(name){checks.push(name);console.log('PASS '+name);}
const source=(result)=>result.data.hits.hits.map(h=>h._source);
async function history(id){return source(await api(8083,'/invoices/'+id+'/audit'));}
async function provider(){return (await api(8090,'/state')).data;}
async function mode(value){assert.equal((await api(8090,'/control',{mode:value})).status,200);}
for(const port of [8081,8082,8083]){await until(async()=>(await api(port,'/actuator/health')).data.status==='UP','health '+port);assert.equal((await api(port,'/v3/api-docs')).status,200);}
pass('three service health and OpenAPI endpoints');
assert.equal((await api(8081,'/customers',null,'auth',{'X-API-Key':'wrong'})).status,401);pass('API key authentication');
assert.equal((await api(8081,'/customers',{name:'',email:'invalid'})).status,400);
assert.equal((await api(8083,'/search?status=UNKNOWN')).status,400);
assert.equal((await api(8083,'/search?from=2026-12-31T00:00:00Z&to=2026-01-01T00:00:00Z')).status,400);pass('validation and global errors');
const c=await api(8081,'/customers',{name:'Synthetic Hosting Customer',email:'synthetic-'+run+'@example.test'});assert.equal(c.status,201);const customer=c.data;
async function subscription(plan,start=period+'-01') {const r=await api(8081,'/subscriptions',{customerId:customer.id,plan,amountMinor:1200,currency:'EUR',startsOn:start});assert.equal(r.status,201);return r.data;}
async function invoice(plan,trace) {const s=await subscription(plan);const started=Date.now();const r=await api(8081,'/invoices',{subscriptionId:s.id,period},trace);assert.equal(r.status,200);return {...r.data,subscription:s,started};}
async function state(id,status){return until(async()=>{const r=await api(8081,'/invoices/'+id);return r.data.status===status?r.data:false;},'invoice '+status);}
await mode('success');const trace='success-'+run;const paid=await invoice('Synthetic VPS success',trace);await state(paid.id,'PAID');timings.push({case:'successful-payment',milliseconds:Date.now()-paid.started});pass('invoice to Kafka to provider to paid invoice');
const repeats=await Promise.all(Array.from({length:8},()=>api(8081,'/invoices',{subscriptionId:paid.subscription.id,period},trace)));assert(repeats.every(r=>r.status===200&&r.data.id===paid.id));pass('concurrent monthly invoice uniqueness');
const audit=await until(async()=>{const rows=await history(paid.id);return rows.some(r=>r.type==='PaymentReceived')?rows:false;},'paid audit');
assert.equal(audit.length,2);assert(audit.every(r=>r.correlationId===trace));
const attempts=(await provider()).attempts.filter(a=>a.key==='invoice-'+paid.id);assert.equal(attempts.length,1);assert.equal(attempts[0].correlationId,trace);pass('Kafka headers preserve correlation into audit and provider');
const original=audit.find(r=>r.type==='InvoiceCreated');
const event={schemaVersion:1,eventId:original.eventId,type:'InvoiceCreated',invoiceId:paid.id,customerId:customer.id,amountMinor:1200,currency:'EUR',occurredAt:original.timestamp,detail:original.detail};
execFileSync('docker',['compose','exec','-T','kafka','/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','kafka:9092','--topic','billing.events','--property','parse.headers=true','--property','headers.separator=~','--property','parse.key=true','--property','key.separator=|'],{input:'X-Correlation-ID:'+trace+'~'+paid.id+'|'+JSON.stringify(event)+'\n',timeout:30000});
await new Promise(r=>setTimeout(r,2000));assert.equal((await provider()).charges.filter(x=>x.key==='invoice-'+paid.id).length,1);assert.equal((await provider()).attempts.filter(x=>x.key==='invoice-'+paid.id).length,1);assert.equal((await history(paid.id)).length,2);pass('Kafka replay does not recharge or duplicate audit document');
await mode('lost-response');const lost=await invoice('Synthetic lost provider response','lost-'+run);await state(lost.id,'PAID');const lostState=await provider();assert.equal(lostState.charges.filter(c=>c.key==='invoice-'+lost.id).length,1);assert.equal(lostState.attempts.filter(c=>c.key==='invoice-'+lost.id).length,2);pass('ambiguous provider response retried with one durable charge');
await mode('decline');const declined=await invoice('Synthetic decline','decline-'+run);await state(declined.id,'FAILED');await until(async()=>(await history(declined.id)).some(e=>e.type==='PaymentFailed'),'decline event');assert.equal((await provider()).charges.filter(c=>c.key==='invoice-'+declined.id).length,0);pass('business decline publishes PaymentFailed');
await mode('unavailable');const failed=await invoice('Synthetic provider outage','outage-'+run);await state(failed.id,'FAILED');
await until(async()=>(await history(failed.id)).some(e=>e.status==='DEAD_LETTER'),'dead letter indexed');
assert.equal((await provider()).attempts.filter(a=>a.key==='invoice-'+failed.id).length,3);const alerts=source(await api(8083,'/alerts'));assert(alerts.some(a=>a.invoiceId===failed.id&&a.status==='DEAD_LETTER'));assert(alerts.some(a=>a.invoiceId===failed.id&&a.status==='FAILED'));pass('three bounded attempts then DLT PaymentFailed and searchable alerts');
const search=await api(8083,'/search?customer='+customer.id+'&status=PAID&q=Provider&from=2020-01-01T00:00:00Z&to=2100-01-01T00:00:00Z');assert(source(search).some(e=>e.invoiceId===paid.id));assert(source(search).every(e=>e.customerId===customer.id&&e.status==='PAID'));pass('Elasticsearch customer status date range and full text search');
assert.equal((await api(8081,'/invoices/'+failed.id+'/payment-notifications',{received:false,reference:'synthetic-failure-notification'})).status,200);
assert.equal((await api(8081,'/invoices/'+failed.id+'/payment-notifications',{received:true,reference:'operator-confirmed-synthetic-payment'})).data.status,'PAID');
await until(async()=>(await history(failed.id)).some(e=>e.type==='PaymentReceived'),'reconciliation event');pass('billing publishes payment notifications and projects reconciliation');
await mode('success');const current=new Date().toISOString().slice(0,7);const scheduled=await subscription('Synthetic scheduled monthly invoice',current+'-01');
const scheduledInvoice=await until(async()=>{const rows=(await api(8081,'/invoices')).data;return rows.find(i=>i.subscriptionId===scheduled.id&&i.period===current);},'actual monthly scheduler');await state(scheduledInvoice.id,'PAID');
await new Promise(r=>setTimeout(r,6000));assert.equal((await api(8081,'/invoices')).data.filter(i=>i.subscriptionId===scheduled.id&&i.period===current).length,1);pass('actual accelerated monthly job and duplicate tick protection');
for(const port of [8081,8082,8083])assert((await api(port,'/actuator/prometheus')).data.includes('jvm_memory_used_bytes'));pass('Prometheus metrics exposed by all services');
for(const service of ['billing-service','payment-service','audit-search-service']){
 const logs=execFileSync('docker',['compose','logs','--no-color',service],{encoding:'utf8',maxBuffer:20*1024*1024});
 const entries=logs.split('\n').flatMap(line=>{try{return [JSON.parse(line.slice(line.indexOf('{')))];}catch{return [];}});
 assert(entries.some(e=>e.correlationId===trace),'Missing structured trace in '+service);
}pass('correlated structured JSON logs across all three services');
mkdirSync('docs/runs',{recursive:true});const result={timestamp:new Date().toISOString(),passed:checks.length,checks,timings};writeFileSync('docs/runs/smoke.json',JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result,null,2));
