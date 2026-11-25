import { LineSegments2 } from 'three/addons/lines/LineSegments2.js'
import { LineSegmentsGeometry } from 'three/addons/lines/LineSegmentsGeometry.js'
import { LineMaterial } from 'three/addons/lines/LineMaterial.js'
import { BufferGeometry, Float32BufferAttribute, LineBasicMaterial, LineSegments } from 'three'

// The r1 exporter encodes each CAD stroke as two sloping faces sharing a ridge.
// Only these meshes have a recoverable source stroke. Keep solids and point
// symbols exactly as delivered.
const ridgeFaces = [0, 1, 3, 1, 4, 3, 1, 2, 4, 2, 5, 4]

export function createDisciplineInk(mesh, retain) {
  const segments = mesh.userData.segments
  const positions = mesh.geometry.attributes.position
  const index = mesh.geometry.index
  if (!mesh.userData.sourceLayer || !Number.isSafeInteger(segments) || segments <= 0 ||
      positions?.count !== segments * 6 || index?.count !== segments * ridgeFaces.length ||
      !mesh.material.color) return null

  const ridge = new Float32Array(segments * 6)
  for (let i = 0; i < segments; i++) {
    const base = i * 6
    for (let j = 0; j < ridgeFaces.length; j++) {
      if (index.getX(i * ridgeFaces.length + j) !== base + ridgeFaces[j]) return null
    }
    let offset = base
    for (const vertex of [base + 1, base + 4]) {
      ridge[offset++] = positions.getX(vertex)
      ridge[offset++] = positions.getY(vertex)
      ridge[offset++] = positions.getZ(vertex)
    }
  }
  const geometry = retain(new BufferGeometry())
  geometry.setAttribute('position', new Float32BufferAttribute(ridge, 3))
  const material = retain(new LineBasicMaterial({ color: mesh.material.color, depthWrite: false }))
  const ink = new LineSegments(geometry, material)
  ink.renderOrder = 1
  ink.userData = { group: 'discipline', index: mesh.userData.index }
  return ink
}

// A screen-space stroke stays legible from the whole floor down to a single room.
// Keep the source line for picking. Both strokes share the selection color.
export function addDiagramStroke(ink, retain, color) {
  const geometry=retain(new LineSegmentsGeometry())
  geometry.setPositions(ink.geometry.attributes.position.array)
  ink.material.color.setHex(color)
  const material=retain(new LineMaterial({linewidth:1.55,transparent:true,depthTest:false,depthWrite:false}))
  material.uniforms.diffuse.value=ink.material.color
  const stroke=new LineSegments2(geometry,material)
  stroke.renderOrder=4
  ink.visible=false
  return stroke
}
