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
  locate: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="7"/><circle cx="12" cy="12" r="2"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3"/></svg>',
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
// All countries and measurements below are explicitly illustrative, never inferred from a real profile.
const exampleRows = [
  ['ShatakVPN 803431','VLESS · Reality',42,'DE'], ['ShatakVPN 505086','VLESS · Reality',150,'US'],
  ['ShatakVPN 794674','Shadowsocks · ws',150,'FR'], ['ShatakVPN 805692','Shadowsocks · ws',150,'GB'],
  ['ShatakVPN 905086','VLESS · Reality',-1,'US'], ['ShatakVPN 803431','Shadowsocks · ws',42,'DE'],
  ['ShatakVPN 805886','Shadowsocks · ws',150,'GB'], ['ShatakVPN 803465','Shadowsocks · ws',150,'FR'],
  ['ShatakVPN 803436','VLESS · Reality',150,'DE'], ['ShatakVPN 805101','VLESS · Reality',0,'US'],
  ['ShatakVPN 805324','VMess · TCP',82,'FR'], ['ShatakVPN 805642','VLESS · Reality',0,'DE'],
  ['ShatakVPN 806128','VLESS · Reality',-1,'GB'], ['ShatakVPN 806442','Shadowsocks · ws',241,'FR'],
  ['ShatakVPN 806788','VMess · TCP',0,'DE'], ['ShatakVPN 807431','VLESS · Reality',64,'US'],
  ['ShatakVPN 807990','Shadowsocks · ws',-1,'DE'], ['ShatakVPN 808105','VLESS · Reality',0,'GB'],
  ['Work 100101','VLESS · Reality',34,'DE'], ['Work 100102','Shadowsocks · ws',0,'US'],
  ['Work 100103','VLESS · Reality',330,'FR'], ['Work 100104','VMess · TCP',108,'GB'],
  ['Work 100105','Shadowsocks · ws',72,'DE'], ['Work 100106','VLESS · Reality',-1,'US'],
  ['Free 100101','VMess · TCP',0,'FR'], ['Free 100102','VLESS · Reality',185,'DE'],
  ['Free 100103','Shadowsocks · ws',54,'GB'], ['Free 100104','VMess · TCP',99,'US'],
];
const flag = {
  DE:'<svg viewBox="0 0 30 20"><path fill="#111" d="M0 0h30v7H0z"/><path fill="#e02535" d="M0 7h30v6H0z"/><path fill="#ffce38" d="M0 13h30v7H0z"/></svg>',
  FR:'<svg viewBox="0 0 30 20"><path fill="#213e9b" d="M0 0h10v20H0z"/><path fill="#fafafa" d="M10 0h10v20H10z"/><path fill="#ee3d4b" d="M20 0h10v20H20z"/></svg>',
  US:'<svg viewBox="0 0 30 20"><path fill="#f9f9f9" d="M0 0h30v20H0z"/><path stroke="#d84853" stroke-width="1.55" d="M0 .8h30M0 3.9h30M0 7h30M0 10.1h30M0 13.2h30M0 16.3h30M0 19.4h30"/><path fill="#344d91" d="M0 0h13v10H0z"/><path stroke="#fff" stroke-width=".8" stroke-dasharray=".5 1.6" d="M1 2h11M1 4h11M1 6h11M1 8h11"/></svg>',
  GB:'<svg viewBox="0 0 30 20"><path fill="#243a77" d="M0 0h30v20H0z"/><path stroke="#fff" stroke-width="4" d="M0 0l30 20M30 0 0 20"/><path stroke="#d82e48" stroke-width="1.5" d="M0 0l30 20M30 0 0 20"/><path stroke="#fff" stroke-width="6" d="M15 0v20M0 10h30"/><path stroke="#d82e48" stroke-width="3" d="M15 0v20M0 10h30"/></svg>',
};
let servers = exampleRows.map(([name,type,delay,country],i) => ({
  id:`demo-${i+1}`, group:i<18?'sample':i<24?'work':'free', name,type,delay,country,age:''
}));
const filters = [
  ['all','All'],['top','Top'],['alive','Alive'],['failed','Failed'],['untested','Untested'],
];
const testNames = {real:'تأخیر واقعی', tcp:'بررسی سریع TCP', top:'تست دوبارهٔ برترها', failed:'تست دوبارهٔ ناموفق‌ها', current:'تست اتصال فعلی'};
const state = {group:'sample',filter:'all',sort:'default',query:'',selected:'demo-6',connected:true,connectionResult:42,connectionCountry:'DE',run:null,resume:null,demoProgress:true};
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
  modal.classList.remove('tool-modal');
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
  $('#group-name').textContent=group.name;$('#group-count').textContent='· '+all.length;
  $('#subscription-summary').textContent=group.summary;
  $('#test-count').textContent='';
  $('#visible-label').textContent=`${fa(visible.length)} کانفیگ نمایان`;
  $('#filters').innerHTML=filters.map(([id,label])=>`<button class="filter-chip" data-action="filter" data-id="${id}" aria-pressed="${state.filter===id}">${label}<span>${searched.filter(s=>matches(s,id)).length}</span></button>`).join('');
  $('#server-list').innerHTML=visible.length?visible.map(s=>{
    const kind=s.delay<0?'bad':s.delay===0?'':s.delay<150?'good':s.delay<300?'medium':'bad';
    const label=s.delay<0?'Failed':s.delay===0?'Untested':`${s.delay} ms`;
    return `<article class="server-card ${s.id===state.selected?'selected':''}" id="${s.id}"><button class="server-select" data-action="select" data-id="${s.id}" aria-label="انتخاب ${escapeHtml(s.name)}، ${label}" aria-pressed="${s.id===state.selected}"><span class="server-flag" aria-hidden="true">${flag[s.country] || ''}</span><span class="server-info"><span class="server-title">${escapeHtml(s.name)}</span><span class="server-sub">${escapeHtml(s.type)}</span></span><span class="ping ${kind}">${label}${s.age?`<small>${s.age}</small>`:''}</span></button><button class="row-menu" data-action="server-menu" data-id="${s.id}" aria-label="گزینه‌های ${escapeHtml(s.name)}">${icons.dots}</button></article>`;
  }).join(''):'<div class="empty-state">سروری در این محدوده پیدا نشد.<br>فیلتر یا عبارت جست‌وجو را تغییر دهید.</div>';
  if(focusedRow){
    const row=document.getElementById(focusedRow.id);
    const next=row && row.querySelector(`[data-action="${focusedRow.action}"]`);
    if(next) next.focus({preventScroll:true});
  }
  const run=state.run, progress=$('#progress');
  if(run){progress.hidden=false;progress.innerHTML=`<div class="progress-track" aria-hidden="true"><i style="width:${run.total?Math.round(run.done/run.total*100):0}%"></i></div><span>${fa(run.done)} از ${fa(run.total)} · موفق ${fa(run.ok)} · ناموفق ${fa(run.failed)}</span><button data-action="cancel-test">لغو</button>`}
  else if(state.resume?.remaining.length){progress.hidden=false;progress.innerHTML=`<span>تست متوقف شد · ${fa(state.resume.remaining.length)} مورد باقی‌مانده</span><button data-action="resume-test">ادامه</button>`}
  else if(state.demoProgress){progress.hidden=false;progress.innerHTML=`<div class="progress-track" aria-hidden="true"><i style="width:70%"></i></div><span>13/18 · Sample</span><button data-action="cancel-demo">Cancel</button>`}
  else {progress.hidden=true;progress.innerHTML=''}
  document.querySelectorAll('[data-action="test-real"],[data-action="test-options"]').forEach(el=>el.disabled=!!run);
  $('#connection-dot').classList.toggle('on',state.connected);
  $('#connection-label').textContent=!state.connected?'Disconnected · Needs test':state.connectionResult?`Connected · ${state.connectionResult} ms${state.connectionCountry?' · '+state.connectionCountry:''}`:'Connected · Needs test';
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
  state.demoProgress=false;
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
  const tile=(title,subtitle,action,icon)=>`<button class="tool-tile" data-action="${action}">${icons[icon]}<span><small>${subtitle}</small><strong>${title}</strong></span></button>`;
  const danger=(title,action,primary=false)=>`<button class="danger-button ${primary?'danger-primary':''}" data-action="${action}">${title}</button>`;
  const sortLabel=state.sort==='ping'?'Ping':state.sort==='name'?'Name':'Default';
  dialog('More Actions',`<div class="tools-grid">${tile('Update Subscription','به‌روزرسانی اشتراک','update-sub','refresh')}${tile('Export Configs','خروجی کانفیگ‌ها','export','export')}${tile('Restart','راه‌اندازی مجدد','restart','power')}</div>
    <section class="sort-panel"><div class="sort-panel-head"><strong>Save Sort Order</strong><button class="sort-indicator" data-action="save-order" aria-label="ذخیرهٔ ترتیب نمایشی"></button></div><div class="sort-panel-row"><button class="sort-select" data-action="sort">${state.sort==='default'?'Default / Ping / Name':sortLabel} <span class="chevron"></span></button><button class="save-sort-button" data-action="save-order">Save Permanently</button></div></section>
    <div class="aux-actions"><button data-action="group-details">اطلاعات گروه</button><button data-action="locate">یافتن سرور انتخاب‌شده</button></div>
    <div class="danger-zone"><h3><span aria-hidden="true">⚠</span> Danger Zone</h3><div class="danger-row">${danger('Remove Invalid','delete-invalid')}${danger('حذف تکراری‌ها','delete-duplicate')}</div>${danger('Delete All ⚠','delete-all',true)}<p>حذف امن در برنامهٔ اندروید هنوز تکمیل نشده است.</p></div>`);
  modal.classList.add('tool-modal');
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
  case 'choose-group':state.group=el.dataset.id;state.filter='all';state.demoProgress=false;closeModal();render();break;
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
  case 'start-current':state.connectionResult=82;state.connectionCountry=servers.find(s=>s.id===state.selected)?.country || null;closeModal();render();showToast('تست نمایشی اتصال انجام شد');break;
  case 'cancel-demo':state.demoProgress=false;render();break;
  case 'cancel-test':if(state.run){clearInterval(runTimer);state.resume={kind:state.run.kind,group:state.run.group,remaining:[...state.run.remaining]};state.run=null;render()}break;
  case 'resume-test':if(state.resume){const r=state.resume;startRun(r.kind,r.group,r.remaining)}break;
  case 'select':if(state.selected!==el.dataset.id){state.connectionResult=null;state.connectionCountry=null}state.selected=el.dataset.id;render();break;
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
  case 'toggle-connection':state.connected=!state.connected;state.connectionResult=null;state.connectionCountry=null;render();showToast('اتصال فقط شبیه‌سازی است');break;
  case 'connection-details':dialog('وضعیت اتصال',`<p class="modal-desc">${state.connected?'متصل به‌صورت نمایشی':'قطع است'} · هیچ اتصال VPN واقعی برقرار نمی‌شود.</p>`,btn('تست اتصال','test-current','primary')+btn('بستن','dismiss'));break;
  case 'test-current':confirmTest('current');break;
  case 'demo-only':closeModal();showToast('این عملیات در پیش‌نمایش انجام نمی‌شود');break;
 }
}
for(const [selector,icon] of [['[data-action="navigation"]','menu'],['[data-action="search"]','search'],['.header-actions [data-action="locate"]','locate'],['[data-action="import"]','plus'],['[data-action="tools"]','more'],['[data-action="close-search"]','close'],['[data-action="sort"]','sort'],['[data-action="toggle-connection"]','power'],['#test-icon','bolt'],['#subscription-icon','card'],['#search-icon','search']]) $(selector).innerHTML=icons[icon];
document.addEventListener('click',event=>{const el=event.target.closest('[data-action]');if(el && !el.disabled)handle(el.dataset.action,el)});
$('#search-input').addEventListener('input',event=>{state.query=event.target.value;render()});
modal.addEventListener('click',event=>{if(event.target===modal)closeModal()});
render();
