import * as T from 'three'

// Equipment geometry is in local metres around the surveyed connection point.
// Enclosures are symbolic unless a manufacturer's dimensional drawing is linked.
export function createStationParts(retain) {
  const cube=retain(new T.BoxGeometry()), tube=retain(new T.CylinderGeometry(1,1,1,16))
  const cone=retain(new T.ConeGeometry(1,1,12)), ring=retain(new T.TorusGeometry(.16,.035,8,18))
  const mats=new Map(), up=new T.Vector3(0,1,0)
  const v=(x,y,z)=>new T.Vector3(x,y,z)
  const colors={steel:'#728b98', dark:'#394e58', pump:'#397989', valve:'#bc8653'}
  function part(parent,geometry,color,pos,scale,opacity=1){
    const key=color+'/'+opacity
    if(!mats.has(key)) mats.set(key,retain(new T.MeshPhongMaterial({color,shininess:65,
      transparent:opacity<1,opacity,depthWrite:opacity===1})))
    const mesh=new T.Mesh(geometry,mats.get(key))
    mesh.position.copy(pos);mesh.scale.copy(scale);parent.add(mesh)
    return mesh
  }
  function pipe(parent,a,b,r,color,opacity=1){
    const delta=b.clone().sub(a)
    const mesh=part(parent,tube,color,a.clone().lerp(b,.5),v(r,delta.length(),r),opacity)
    mesh.quaternion.setFromUnitVectors(up,delta.normalize())
    return mesh
  }
  function createEquipment(s) {
    const lift=s.kind==='处理间'
    const equipment=new T.Group()
    const base=part(equipment,cube,colors.dark,v(0,.09,0),v(1.9,.18,1.44))
    const interceptor = lift && s.model === 'BF-DZ-20'
    if (interceptor) {
      part(equipment,cube,colors.steel,v(0,.62,0),v(1.76,.98,1.1))
      for (const x of [-.58,0,.58]) {
        part(equipment,cube,'#b9c8cb',v(x,1.15,0),v(.53,.07,1.02))
        part(equipment,cube,colors.dark,v(x,1.21,0),v(.17,.055,.07))
      }
    } else if (lift) {
      part(equipment,tube,colors.steel,v(0,.66,0),v(.65,1.04,.65))
      part(equipment,tube,colors.dark,v(0,1.19,0),v(.69,.09,.69))
      part(equipment,tube,'#b9c8cb',v(0,1.25,0),v(.45,.06,.45))
    } else {
      part(equipment,tube,'#64adb9',v(0,.53,0),v(.79,.88,.79),.30)
    }
    if(lift){
      // Elevation fittings are schematic. The room boundary uses the A101 survey.
      part(equipment,cube,'#cfdbdc',v(-.69,1.05,-.73),v(.88,2.10,.09))
      part(equipment,cube,'#e4ecec',v(-.69,1.72,-.55),v(.54,.68,.18))
      part(equipment,cube,'#415b69',v(-.69,1.8,-.44),v(.34,.17,.025))
      part(equipment,tube,'#70bda5',v(-.55,1.55,-.44),v(.051,.07,.051))
      part(equipment,cube,'#9cdbe2',v(.57,.61,.59),v(.08,.61,.04))
    }
    const offsets=lift?[.36]:[-.42,.42]
    const motorZ=lift ? .66 : .28
    for(const offset of offsets){
      part(equipment,tube,colors.pump,v(offset,.57,motorZ),v(.22,.83,.22))
      for(const y of [.37,.47,.57,.67]) part(equipment,tube,colors.dark,v(offset,y,motorZ),v(.245,.045,.245))
      pipe(equipment,v(offset,.87,motorZ),v(offset,1.61,motorZ),.105,colors.pump)
      pipe(equipment,v(offset,1.61,motorZ),v(0,1.61,0),.105,colors.pump)
      part(equipment,tube,colors.valve,v(offset,1.22,motorZ),v(.15,.13,.15))
      part(equipment,ring,'#a66038',v(offset,1.34,motorZ+.14),v(.85,.85,.85))
    }
    return {equipment,base}
  }
  return {createEquipment,part,pipe,tube,cone,ring}
}
