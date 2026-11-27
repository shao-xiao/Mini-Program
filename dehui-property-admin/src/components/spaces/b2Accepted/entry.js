import {authorize,openingParts,readPart} from './ranges'

const systems={暖通:'hvac',强电:'electric',消防:'fire',弱电:'weak'}
let session, pictures, pending, active, overlay, scheduled, version=0
let warmed=false
function isB2(){
  const q=new URLSearchParams(location.search)
  return location.pathname==='/assets/spaces' && q.get('buildingId')==='1' && q.get('floorId')==='14'
}
function clear(){overlay?.remove();overlay=null}
function prime(){
  const token=localStorage.getItem('token')
  if(!token)return
  if(session!==token){pictures=null;pending=null;warmed=false;session=token;clear()}
  if(pending)return pending
  const task=Promise.all([authorize(['previews']),readPart('previews')]).then(([,bytes])=>{
    if(token!==localStorage.getItem('token')||session!==token)return
    pictures=JSON.parse(new TextDecoder().decode(bytes))
  })
  pending=task
  task.catch(()=>{if(task===pending)pending=null})
  return task
}
function warm(key){
  if(navigator.connection?.saveData)return Promise.resolve()
  return Promise.allSettled(openingParts(key).map(readPart))
}
function ready(drawing,frame){
  if(!frame)return !!drawing.querySelector('canvas,.blank')
  const doc=frame.contentDocument
  return !!doc?.querySelector('.e-status,[role=alert]') || /拖动旋转|失败/.test(doc?.querySelector('.installation .hint')?.textContent||'')
}
function render(){
  scheduled=false
  if(!isB2()){
    if(active||pending){active=null;pictures=null;pending=null;warmed=false;version++;clear()}
    return
  }
  const task=prime()
  const drawing=document.querySelector('.drawing')
  const nav=drawing?.querySelector('nav[aria-label="图纸专业"]')
  const name=nav?.querySelector('[aria-pressed=true]')?.textContent.trim()
  if(!name||!task)return
  if(active?.name===name&&active.drawing===drawing&&active.session===session)return
  const serial=++version
  active={name,drawing,session};clear()
  const selected=systems[name]
  if(selected)warm(selected)
  const started=performance.now()
  function check(){
    if(serial!==version||!isB2()||!drawing.isConnected||session!==localStorage.getItem('token')){if(serial===version)clear();return}
    const frame=selected?drawing.querySelector('iframe'):null
    if((!selected||frame)&&ready(drawing,frame)){
      clear()
      if(!warmed&&!navigator.connection?.saveData){
        warmed=true
        setTimeout(async()=>{for(const key of Object.values(systems)){if(!isB2()||serial!==version)break;await warm(key)}},400)
      }
      return
    }
    if(pictures?.[name]&&!overlay){
      drawing.style.position='relative'
      overlay=document.createElement('div')
      overlay.dataset.b2Preview=name
      overlay.style.cssText='position:absolute;z-index:2;left:0;right:0;height:clamp(320px,70vh,880px);background:#f1f4f4;pointer-events:none'
      overlay.style.top=(nav.offsetTop+nav.offsetHeight+3)+'px'
      const img=new Image();img.alt='B2 '+name+'首帧预览'
      img.style.cssText='width:100%;height:100%;object-fit:contain'
      img.src='data:image/webp;base64,'+pictures[name]
      const status=document.createElement('small');status.textContent='正在准备交互模型…'
      status.style.cssText='position:absolute;bottom:8px;left:8px;background:#fffe;padding:3px 7px'
      overlay.append(img,status);drawing.append(overlay)
    }
    if(performance.now()-started<60000)setTimeout(check,45)
    else clear()
  }
  check()
}
function schedule(){if(!scheduled){scheduled=true;requestAnimationFrame(render)}}
new MutationObserver(schedule).observe(document.body,{subtree:true,childList:true,attributes:true,attributeFilter:['aria-pressed']})
for(const event of ['pointerover','focusin','touchstart'])document.addEventListener(event,e=>{
  const tile=e.target.closest?.('.folders .tile')
  if(tile?.querySelector('strong')?.textContent.trim()==='B2')prime()
  if(isB2()){
    const key=systems[e.target.closest?.('.drawing>nav button')?.textContent.trim()]
    if(key)warm(key)
  }
},{passive:true})
window.addEventListener('popstate',schedule)
schedule()
