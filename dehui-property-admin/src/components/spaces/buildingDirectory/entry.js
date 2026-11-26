import './style.css'
import {readBytes} from './bytes'
import {readArchive,warmFloor} from './warmFloor'
import {installFloorPreviews,primePreviews} from './floorPreviews'
let warmTimer
const supported = /^(?:[1-9]F|B[12]|屋面)$/
let directory, panel, controller, scene, selection, pointerFloor, focusFloor, retry, version = 0
const tileName = tile => tile?.querySelector('strong')?.textContent?.trim()
function select(name) {
  name = supported.test(name) ? name : null
  if (selection === name) return
  selection = name
  directory?.querySelectorAll('.folders .tile').forEach(el => el.classList.toggle('dhic-previewing', tileName(el) === name))
  clearTimeout(warmTimer)
  if(name)warmTimer=setTimeout(()=>warmFloor(name,controller.signal),700)
  scene?.setFloor(name)
}
function dispose() {
  clearTimeout(warmTimer)
  version++
  controller?.abort()
  scene?.dispose()
  directory?.querySelectorAll('.dhic-previewing').forEach(el => el.classList.remove('dhic-previewing'))
  directory?.classList.remove('dhic-has-tower','dhic-global-directory')
  panel?.remove()
  panel = scene = directory = selection = pointerFloor = focusFloor = null
}
async function start() {
  const v = ++version
  const status = panel.querySelector('.tower-status')
  function message(text, action) {
    if (v !== version) return
    status.textContent = text
    if (action) {
      retry = document.createElement('button')
      retry.type = 'button'; retry.textContent = '重试'; retry.onclick = action; status.append(retry)
    }
  }
  try {
    message('正在读取建筑主体总成…')
    const pending=readBytes('tower',controller.signal)
    pending.catch(()=>{})
    const { createScene } = await import('./scene.js')
    if (v !== version) return
    scene = createScene(panel, controller.signal, message, select)
    scene.setFloor(selection)
    await scene.open(pending)
  } catch (e) {
    if (v !== version) return
    scene?.dispose(); scene = null
    message(e.response?.status === 403 ? '当前账号无权查看楼宇模型' : '三维模型未能打开', start)
  }
}
function mount(el,global) {
  directory = el
  controller = new AbortController()
  const options = {signal:controller.signal}
  panel = document.createElement('aside');panel.className = 'dhic-tower';panel.setAttribute('aria-label','整栋建筑三维模型')
  panel.innerHTML = '<header><strong>建筑主体总成</strong><span></span></header><div class="tower-stage"></div><div class="tower-plan-label" hidden></div><div class="tower-status" role="status"></div>'
  panel.dataset.global=String(global)
  if(global){
    directory.classList.add('dhic-global-directory')
    panel.querySelector('strong').textContent='德汇创新中心 · 全楼三维总览'
  }
  directory.classList.add('dhic-has-tower');directory.append(panel)
  primePreviews()

  const tile = target => target?.closest?.('.folders .tile')
  directory.addEventListener('pointerover', e => {
    if (e.pointerType === 'touch' || !tile(e.target)) return
    pointerFloor = tileName(tile(e.target));select(pointerFloor)
  },options)
  directory.addEventListener('pointerout', e => {
    if (!tile(e.target) || tile(e.target)===tile(e.relatedTarget)) return
    pointerFloor = tileName(tile(e.relatedTarget));focusFloor=null;select(pointerFloor)
  },options)
  let drag, suppressClick=false
  directory.addEventListener('wheel', e=>{
    const card=tile(e.target)
    if(!card || !scene || e.ctrlKey)return
    e.preventDefault();select(tileName(card))
    const unit=e.deltaMode===1?16:e.deltaMode===2?120:1
    scene.orbit((e.deltaX || e.deltaY)*unit*.007,Math.abs(e.deltaY)*unit*.0018)
  },{...options,passive:false})
  directory.addEventListener('pointerdown', e=>{
    suppressClick=false
    if(e.button===0 && tile(e.target))drag={card:tile(e.target),x:e.clientX,y:e.clientY,id:e.pointerId}
  },options)
  directory.addEventListener('pointermove',e=>{
    if(!drag || drag.id!==e.pointerId || !e.buttons)return
    const dx=e.clientX-drag.x,dy=e.clientY-drag.y
    if(!suppressClick && Math.hypot(dx,dy)<4)return
    suppressClick=true;drag.card.setPointerCapture(e.pointerId);select(tileName(drag.card))
    scene?.orbit(dx*.017,dy*.014);drag.x=e.clientX;drag.y=e.clientY
  },options)
  directory.addEventListener('pointerup',e=>{
    if(drag && suppressClick && !drag.card.contains(document.elementFromPoint(e.clientX,e.clientY))){
      focusFloor=pointerFloor=null;select(null)
    }
    drag=null
  },options)
  directory.addEventListener('pointercancel',()=>{drag=null;suppressClick=false},options)
  directory.addEventListener('click',e=>{if(suppressClick && e.detail){e.preventDefault();e.stopImmediatePropagation();suppressClick=false}}, {...options,capture:true})
  directory.addEventListener('focusin', e => {focusFloor=tileName(tile(e.target));select(focusFloor || pointerFloor)},options)
  directory.addEventListener('focusout', e => {focusFloor=tileName(tile(e.relatedTarget));select(focusFloor || pointerFloor)},options)
  directory.addEventListener('keydown', e => {if(e.key === 'Escape'){focusFloor=null;pointerFloor=null;select(null)}},options)
  start()
}
function sync() {
  const el = document.querySelector('section.directory')
  const title=el?.querySelector('.directory-head h2')?.textContent.trim()
  const global=title==='楼宇目录' && [...el.querySelectorAll('.folders .tile strong')].some(t=>t.textContent.trim()==='德汇创新中心')
  const floors=title==='德汇创新中心' && el?.querySelector(':scope > .floor-tools')
  const matches=location.pathname==='/assets/spaces' && (global||floors)
  if(matches && directory===el && panel?.isConnected && panel.dataset.global===String(global))return
  if(directory)dispose()
  if(matches)mount(el,global)

}
if (!window.__dhicTowerDirectory && location.hostname === 'wuye.mingda.com.cn') {
  window.__dhicTowerDirectory = true
  window[Symbol.for('dhic.readArchive')]=readArchive
  installFloorPreviews()
  new MutationObserver(sync).observe(document.body,{subtree:true,childList:true})
  window.addEventListener('popstate',sync)
  sync()
}
