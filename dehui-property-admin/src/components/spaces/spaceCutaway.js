import * as T from 'three'

// Schematic room envelopes use the registered selection footprints. Actual wall
// openings and dimensions remain in the architectural linework underneath.
export function addSpaceCutaway(mesh,room,rooms,retain,pickables) {
  const points=room.points
  const isLift=room.name.includes('电梯井'), isLobby=room.name==='电梯厅'
  const xs=points.map(p=>p[0]), zs=points.map(p=>p[1])
  const x=(Math.min(...xs)+Math.max(...xs))/2,z=(Math.min(...zs)+Math.max(...zs))/2
  const width=Math.max(...xs)-Math.min(...xs), depth=Math.max(...zs)-Math.min(...zs)
  const tint=isLift ? 0x526d86 : isLobby ? 0x927851 : 0x638d82
  const boundary=[]
  points.forEach(([u,v],i)=>{
    const [s,t]=points[(i+1)%points.length]
    boundary.push(u,0,v,u,1.2,v,u,1.2,v,s,1.2,t)
  })
  const frame=retain(new T.BufferGeometry())
  frame.setAttribute('position',new T.Float32BufferAttribute(boundary,3))
  mesh.add(new T.LineSegments(frame,retain(new T.LineBasicMaterial({color:tint}))))
  if (!isLift) return
  const landing=rooms.find(r=>r.name===(room.name.startsWith('DT-5') ? '合用前室' : '电梯厅'))
  if (!landing) return
  const facing=Math.sign(landing.points.reduce((sum,p)=>sum+p[0],0)/landing.points.length-x) || 1
  const material=retain(new T.MeshLambertMaterial({color:0xabbecb}))
  function panel(w,h,d,u,y,v) {
    const part=new T.Mesh(retain(new T.BoxGeometry(w,h,d)),material)
    part.position.set(u,y,v)
    part.userData=mesh.userData
    mesh.add(part)
    pickables.push(part)
  }
  // Open-topped symbol, facing its landing. Three panels let the cabin read
  // separately from the actual shaft outline without asserting car dimensions.
  const cw=width*.70, cd=depth*.72, thick=.07, height=.95
  panel(thick,height,cd,x-facing*cw/2,height/2,z)
  for (const side of [-1,1]) panel(cw,height,thick,x,height/2,z+side*cd/2)
  panel(cw,.08,cd,x,.08,z)
}
