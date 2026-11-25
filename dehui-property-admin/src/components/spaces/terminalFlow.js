// The B2 M306/M207 terminal GLBs record the discharge face in their local
// coordinate system. A marker inherits the surveyed terminal's yaw and sill.
import { ConeGeometry, Group, Mesh, MeshBasicMaterial } from 'three'

export function terminalFlow(instance) {
  const face=instance.children.find(node=>node.userData.module?.facingLocal==='+Z')
  const data=face?.userData.module
  if (!(data?.widthM>0 && data.heightM>0)) return null
  const group=new Group()
  const geometry=new ConeGeometry(.12,.32,8)
  geometry.rotateX(Math.PI/2)
  const material=new MeshBasicMaterial({color:0x088882})
  for (const x of [-.28,0,.28]) {
    const arrow=new Mesh(geometry,material)
    arrow.position.set(x*data.widthM,data.heightM*.55,0)
    group.add(arrow)
  }
  face.add(group)
  return {
    update(time,visible){
      group.visible=visible
      group.position.z=.18+(time*.68%1)*1.25
    },
    dispose(){group.removeFromParent();geometry.dispose();material.dispose()}
  }
}
