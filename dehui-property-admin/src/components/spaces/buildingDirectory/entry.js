import './style.css'
const supported = /^(?:[1-9]F|B[12]|屋面)$/
let directory, panel, controller, scene, selection, pointerFloor, focusFloor, retry, version = 0
const tileName = tile => tile?.querySelector('strong')?.textContent?.trim()
function select(name) {
  name = supported.test(name) ? name : null
  if (selection === name) return
  selection = name
  directory?.querySelectorAll('.folders .tile').forEach(el => el.classList.toggle('dhic-previewing', tileName(el) === name))
  scene?.setFloor(name)
}
function dispose() {
  version++
  controller?.abort()
  scene?.dispose()
  directory?.querySelectorAll('.dhic-previewing').forEach(el => el.classList.remove('dhic-previewing'))
  directory?.classList.remove('dhic-has-tower')
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
    const { createScene } = await import('./scene.js')
    if (v !== version) return
    scene = createScene(panel, controller.signal, message, select)
    scene.setFloor(selection)
    await scene.open()
  } catch (e) {
    if (v !== version) return
    scene?.dispose(); scene = null
    message(e.response?.status === 403 ? '当前账号无权查看楼宇模型' : '三维模型未能打开', start)
  }
}
function mount(el) {
  directory = el
  controller = new AbortController()
  const options = {signal:controller.signal}
  panel = document.createElement('aside');panel.className = 'dhic-tower';panel.setAttribute('aria-label','整栋建筑三维模型')
  panel.innerHTML = '<header><strong>建筑主体总成</strong><span></span></header><div class="tower-stage"></div><div class="tower-plan-label" hidden></div><div class="tower-status" role="status"></div>'
  directory.classList.add('dhic-has-tower');directory.append(panel)
  const tile = target => target?.closest?.('.folders .tile')
  directory.addEventListener('pointerover', e => {
    if (e.pointerType === 'touch' || !tile(e.target)) return
    pointerFloor = tileName(tile(e.target));select(pointerFloor)
  },options)
  directory.addEventListener('pointerout', e => {
    if (!tile(e.target) || tile(e.target)===tile(e.relatedTarget)) return
    pointerFloor = tileName(tile(e.relatedTarget));select(pointerFloor || focusFloor)
  },options)
  directory.addEventListener('focusin', e => {focusFloor=tileName(tile(e.target));select(focusFloor || pointerFloor)},options)
  directory.addEventListener('focusout', e => {focusFloor=tileName(tile(e.relatedTarget));select(focusFloor || pointerFloor)},options)
  directory.addEventListener('keydown', e => {if(e.key === 'Escape'){focusFloor=null;pointerFloor=null;select(null)}},options)
  start()
}
function sync() {
  const el = document.querySelector('section.directory')
  const matches = location.pathname === '/assets/spaces' && el?.querySelector(':scope > .floor-tools') &&
    el.querySelector('.directory-head h2')?.textContent.trim() === '德汇创新中心'
  if (matches && directory === el && panel?.isConnected) return
  if (directory) dispose()
  if (matches) mount(el)
}
if (!window.__dhicTowerDirectory && location.hostname === 'wuye.mingda.com.cn') {
  window.__dhicTowerDirectory = true
  new MutationObserver(sync).observe(document.body,{subtree:true,childList:true})
  window.addEventListener('popstate',sync)
  sync()
}
