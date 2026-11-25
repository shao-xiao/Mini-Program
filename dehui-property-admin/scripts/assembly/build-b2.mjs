// Run from the project root with the five input/output directories documented in the assembly guide.
import assert from 'node:assert/strict'
import {createHash} from 'node:crypto'
import fs from 'node:fs/promises'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import * as T from 'three'
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js'
import {GLTFExporter} from 'three/addons/exporters/GLTFExporter.js'
import {build} from 'vite'
import JSZip from 'jszip'
import {buildStations} from '../../src/components/spaces/plumbingStations.js'

const [drawings,packages,waterFile,roomsDir,out]=process.argv.slice(2)
assert(out,'Usage: node build-b2.mjs <drawings-root> <B2-archive-directory> <plumbing-v2.zip> <room-glbs> <output>')
const sha=bytes=>createHash('sha256').update(bytes).digest('hex')
const zip=async file=>JSZip.loadAsync(await fs.readFile(file))
const read=async (archive,name)=>JSON.parse(await archive.file(name).async('string'))
const [architecture,detail,spaces,water]=await Promise.all([
  zip(path.join(packages,'B2-A101.zip')),zip(path.join(packages,'B2-core-A401.zip')),
  zip(path.join(packages,'B2-space-specs.zip')),zip(waterFile)])
const [model,core,survey,plumbing]=await Promise.all([read(architecture,'model.json'),read(detail,'core.json'),read(spaces,'spaces.json'),read(water,'plumbing.json')])
const refs=[...model.sources.filter(s=>['A101','A301'].includes(s.code)),core.source,plumbing.sources.find(s=>s.code==='P301')]
for(const ref of refs){
  const dir=path.join(drawings,ref.code.startsWith('A')?'02建筑':'05给排水')
  const names=(await fs.readdir(dir)).filter(n=>n.includes('-'+ref.code+'-')&&n.endsWith('.pdf'))
  assert.equal(names.length,1,ref.code)
  assert.equal(sha(await fs.readFile(path.join(dir,names[0]))),ref.sha256,ref.code)
}
assert([core.reference,survey.reference,plumbing.reference].every(v=>v===refs[0].sha256))
assert.equal(survey.detailReference,core.source.sha256)
plumbing.extractionDigest=sha(await water.file('plumbing.json').async('uint8array'))
const station=buildStations(plumbing,survey.rooms).find(s=>s.model==='BF-DZ-20')
assert(station,'P301 revision or station mapping changed')

globalThis.FileReader=class {readAsArrayBuffer(blob){blob.arrayBuffer().then(value=>{this.result=value;this.onloadend?.()})}}
const origin=new T.Vector3(), loader=new GLTFLoader(), exporter=new GLTFExporter()
const id='B2/污水处理间（西侧）'
function tag(obj,key,label,kind,fields={}){
  obj.userData.asset={key,label,kind,...fields}
  return obj
}
function group(key,label,kind,fields){
  const obj=tag(new T.Group(),key,label,kind,fields)
  obj.name=label
  return obj
}
const building=group(model.buildingCode,'德汇创新中心','整栋与场地',{说明:'首批装配仅包含 B2，水平坐标暂沿用 B2 底图基准'})
const floor=group('B2','地下二层','楼层',{地坪标高:model.elevation+' m',范围:'建筑底图、24 个空间、西侧处理间排水样段'})
const structure=group('B2/结构','B2 建筑底图','建筑与结构构件',{来源:'A101',展示剖切高度:'1.20 m'})
const roomIndex=new Map(),clips=[]
building.add(floor);floor.add(structure)
function surface(points,color,height){
  const shape=new T.Shape(points.map(([x,z])=>new T.Vector2(x,-z)))
  const geometry=height?new T.ExtrudeGeometry(shape,{depth:height,bevelEnabled:false}):new T.ShapeGeometry(shape)
  geometry.rotateX(-Math.PI/2)
  return new T.Mesh(geometry,new T.MeshStandardMaterial({color,roughness:.85,side:T.DoubleSide}))
}
structure.add(surface(model.geometry.outline,0xd4dfdf,0))
for(const poly of [...model.geometry.walls,...model.geometry.columns])structure.add(surface(poly,0x667c86,1.2))
const linePoints=model.geometry.lines.flatMap(line=>line.slice(1).flatMap(([x,z],i)=>[...new T.Vector3(line[i][0],.012,line[i][1]).toArray(),x,.012,z]))
const lines=new T.BufferGeometry()
lines.setAttribute('position',new T.Float32BufferAttribute(linePoints,3))
structure.add(new T.LineSegments(lines,new T.LineBasicMaterial({color:0x73888c})))
const index=JSON.parse(await fs.readFile(path.join(roomsDir,'房间索引.json'),'utf8'))
assert.equal(index.length,survey.rooms.length)
for(const [i,record] of index.entries()){
  const bytes=await fs.readFile(path.join(roomsDir,record.file))
  const gltf=await loader.parseAsync(bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength),'')
  const node=gltf.scene.children.find(n=>n.userData.module?.room===record.room)
  assert(node && !roomIndex.has(record.room))
  const schema=node.userData.module, source=survey.rooms.find(s=>s.name===record.room)
  assert.deepEqual(schema.floorOrigin,record.floorOrigin)
  assert.equal(source.points.length,schema.footprint.length)
  schema.footprint.forEach(([x,z],j)=>assert(Math.hypot(x+schema.floorOrigin[0]-source.points[j][0],z+schema.floorOrigin[2]-source.points[j][1])<.00001))
  // Give animated nodes unique names inside the assembled floor before export.
  for(const clip of gltf.animations){
    clip.name=record.room+' '+clip.name
    const renamed=new Map()
    for(const track of clip.tracks){
      const binding=T.PropertyBinding.parseTrackName(track.name)
      const target=T.PropertyBinding.findNode(node,binding.nodeName) || renamed.get(binding.nodeName)
      assert(target,'Unresolved track '+track.name)
      renamed.set(binding.nodeName,target)
      target.name='room'+i+'_'+binding.nodeName
      track.name=target.name+'.'+binding.propertyName
    }
    clips.push(clip)
  }
  tag(node,'B2/'+record.room,record.room,'房间与功能空间',{来源:schema.source})
  node.position.fromArray(schema.floorOrigin);floor.add(node);roomIndex.set(record.room,node)
}
const room=roomIndex.get(station.name), spec=room.userData.module.equipment
assert.equal(spec.model,station.model);assert.equal(spec.performance,station.performance)
const equipment=room.children.find(child=>child.children.length > 0 && child.position.distanceTo(new T.Vector3(spec.anchor[0],0,spec.anchor[1]))<1e-6)
assert(equipment)
tag(equipment,id+'/BF-DZ-20',station.machine,'设备机组',{型号:station.model,图注规格:station.performance,来源:'P301',机身尺寸:'示意外形，厂家尺寸待核'})
equipment.name=station.machine
// The schematic pump is part of the existing unit. Group its meshes once in assembly space.
const pump=group(id+'/BF-DZ-20/pump','提升泵组件','设备与末端',{说明:'机组内示意部件，单泵型号待核'})
// Select the coaxial motor and cooling fins by their authored relative positions.
const motorParts=equipment.children.filter(m=>Math.abs(m.position.x-.36)<1e-5 && Math.abs(m.position.z-.66)<1e-5 && m.position.y<.8)
assert.equal(motorParts.length,5,'Recheck equipment parts after changing the unit')
pump.position.set(.36,.57,.66)
equipment.add(pump);equipment.updateWorldMatrix(true,true);for(const mesh of motorParts)pump.attach(mesh)

const route=group(id+'/YWL-2','压力排水支路','管线与连接件',{去向:station.outlet,来源:'P301',安装标高:'待核；示意管中心在房间地面以上 1.61 m',管径:'待核，模型采用示意管径'})
route.position.set(spec.anchor[0],1.61,spec.anchor[1]);room.add(route)
const local=([x,z])=>new T.Vector3(x-station.anchor[0],0,z-station.anchor[1])
const mat=new T.MeshStandardMaterial({color:0x147f88,roughness:.45})
function cylinder(a,b,r){
  const delta=b.clone().sub(a)
  const mesh=new T.Mesh(new T.CylinderGeometry(r,r,delta.length(),20),mat)
  mesh.position.copy(a).lerp(b,.5);mesh.quaternion.setFromUnitVectors(new T.Vector3(0,1,0),delta.normalize())
  return mesh
}
const flowTracks=[]
const discharge=station.runs.filter(r=>r.medium==='排水')
for(const run of discharge){
  const [a,b]=run.points.map(local),mesh=cylinder(a,b,.09)
  mesh.name='P301-'+run.id
  mesh.userData.segment={source:'P301',index:run.id,pointsXZ:run.points,direction:'排出'}
  route.add(mesh)
  const direction=b.clone().sub(a).normalize()
  const marker=new T.Mesh(new T.ConeGeometry(.055,.15,12),new T.MeshBasicMaterial({color:0x93ffed}))
  marker.name='outflow_'+run.id
  marker.quaternion.setFromUnitVectors(new T.Vector3(0,1,0),direction)
  const start=a.clone().addScaledVector(direction,.09),end=b.clone().addScaledVector(direction,-.09)
  start.y=end.y=.096
  marker.position.copy(start)
  route.add(marker)
  flowTracks.push(new T.VectorKeyframeTrack(marker.name+'.position',[0,2.5],[...start.toArray(),...end.toArray()]))
}
const flowClip=new T.AnimationClip('压力排水流向示意',2.5,flowTracks)
clips.push(flowClip)
const riser=local(station.outlets.find(o=>o.medium==='排水').point)
const terminal=cylinder(riser,riser.clone().add(new T.Vector3(0,1,0)),.09)
terminal.userData.port={reference:station.outlet,adjacentFloorPort:null,verticalExtent:'示意'}
terminal.name=station.outlet;route.add(terminal)
const residual=riser.distanceTo(local(discharge.at(-1).points[1]))
const connections=[
  {from:equipment.userData.asset.key,to:route.userData.asset.key,medium:'压力排水',依据:'P301',设备侧平面偏差米:local(discharge[0].points[0]).length()},
  {from:route.userData.asset.key,to:station.outlet,图面端点与立管中心距离米:residual,跨层端点:'待核'}]
route.userData.connections=connections

await fs.mkdir(out,{recursive:true})
const assets={}
async function save(key,file,node,animations=[]){
  const bytes=Buffer.from(await exporter.parseAsync(node,{binary:true,animations}))
  const check=await loader.parseAsync(bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength),'')
  assert(!new T.Box3().setFromObject(check.scene).isEmpty())
  assert.equal(check.animations.length,animations.length)
  const ids=new Set();check.scene.traverse(n=>{if(n.userData.asset){assert(!ids.has(n.userData.asset.key));ids.add(n.userData.asset.key)}})
  for(const anim of check.animations)for(const track of anim.tracks)
    assert(T.PropertyBinding.findNode(check.scene,T.PropertyBinding.parseTrackName(track.name).nodeName))
  assets[key]={file,sha256:sha(bytes),bytes:bytes.length}
  await fs.writeFile(path.join(out,file),bytes)
  return check
}
// Standalone source modules retain their parent placement in the assembly manifest.
const placements={}
async function standalone(key,name,node,animations=[]){
  placements[key]={parent:node.parent?.userData.asset?.key,translation:node.position.toArray()}
  const copy=node.clone(true);copy.position.copy(origin)
  return save(key,name,copy,animations)
}
await standalone('pump','01-提升泵组件.glb',pump)
await standalone('route','02-压力排水支路.glb',route,[flowClip])
await standalone('structure','03-B2建筑底图.glb',structure)
await standalone('equipment','04-隔油提升机组.glb',equipment)
await standalone('room','05-西侧污水处理间.glb',room,[flowClip])
floor.position.y=model.elevation
await standalone('floor','06-B2楼层.glb',floor,clips)
const assembly=await save('building','07-德汇创新中心-首批B2.glb',building,clips)
assembly.scene.updateMatrixWorld(true)
for(const [name,original] of roomIndex){
  const node=assembly.scene.getObjectByProperty('name',original.name)
  assert(node && node.getWorldPosition(new T.Vector3()).distanceTo(original.getWorldPosition(new T.Vector3()))<1e-5,name)
}
const manifest={version:1,units:'m',up:'Y',building:model.buildingCode,scope:'B2 首批，含 24 个空间和西侧处理间排水样段',sources:refs.map(({code,sha256})=>({code,sha256})),assets,placements,connections,rooms:index.map(r=>({name:r.room,origin:r.floorOrigin})),pending:['其余楼层、全楼轴网水平配准及其他专业','设备机身厂家尺寸、管径和安装标高','YWL-2 跨层接口配准']}
await fs.writeFile(path.join(out,'装配清单.json'),JSON.stringify(manifest,null,2))
const here=path.dirname(fileURLToPath(import.meta.url))
const compiled=await build({configFile:false,root:here,logLevel:'error',build:{write:false,lib:{entry:path.join(here,'viewer.js'),formats:['iife'],name:'B2Assembly'}}})
const js=compiled[0].output.find(x=>x.type==='chunk').code
const preview={building:building.userData.asset,elevation:model.elevation}
for(const key of ['structure','room','floor'])preview[key]=(await fs.readFile(path.join(out,assets[key].file))).toString('base64')
const html=(await fs.readFile(path.join(here,'viewer.html'),'utf8')).replace('/*ASSETS*/',()=>JSON.stringify(preview)).replace('/*VIEWER*/',()=>js.replace(/<\/script/gi,'<\\/script'))
await fs.writeFile(path.join(out,'打开模型.html'),html)
console.log('Verified',roomIndex.size,'rooms,',clips.length,'animations,',Object.keys(assets).length,'GLBs. Port drawing residual:',residual.toFixed(4),'m')
