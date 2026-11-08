'use strict';

// Standalone illustration only: no network, VPN service, real profiles or persistent storage.
const icons = {
  menu: '<svg viewBox="0 0 24 24"><path d="M4 6h16M4 12h16M4 18h16"/></svg>',
  search: '<svg viewBox="0 0 24 24"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4 4"/></svg>',
  plus: '<svg viewBox="0 0 24 24"><path d="M12 4v16M4 12h16"/></svg>',
  more: '<svg viewBox="0 0 24 24"><circle cx="5" cy="12" r="1.2"/><circle cx="12" cy="12" r="1.2"/><circle cx="19" cy="12" r="1.2"/></svg>',
  dots: '<svg viewBox="0 0 24 24"><circle cx="5" cy="12" r="1.8"/><circle cx="12" cy="12" r="1.8"/><circle cx="19" cy="12" r="1.8"/></svg>',
  close: '<svg viewBox="0 0 24 24"><path d="m6 6 12 12M18 6 6 18"/></svg>',
  sort: '<svg viewBox="0 0 24 24"><path d="M4 7h16M4 17h16M8 4v6M16 14v6"/></svg>',
  power: '<svg viewBox="0 0 24 24"><path d="M12 2v10M6.3 5.8a8 8 0 1 0 11.4 0"/></svg>',
  bolt: '<svg viewBox="0 0 24 24"><path d="M13 1 4 13h7l-1 10 10-14h-7z"/></svg>',
  card: '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="3"/><path d="M3 10h18M7 15h3"/></svg>',
  refresh: '<svg viewBox="0 0 24 24"><path d="M20 7v5h-5M4 17v-5h5M5 9a7 7 0 0 1 12-3l3 6M19 15a7 7 0 0 1-12 3l-3-6"/></svg>',
  export: '<svg viewBox="0 0 24 24"><path d="M12 16V3M7 8l5-5 5 5M5 14v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5"/></svg>',
};
const groups = [
  {id:'sample', name:'Shatak', summary:'مصرف نمونه ۲۳ از ۱۰۰ گیگابایت · انقضا ۲۹ روز'},
  {id:'work', name:'Work', summary:'مصرف نمونه ۸ از ۵۰ گیگابایت · انقضا ۱۶ روز'},
  {id:'free', name:'Free', summary:'اطلاعات اشتراک نمونه موجود نیست'},
];
const values = [42, 150, -1, 82, 0, 241, 0, 64, -1, 195, 34, 0, 330, 108, 72, -1, 0, 185, 54, 99, 0, 146, -1, 0, 63, 171, 0, 95];
let servers = values.map((delay,i) => ({
  id:`demo-${i+1}`, group:i<18?'sample':i<24?'work':'free',
  name:`${i%4===0?'Newface':'ShatakVPN'} ${803431+i*121}`, type:i%3===0?'VLESS · Reality':i%3===1?'Shadowsocks · ws':'VMess · TCP',
  delay, age:delay===0?'':i%3===0?'۲h':'۱۲m'
}));
const filters = [
  ['all','همه'],['top','برتر'],['alive','موفق'],['untested','تست‌نشده'],['failed','ناموفق'],
];
const testNames = {real:'تأخیر واقعی', tcp:'بررسی سریع TCP', top:'تست دوبارهٔ برترها', failed:'تست دوبارهٔ ناموفق‌ها', current:'تست اتصال فعلی'};
const state = {group:'sample',filter:'all',sort:'default',query:'',selected:'demo-1',connected:false,connectionResult:null,run:null,resume:null};
const $ = s => document.querySelector(s);
const modal = $('#modal');
let toastTimer, runTimer;
const escapeHtml = v => String(v).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const fa = n => new Intl.NumberFormat('fa-IR').format(n);
const currentGroup = () => groups.find(g => g.id===state.group);
const groupServers = () => servers.filter(s => s.group===state.group);
const searchServers = () => groupServers().filter(s => (s.name+' '+s.type).toLowerCase().includes(state.query.trim().toLowerCase()));
const matches = (s,f) => f==='all' || (f==='top' && s.delay>0 && s.delay<=100) || (f==='alive' && s.delay>0) || (f==='untested' && s.delay===0) || (f==='failed' && s.delay<0);
function visibleServers(){
  const found=searchServers().filter(s=>matches(s,state.filter));
  if(state.sort==='name') return found.sort((a,b)=>a.name.localeCompare(b.name));
  if(state.sort==='ping') return found.sort((a,b)=>((a.delay>0?0:a.delay===0?1:2)-(b.delay>0?0:b.delay===0?1:2)) || ((a.delay>0?a.delay:0)-(b.delay>0?b.delay:0)));
  return found;
}
function showToast(message){const el=$('#toast');el.textContent=message;el.classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove('show'),2600)}
function closeModal(){if(modal.open) modal.close()}
function dialog(title,body,buttons=''){
  $('#modal-content').innerHTML=`<div class="modal-header"><h2 id="modal-title">${escapeHtml(title)}</h2><button class="icon-btn" data-action="dismiss" aria-label="بستن">${icons.close}</button></div>${body}${buttons?`<div class="modal-buttons">${buttons}</div>`:''}`;
  if(!modal.open) modal.showModal();
}
const btn=(label,action,style='',extra='')=>`<button class="btn ${style}" data-action="${action}" ${extra}>${label}</button>`;
const option=(label,detail,action,data='',check=false)=>`<button class="option" data-action="${action}" ${data}><span><strong>${label}</strong>${detail?`<small>${detail}</small>`:''}</span>${check?'<span class="choice">✓</span>':''}</button>`;
function render(){
  const active=document.activeElement;
  const focusedRow=active && active.closest('#server-list') && active.dataset.id
    ? {id:active.dataset.id, action:active.dataset.action} : null;
  const group=currentGroup(), all=groupServers(), searched=searchServers(), visible=visibleServers();
  $('#group-name').textContent=group.name;$('#group-count').textContent='· '+fa(all.length);
  $('#subscription-summary').textContent=group.summary;
  $('#test-count').textContent='· '+fa(visible.length);
  $('#visible-label').textContent=`${fa(visible.length)} کانفیگ نمایان`;
  $('#filters').innerHTML=filters.map(([id,label])=>`<button class="filter-chip" data-action="filter" data-id="${id}" aria-pressed="${state.filter===id}">${label}<span>${fa(searched.filter(s=>matches(s,id)).length)}</span></button>`).join('');
  $('#server-list').innerHTML=visible.length?visible.map(s=>{
    const kind=s.delay<0?'bad':s.delay===0?'':s.delay<150?'good':s.delay<300?'medium':'bad';
    const label=s.delay<0?'ناموفق':s.delay===0?'تست نشده':`${s.delay} ms`;
    return `<article class="server-card ${s.id===state.selected?'selected':''}" id="${s.id}"><button class="server-select" data-action="select" data-id="${s.id}" aria-label="انتخاب ${escapeHtml(s.name)}، ${label}" aria-pressed="${s.id===state.selected}"><span class="server-avatar" aria-hidden="true">${s.name[0]}</span><span class="server-info"><span class="server-title">${escapeHtml(s.name)}</span><span class="server-sub">${escapeHtml(s.type)}</span></span><span class="ping ${kind}">${label}${s.age?`<small>${s.age}</small>`:''}</span></button><button class="row-menu" data-action="server-menu" data-id="${s.id}" aria-label="گزینه‌های ${escapeHtml(s.name)}">${icons.dots}</button></article>`;
  }).join(''):'<div class="empty-state">سروری در این محدوده پیدا نشد.<br>فیلتر یا عبارت جست‌وجو را تغییر دهید.</div>';
  if(focusedRow){
    const row=document.getElementById(focusedRow.id);
    const next=row && row.querySelector(`[data-action="${focusedRow.action}"]`);
    if(next) next.focus({preventScroll:true});
  }
  const run=state.run, progress=$('#progress');
  if(run){progress.hidden=false;progress.innerHTML=`<div class="progress-track" aria-hidden="true"><i style="width:${run.total?Math.round(run.done/run.total*100):0}%"></i></div><span>${fa(run.done)} از ${fa(run.total)} · موفق ${fa(run.ok)} · ناموفق ${fa(run.failed)}</span><button data-action="cancel-test">لغو</button>`}
  else if(state.resume?.remaining.length){progress.hidden=false;progress.innerHTML=`<span>تست متوقف شد · ${fa(state.resume.remaining.length)} مورد باقی‌مانده</span><button data-action="resume-test">ادامه</button>`}
  else {progress.hidden=true;progress.innerHTML=''}
  document.querySelectorAll('[data-action="test-real"],[data-action="test-options"]').forEach(el=>el.disabled=!!run);
  $('#connection-dot').classList.toggle('on',state.connected);
  $('#connection-label').textContent=!state.connected?'قطع است · نیاز به تست':state.connectionResult?`متصل · ${state.connectionResult} ms · نمونه`:'متصل · نیاز به تست';
  const power=$('[data-action="toggle-connection"]');power.classList.toggle('off',!state.connected);power.setAttribute('aria-label',state.connected?'قطع اتصال':'اتصال');
}
function confirmTest(kind){
  if(kind==='current'){
    if(!state.connected){closeModal();showToast('برای تست اتصال فعلی ابتدا وصل شوید');return}
    dialog(testNames[kind],'<p class="modal-desc">این یک تست نمایشی برای اتصال فعلی است و هیچ شبکه‌ای را بررسی نمی‌کند.</p>',btn('شروع','start-current','primary')+btn('انصراف','dismiss'));
    return;
  }
  const visible=visibleServers();
  const targets=visible.filter(s=>kind==='top'?matches(s,'top'):kind==='failed'?matches(s,'failed'):true).map(s=>s.id);
  const context=`${fa(targets.length)} کانفیگ · گروه ${escapeHtml(currentGroup().name)} · فیلتر ${filters.find(f=>f[0]===state.filter)[1]}`;
  state.pendingTest={kind,group:state.group,targets};
  dialog(testNames[kind],`<p class="modal-desc">${context}${state.query?' · جست‌وجو فعال است':''}</p><p class="modal-desc">نتایج این پیش‌نمایش ساختگی هستند. تست واقعی در وب اجرا نمی‌شود.</p>${!targets.length?'<div class="modal-warning">در این محدوده کانفیگی وجود ندارد.</div>':''}`,btn('شروع تست','start-test','primary',targets.length?'':'disabled')+btn('انصراف','dismiss'));
}
function startRun(kind,group,ids){
  state.resume=null;
  state.run={kind,group,remaining:[...ids],total:ids.length,done:0,ok:0,failed:0};
  clearInterval(runTimer);
  runTimer=setInterval(()=>{
    const run=state.run;if(!run){clearInterval(runTimer);return}
    const id=run.remaining.shift(), target=servers.find(s=>s.id===id && s.group===run.group);
    if(target){const n=Number(id.split('-')[1]);target.delay=n%7===0?-1:38+(n*37)%240;target.age='';run.done++;if(target.delay<0)run.failed++;else run.ok++}
    if(!run.remaining.length){clearInterval(runTimer);state.run=null;showToast('تست نمایشی به پایان رسید')}
    render();
  },550);
  render();
}
function tools(){
  const tile=(name,action,icon)=>`<button class="tool-tile" data-action="${action}">${icons[icon]}<span>${name}</span></button>`;
  dialog('ابزارها',`<div class="tools-grid">${tile('به‌روزرسانی اشتراک','update-sub','refresh')}${tile('خروجی کانفیگ‌ها','export','export')}${tile('راه‌اندازی مجدد','restart','power')}</div><p class="section-title">گروه و ترتیب</p>${option('اطلاعات گروه','','group-details')}${option('ذخیرهٔ ترتیب نمایشی','','save-order')}${option('یافتن سرور انتخاب‌شده','','locate')}<div class="danger-zone"><p class="section-title">⚠ مدیریت و حذف</p>${option('حذف نامعتبرها','','delete-invalid')}${option('حذف تکراری‌ها','','delete-duplicate')}${option('حذف همه','','delete-all')}<p class="modal-desc">در برنامهٔ اندروید حذف دسته‌ای ایمن هنوز تکمیل نشده است.</p></div>`);
}
function groupsDialog(){dialog('انتخاب گروه',`<p class="modal-desc">گروه‌ها در این پیش‌نمایش نمونه هستند.</p>${groups.map(g=>option(escapeHtml(g.name),`${fa(servers.filter(s=>s.group===g.id).length)} کانفیگ نمونه`,'choose-group',`data-id="${g.id}"`,g.id===state.group)).join('')}`)}
function testOptions(){dialog('گزینه‌های تست',Object.entries(testNames).map(([id,name])=>option(name,id==='tcp'?'فقط بررسی دسترسی؛ مقدار نمایشی':id==='real'?'تأخیر واقعی؛ نتیجه نمایشی':'محدوده قبل از شروع نمایش داده می‌شود','test-kind',`data-id="${id}"`)).join(''))}
function sortDialog(){const entries=[['default','ترتیب ذخیره‌شده'],['ping','پینگ کمتر'],['name','نام']];dialog('مرتب‌سازی نمایشی',`<p class="modal-desc">این انتخاب به‌تنهایی ترتیب ذخیره‌شده را تغییر نمی‌دهد.</p>${entries.map(([id,label])=>option(label,'','choose-sort',`data-id="${id}"`,state.sort===id)).join('')}`)}
function showExport(){dialog('انتخاب محدودهٔ خروجی',`<p class="modal-desc">در برنامهٔ واقعی لینک‌ها ممکن است حاوی اطلاعات محرمانه باشند. این نسخه چیزی به کلیپ‌بورد کپی نمی‌کند.</p>${option('گروه فعلی',`${fa(groupServers().length)} مورد نمایشی`,'export-result')}${option('فهرست نمایان',`${fa(visibleServers().length)} مورد نمایشی`,'export-result')}${option('سرور انتخاب‌شده','یک مورد نمایشی','export-result')}`)}
function showDanger(action){
  const names={'delete-all':'حذف همه','delete-invalid':'حذف نامعتبرها','delete-duplicate':'حذف تکراری‌ها'};
  dialog(names[action],'<div class="modal-warning">این فقط نمایش ظاهری است و چیزی را حذف نمی‌کند. حذف دسته‌ای امن و بازگردانی در برنامهٔ اندروید هنوز تکمیل نشده است؛ پیش از استفاده پشتیبان بگیرید.</div>',btn('متوجه شدم','dismiss','primary'));
}
function showServerMenu(id){const s=servers.find(x=>x.id===id);if(!s)return;dialog(s.name,`<p class="modal-desc">گزینه‌های نمایشی برای یک سرور نمونه</p>${option('اشتراک‌گذاری با QR','','demo-only')}${option('کپی لینک','','demo-only')}${option('ویرایش','','demo-only')}${option('حذف','','demo-only')}`)}
function handle(action,el){
 switch(action){
  case 'dismiss':closeModal();break;
  case 'navigation':dialog('منوی برنامه','<p class="modal-desc">این پیش‌نمایش فقط صفحهٔ سرورها و ابزارهای آن را شبیه‌سازی می‌کند.</p>',btn('بستن','dismiss','primary'));break;
  case 'groups':groupsDialog();break;
  case 'choose-group':state.group=el.dataset.id;state.filter='all';closeModal();render();break;
  case 'group-details':dialog('اطلاعات گروه',`<p class="modal-desc">${escapeHtml(currentGroup().name)}<br>${escapeHtml(currentGroup().summary)}</p><p class="modal-desc">تمام اطلاعات این بخش ساختگی است.</p>`,btn('بستن','dismiss','primary'));break;
  case 'search':$('#search-box').hidden=false;$('#search-input').focus();break;
  case 'close-search':$('#search-box').hidden=true;$('#search-input').value='';state.query='';render();break;
  case 'filter':state.filter=el.dataset.id;render();break;
  case 'sort':sortDialog();break;
  case 'choose-sort':state.sort=el.dataset.id;closeModal();render();break;
  case 'test-options':testOptions();break;
  case 'test-kind':confirmTest(el.dataset.id);break;
  case 'test-real':confirmTest('real');break;
  case 'start-test':{const pending=state.pendingTest;closeModal();if(pending && pending.targets.length)startRun(pending.kind,pending.group,pending.targets);break}
  case 'start-current':state.connectionResult=82;closeModal();render();showToast('تست نمایشی اتصال انجام شد');break;
  case 'cancel-test':if(state.run){clearInterval(runTimer);state.resume={kind:state.run.kind,group:state.run.group,remaining:[...state.run.remaining]};state.run=null;render()}break;
  case 'resume-test':if(state.resume){const r=state.resume;startRun(r.kind,r.group,r.remaining)}break;
  case 'select':state.selected=el.dataset.id;render();break;
  case 'server-menu':showServerMenu(el.dataset.id);break;
  case 'tools':tools();break;
  case 'import':dialog('افزودن کانفیگ','<p class="modal-desc">این نسخهٔ نمایشی کانفیگ واقعی را وارد یا ذخیره نمی‌کند.</p>'+option('از کلیپ‌بورد','','demo-only')+option('از فایل','','demo-only')+option('ساخت دستی','','demo-only'));break;
  case 'update-sub':closeModal();showToast('به‌روزرسانی فقط شبیه‌سازی است');break;
  case 'restart':closeModal();showToast('راه‌اندازی مجدد فقط شبیه‌سازی است');break;
  case 'export':showExport();break;
  case 'export-result':closeModal();showToast('هیچ داده‌ای کپی نشد؛ پیش‌نمایش است');break;
  case 'save-order':dialog('ذخیرهٔ ترتیب',`<p class="modal-desc">گروه ${escapeHtml(currentGroup().name)} · ${fa(groupServers().length)} کانفیگ نمونه</p><p class="modal-desc">در پیش‌نمایش هیچ ترتیبی واقعاً ذخیره نمی‌شود.</p>`,btn('متوجه شدم','dismiss','primary'));break;
  case 'locate':{closeModal();const node=document.getElementById(state.selected);if(node){node.scrollIntoView({block:'center',behavior:'smooth'});showToast('سرور انتخاب‌شده')}else showToast('سرور انتخاب‌شده در این فیلتر یا گروه نیست');break}
  case 'delete-all':case 'delete-invalid':case 'delete-duplicate':showDanger(action);break;
  case 'toggle-connection':state.connected=!state.connected;state.connectionResult=null;render();showToast('اتصال فقط شبیه‌سازی است');break;
  case 'connection-details':dialog('وضعیت اتصال',`<p class="modal-desc">${state.connected?'متصل به‌صورت نمایشی':'قطع است'} · هیچ اتصال VPN واقعی برقرار نمی‌شود.</p>`,btn('تست اتصال','test-current','primary')+btn('بستن','dismiss'));break;
  case 'test-current':confirmTest('current');break;
  case 'demo-only':closeModal();showToast('این عملیات در پیش‌نمایش انجام نمی‌شود');break;
 }
}
for(const [selector,icon] of [['[data-action="navigation"]','menu'],['[data-action="search"]','search'],['[data-action="import"]','plus'],['[data-action="tools"]','more'],['[data-action="close-search"]','close'],['[data-action="sort"]','sort'],['[data-action="toggle-connection"]','power'],['#test-icon','bolt'],['#subscription-icon','card'],['#search-icon','search']]) $(selector).innerHTML=icons[icon];
document.addEventListener('click',event=>{const el=event.target.closest('[data-action]');if(el && !el.disabled)handle(el.dataset.action,el)});
$('#search-input').addEventListener('input',event=>{state.query=event.target.value;render()});
modal.addEventListener('click',event=>{if(event.target===modal)closeModal()});
render();
