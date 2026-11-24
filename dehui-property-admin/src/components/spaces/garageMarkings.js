// All plan geometry uses metres. The traffic sheet supplies identifiers and directions.
import * as T from 'three'

export const selectionColor = '#7635a4'
export const garageColors = { C:0xf2d175, D:0x98b5d7, lanes:0x889293, rooms:0x81a69e }

export function addGarageMarkings(scene, garage, bounds, retain) {
  const width = bounds.max.x - bounds.min.x, depth = bounds.max.z - bounds.min.z
  const canvas = document.createElement('canvas')
  const resolution = 2048 / Math.max(width, depth)
  canvas.width = Math.ceil(width * resolution)
  canvas.height = Math.ceil(depth * resolution)
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.scale(resolution, resolution)
  ctx.translate(-bounds.min.x, -bounds.min.z)
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.font = '600 1.05px sans-serif'
  ctx.fillStyle = '#24323c'
  for (const bay of garage.parking) {
    const x = bay.points.reduce((n,p) => n + p[0],0) / bay.points.length
    const z = bay.points.reduce((n,p) => n + p[1],0) / bay.points.length
    const [a,b,c] = bay.points
    const longer = Math.hypot(b[0]-a[0],b[1]-a[1]) >= Math.hypot(c[0]-b[0],c[1]-b[1])
    const angle = longer ? Math.atan2(b[1]-a[1],b[0]-a[0]) : Math.atan2(c[1]-b[1],c[0]-b[0])
    ctx.save(); ctx.translate(x,z); ctx.rotate(angle); ctx.fillText(bay.name,0,0); ctx.restore()
  }
  ctx.lineWidth = .14
  ctx.strokeStyle = '#f9d672'
  ctx.setLineDash([1.6,1.2])
  for (const {centerline} of garage.lanes) {
    if (!centerline) continue
    ctx.beginPath(); ctx.moveTo(...centerline[0]); ctx.lineTo(...centerline[1]); ctx.stroke()
  }
  ctx.setLineDash([])
  ctx.fillStyle = '#fff'
  for (const [a,b,c] of garage.crossings || []) {
    for (let z=a[1]; z<c[1]; z+=.48) ctx.fillRect(a[0],z,b[0]-a[0],.22)
  }
  ctx.fillStyle = '#fff'
  for (const lane of garage.lanes) {
    for (const [x,z,angle] of lane.arrows) {
      ctx.save(); ctx.translate(x,z); ctx.rotate(angle*Math.PI/180)
      ctx.beginPath(); ctx.moveTo(1.35,0); ctx.lineTo(.1,-.46); ctx.lineTo(.2,-.13)
      ctx.lineTo(-1.1,-.13); ctx.lineTo(-1.1,.13); ctx.lineTo(.2,.13); ctx.lineTo(.1,.46)
      ctx.closePath(); ctx.fill(); ctx.restore()
    }
  }
  const texture = retain(new T.CanvasTexture(canvas))
  texture.colorSpace = T.SRGBColorSpace
  const material = retain(new T.MeshBasicMaterial({map:texture,transparent:true,depthWrite:false}))
  const plane = new T.Mesh(retain(new T.PlaneGeometry(width,depth)),material)
  plane.rotation.x = -Math.PI/2
  plane.position.set(bounds.min.x+width/2,.075,bounds.min.z+depth/2)
  plane.renderOrder = 2
  scene.add(plane)
}
