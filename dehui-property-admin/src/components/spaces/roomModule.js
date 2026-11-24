import * as T from 'three'
import {localRoomStrokes} from './roomLinework'
import {garageColors} from './garageMarkings'
import {addSpaceCutaway} from './spaceCutaway'

// X/Z correspond to the registered floor plan. Local Y is height in metres.
export function defineRoomModule(room, rooms, station, floorElevation, strokes=[]) {
  const x = Math.min(...room.points.map(point => point[0]))
  const z = Math.min(...room.points.map(point => point[1]))
  const local = ([u,w]) => [u-x,w-z]
  const floorField = room.fields?.find(([label]) => ['地面标高（图注）','建筑标高（图注）'].includes(label))
  const level = floorField && /^([−-]?\d+(?:\.\d+)?)\s*m$/.exec(floorField[1])
  const y = level && Number.isFinite(floorElevation) ? Number(level[1].replace('−','-')) - floorElevation : 0
  const landing = room.name.includes('电梯井') && rooms.find(r =>
    r.name === (room.name.startsWith('DT-5') ? '合用前室' : '电梯厅'))
  return {
    name:room.name, source:room.source, fields:room.fields, origin:[x,y,z],
    points:room.points.map(local),
    landing:landing ? {...landing,points:landing.points.map(local)} : null,
    strokes:localRoomStrokes(strokes,room.points,[x,y,z]),
    station, equipmentAnchor:station ? local(station.anchor) : null
  }
}

// Mount this root at the original origin or move/rotate it as one unit.
// Picking, scene visibility and route overlays belong to the host view.
export function createRoomInstance(definition,{retain,motion,parts}) {
  const {name,points,landing,station,equipmentAnchor,origin}=definition
  const root=new T.Group()
  root.name=name
  root.position.fromArray(origin)
  const shape=new T.Shape(points.map(([x,z])=>new T.Vector2(x,-z)))
  const geometry=retain(new T.ShapeGeometry(shape))
  geometry.rotateX(-Math.PI/2)
  const color=name==='电梯厅' ? 0xe6d4aa : name.includes('电梯井') ? 0x92a7b7 : garageColors.rooms
  const material=retain(new T.MeshLambertMaterial({color,side:T.DoubleSide,polygonOffset:true,polygonOffsetFactor:-1,polygonOffsetUnits:-1}))
  const slab=new T.Mesh(geometry,material)
  root.add(slab)
  addSpaceCutaway(root,definition,landing ? [landing] : [],retain,[])
  if(definition.strokes.length) {
    const lines=retain(new T.BufferGeometry())
    lines.setAttribute('position',new T.Float32BufferAttribute(definition.strokes,3))
    root.add(new T.LineSegments(lines,retain(new T.LineBasicMaterial({color:0x62747a}))))
  }
  let assembly
  if (station) {
    assembly=parts.createEquipment(station)
    assembly.equipment.position.set(equipmentAnchor[0],0,equipmentAnchor[1])
    root.add(assembly.equipment)
    root.userData.hasEquipment=true
  } else {
    motion.equipment(root,definition,[])
  }
  root.userData.module={
    version:1, room:name, units:'m', up:'Y', floorOrigin:origin, footprint:points,
    source:definition.source, fields:definition.fields, enclosureCut:1.2,
    equipmentGeometry:'示意外形', ...(station ? {
      equipment:{name:station.machine,model:station.model,performance:station.performance,anchor:equipmentAnchor},
      risers:station.outlets.map(({medium,point})=>({medium,reference:medium==='通气'?station.vent:station.outlet,
        localXZ:[point[0]-origin[0],point[1]-origin[2]],source:'P301'}))
    } : {})
  }
  return {root,slab,assembly}
}
