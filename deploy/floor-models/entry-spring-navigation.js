(()=>{
const rows=[{"key": "b1", "label": "B1", "buildingId": 1, "floorId": 3}, {"key": "f4", "label": "4F", "buildingId": 1, "floorId": 7}, {"key": "f1", "label": "1F", "buildingId": 1, "floorId": 4}, {"key": "f2", "label": "2F", "buildingId": 1, "floorId": 5}, {"key": "f3", "label": "3F", "buildingId": 1, "floorId": 6}, {"key": "f5", "label": "5F", "buildingId": 1, "floorId": 8}, {"key": "f6", "label": "6F", "buildingId": 1, "floorId": 9}, {"key": "f7", "label": "7F", "buildingId": 1, "floorId": 10}, {"key": "f8", "label": "8F", "buildingId": 1, "floorId": 11}, {"key": "f9", "label": "9F", "buildingId": 1, "floorId": 12}, {"key": "machine", "label": "屋顶机房层", "buildingId": 1, "floorId": 13}, {"key": "roof", "label": "屋面", "buildingId": 1, "floorId": 13}];
let panel,key='';

const sums={"b1": "3a1d72b590726562bb8da44c470418e28df0cacf89fdf7bba262641a42c76bd7", "f4": "a68a3f1b4604cef0d79768e5f6901f97a8e97891cf4c4f4aa05133c17644f121", "f1": "4b6751e9fb31cae21cee7f1f22e18719023503d22ca3313d9ba52eef4e8ea957", "f2": "3d1361e975e59a12c0a406b2996b6712b00aa00739270eaaeb299e39a1783f2f", "f3": "619e3108bac7e0df4aca603bc40bb6a63d718e77948df7516878e0371b37583d", "f5": "b0fe0f9241c306ac00b17342cebb6e4f3dcd01e49332d65476e484afc8252e27", "f6": "e71d3f16387939db187bdc3a75f4d1d30c75ffad0ec6e2818c6e4eeba7862f7e", "f7": "17499dfb5e5244a08262985e09a8a8938aa45e038dc77ad120daadb20fd39e4d", "f8": "7c0983df60b1ec0c7a07b624e8f356149fddb9d7ae59e6645da48f4d73b1c6cf", "f9": "9d17f1f730bb6a1525e230bd2020d322ecd456a67f90c9ad31a50cec01074192", "machine": "45050dec3606b3f44a1d898424af413e2399e197cfd122d69146efc7778a6e8a", "roof": "e54af2ce314da9c47a582c9b1aa61813027946cd6ee1403715d7fc74b8e3eb15"};
let warm=null, timer=0, expiry=0, target=null;
const urlFor=r=>'/floor-model-ui-r2/'+r.key+'/viewer.html';
function discard(){
  clearTimeout(timer);clearTimeout(expiry);warm?.frame.remove();warm=null;target=null;
}
function stageFloor(r,button){
  if(target!==r.key || !button.isConnected || !button.matches(':hover,:focus-visible'))return;
  const token=localStorage.getItem('token'), cache=window[Symbol.for('dehui.floor.packs.v2')];
  if(!token)return;
  if(cache?.token!==token || !cache.files.has(sums[r.key])){
    timer=setTimeout(()=>stageFloor(r,button),180);return;
  }
  const frame=document.createElement('iframe');
  frame.inert=true;frame.tabIndex=-1;frame.setAttribute('aria-hidden','true');
  frame.style.cssText='position:fixed;left:0;top:105vh;width:'+button.closest('section.directory').clientWidth+'px;height:86vh;border:0;pointer-events:none';
  frame.title=r.label+' 模型预载';frame.src=urlFor(r);document.body.append(frame);
  warm={key:r.key,frame,token};
}
function anticipate(e){
  if(!Element.prototype.moveBefore || navigator.connection?.saveData || e.pointerType==='touch')return;
  const button=e.target.closest?.('.dhic-has-tower .folders .tile');if(!button)return;
  const r=rows.find(r=>r.label===button.querySelector('strong')?.textContent.trim());if(!r)return;
  clearTimeout(expiry);
  if(target===r.key)return;
  discard();target=r.key;timer=setTimeout(()=>stageFloor(r,button),900);
}
document.addEventListener('pointerover',anticipate);
document.addEventListener('pointerout',e=>{
  const button=e.target.closest?.('.dhic-has-tower .folders .tile');
  if(button && !button.contains(e.relatedTarget)){clearTimeout(expiry);expiry=setTimeout(discard,600)}
});
window.addEventListener('pagehide',discard);

function mount(){
 const u=new URL(location.href),head=document.querySelector('section.directory > header.directory-head');
 const matches=rows.filter(r=>u.pathname==='/assets/spaces'&&u.searchParams.get('buildingId')===String(r.buildingId)&&u.searchParams.get('floorId')===String(r.floorId));
 if(!matches.length||!head||!head.parentElement.querySelector('.room-list')){panel?.remove();panel=null;key='';return}
 const r=matches.find(x=>u.hash==='#model-'+x.key)||matches.find(x=>x.key==='roof')||matches[0];
 if(panel?.isConnected&&panel.previousElementSibling===head&&key===r.key)return;
 panel?.remove();panel=document.createElement('section');key=r.key;
 if(matches.length>1){const nav=document.createElement('nav');nav.setAttribute('aria-label','屋顶模型');nav.style.cssText='display:flex;gap:14px;padding:8px 0';for(const item of matches){const a=document.createElement('a');a.href='#model-'+item.key;a.textContent=item.label;if(r===item)a.style.fontWeight='650';nav.append(a)}panel.append(nav)}
 head.after(panel);
 const reuse=warm?.key===r.key && warm.token===localStorage.getItem('token') && warm.frame.isConnected;
 const frame=reuse?warm.frame:document.createElement('iframe');
 if(reuse){warm=null;panel.moveBefore(frame,null);frame.inert=false;frame.removeAttribute('aria-hidden');frame.removeAttribute('tabindex')}
 else{frame.src=urlFor(r);panel.append(frame)}
 discard();frame.allowFullscreen=true;frame.title=r.label+' 六专业三维模型';frame.style.cssText='display:block;width:100%;height:clamp(590px,86vh,1120px);border:0;background:white';

}
const refreshRoute=()=>requestAnimationFrame(mount)
if(window.navigation)window.navigation.addEventListener('currententrychange',refreshRoute)
else for(const method of ['pushState','replaceState']){const original=history[method];history[method]=function(...args){const result=original.apply(this,args);refreshRoute();return result}}
new MutationObserver(mount).observe(document.body,{childList:true,subtree:true});addEventListener('hashchange',mount);addEventListener('popstate',mount);mount()
})()
