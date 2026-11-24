// Model coordinates are in metres. Paint belongs to the roadway, labels to the camera.
import * as T from 'three'

export const selectionColor = '#7635a4'
export const garageColors = { C:0xf2d175, D:0x98b5d7, lanes:0x78878c, rooms:0xa2c8ba }
export const roadLevel = .026

// The traffic overlay contains the visible footprint only. A409/A410 give the
// uphill direction and the 3.60 m transition rising from -9.200 to -8.930.
export function rampProfile(lane) {
  const north = lane.name === '北侧坡道图示'
  if (!north && lane.name !== '东侧坡道图示') return null
  const xs = lane.points.map(p => p[0]), zs = lane.points.map(p => p[1])
  const minX = Math.min(...xs), maxX = Math.max(...xs), minZ = Math.min(...zs), maxZ = Math.max(...zs)
  const length = north ? maxX-minX : maxZ-minZ
  const rise = d => Math.min(d,3.6)*.075 + Math.max(0,d-3.6)*.15
  return {
    length,
    height: (x,z) => roadLevel + rise(T.MathUtils.clamp(north ? x-minX : maxZ-z,0,length)),
    ends: d => north ? [[minX+d,minZ],[minX+d,maxZ]] : [[minX,maxZ-d],[maxX,maxZ-d]]
  }
}

export function rampGeometry(profile) {
  const vertices = []
  const stations = [0, Math.min(3.6,profile.length), profile.length]
  for (let i=1; i<stations.length; i++) {
    const [a,b] = profile.ends(stations[i-1]), [c,d] = profile.ends(stations[i])
    for (const p of [a,b,c,b,d,c]) vertices.push(p[0],profile.height(...p)-roadLevel,p[1])
    for (const [p,q] of [[a,c],[b,d],...(i===2 ? [[c,d]] : [])]) {
      const top = v => [v[0],profile.height(...v)-roadLevel,v[1]]
      const low = v => [v[0],0,v[1]]
      vertices.push(...top(p),...low(p),...top(q),...top(q),...low(p),...low(q))
    }
  }
  const geometry = new T.BufferGeometry()
  geometry.setAttribute('position',new T.Float32BufferAttribute(vertices,3))
  geometry.computeVertexNormals()
  return geometry
}

export function addGarageMarkings(scene, garage, retain) {
  const batches = new Map()
  const flat = () => roadLevel
  function paint(points, color, height=flat) {
    const coords = batches.get(color) || []
    const polygon = points.map(([x,z]) => new T.Vector2(x,z))
    for (const tri of T.ShapeUtils.triangulateShape(polygon,[])) {
      for (const index of tri) {
        const [x,z] = points[index]
        coords.push(x,height(x,z)+.016,z)
      }
    }
    batches.set(color,coords)
  }
  for (const lane of garage.lanes) {
    const height = rampProfile(lane)?.height || flat
    if (lane.centerline) {
      const [[x,z],[endX,endZ]] = lane.centerline
      const length = Math.hypot(endX-x,endZ-z)
      const dx = (endX-x)/length, dz = (endZ-z)/length
      const at = (d,w) => [x+d*dx-w*dz,z+d*dz+w*dx]
      for (let d=0; d<length; d+=2.8) {
        const end = Math.min(d+1.6,length)
        paint([at(d,-.07),at(d,.07),at(end,.07),at(end,-.07)],0xf9d672,height)
      }
    }
    for (const [x,z,deg] of lane.arrows) {
      const angle = deg*Math.PI/180, c=Math.cos(angle), s=Math.sin(angle)
      const arrow=[[1.35,0],[.1,-.46],[.2,-.13],[-1.1,-.13],[-1.1,.13],[.2,.13],[.1,.46]]
      paint(arrow.map(([u,v])=>[x+u*c-v*s,z+u*s+v*c]),0xffffff,height)
    }
  }
  for (const [a,b,c] of garage.crossings || []) {
    for (let z=a[1]; z<c[1]; z+=.48) {
      const end=Math.min(z+.22,c[1])
      paint([[a[0],z],[b[0],z],[b[0],end],[a[0],end]],0xffffff,()=>.05)
    }
  }
  for (const bay of garage.parking) {
    const centre = bay.points.reduce(([x,z],[u,v])=>[x+u/4,z+v/4],[0,0])
    const inset = bay.points.map(([x,z])=>[x+(centre[0]-x)*.035,z+(centre[1]-z)*.035])
    bay.points.forEach((p,i) => {
      const j=(i+1)%4
      paint([p,bay.points[j],inset[j],inset[i]],0xfffcf2,()=>.04)
    })
  }
  for (const [color,vertices] of batches) {
    const geometry=retain(new T.BufferGeometry())
    geometry.setAttribute('position',new T.Float32BufferAttribute(vertices,3))
    scene.add(new T.Mesh(geometry,retain(new T.MeshBasicMaterial({color,side:T.DoubleSide}))))
  }
}
