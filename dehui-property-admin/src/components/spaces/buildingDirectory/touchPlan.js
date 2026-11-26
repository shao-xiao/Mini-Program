// Selected floor previews use a separate orthographic camera from the exterior.
export function bindPlanGestures(canvas, enabled, orbit, zoom, signal) {
  const fingers = new Map()
  function distance() {
    const [a,b] = fingers.values()
    return Math.hypot(a.x-b.x, a.y-b.y)
  }
  const options = {signal}
  canvas.addEventListener('pointerdown', e => {
    if (e.pointerType !== 'touch' || !enabled()) return
    fingers.set(e.pointerId, {x:e.clientX,y:e.clientY})
    canvas.setPointerCapture(e.pointerId)
  }, options)
  canvas.addEventListener('pointermove', e => {
    const previous = fingers.get(e.pointerId)
    if (!previous || !enabled()) return
    const before = fingers.size === 2 ? distance() : 0
    fingers.set(e.pointerId, {x:e.clientX,y:e.clientY})
    if (fingers.size === 1) {
      const scale = Math.PI / Math.max(160, canvas.clientWidth)
      orbit((previous.x-e.clientX)*scale, (e.clientY-previous.y)*scale)
    } else if (before > 1 && fingers.size === 2) zoom(distance()/before)
  }, options)
  const end = e => fingers.delete(e.pointerId)
  for (const event of ['pointerup','pointercancel','lostpointercapture']) canvas.addEventListener(event,end,options)
}
