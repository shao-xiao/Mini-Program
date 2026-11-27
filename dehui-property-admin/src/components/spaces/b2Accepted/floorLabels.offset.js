import * as T from 'three'

// Choose an interior anchor even for a concave footprint.
function anchor(points) {
  const polygon = points.map(([x,z])=>new T.Vector2(x,z))
  const centre = polygon.reduce((sum,v)=>sum.add(v),new T.Vector2()).divideScalar(polygon.length)
  const triangles = T.ShapeUtils.triangulateShape(polygon,[])
  let largest=0, fallback=centre
  for (const [i,j,k] of triangles) {
    const [a,b,c]=[polygon[i],polygon[j],polygon[k]]
    const cross=(p,q,r)=>(q.x-p.x)*(r.y-p.y)-(q.y-p.y)*(r.x-p.x)
    const signs=[cross(a,b,centre),cross(b,c,centre),cross(c,a,centre)]
    if (signs.every(v=>v>=0) || signs.every(v=>v<=0)) return centre
    const area=Math.abs(cross(a,b,c))
    if (area>largest) {largest=area; fallback=a.clone().add(b).add(c).divideScalar(3)}
  }
  return fallback
}

export function createFloorLabels(layer) {
  const labels=[], projected=new T.Vector3()
  function add(group,index,text,points,height,offsetY=0,activate) {
    const point=anchor(points)
    const node=document.createElement('span')
    node.className='plan-label '+group
    node.textContent=text
    if(activate){
      node.setAttribute('role','button');node.tabIndex=0
      node.onclick=activate
      node.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();activate()}}
    }
    layer.append(node)
    // A single layout pass when the scene opens avoids measuring hidden labels on orbit.
    labels.push({group,index,node,offsetY,point:new T.Vector3(point.x,height(point.x,point.y),point.y),
      rank: group==='rooms' ? (text==='电梯厅' ? 0 : 1) : group==='parking' ? 2 : 3})
  }
  function prepare() {
    labels.sort((a,b)=>a.rank-b.rank)
    for (const entry of labels) {entry.w=entry.node.offsetWidth;entry.h=entry.node.offsetHeight}
  }
  function update(camera,w,h,selection) {
    const used=[], chosen=labels.find(l=>l.group===selection?.group && l.index===selection?.index)
    for (const l of chosen ? [chosen,...labels.filter(e=>e!==chosen)] : labels) {
      projected.copy(l.point).project(camera)
      const x=(projected.x+1)*w/2,y=(1-projected.y)*h/2
      const active=l===chosen
      const left=Math.round(x-l.w/2),top=Math.round(y-l.h/2+l.offsetY),right=left+l.w,bottom=top+l.h
      const clipped=right<=0 || left>=w || bottom<=0 || top>=h || Math.abs(projected.z)>1
      const overlaps=used.some(r=>left<r.right+2 && right>r.left-2 && top<r.bottom+1 && bottom>r.top-1)
      const visible=!clipped && !overlaps
      l.node.hidden=!visible
      l.node.classList.toggle('active',active)
      if (visible) { l.node.style.transform='translate('+left+'px,'+top+'px)';used.push({left,top,right,bottom}) }
    }
  }
  function hit(clientX,clientY) {
    for (const {group,index,node} of labels) {
      if (node.hidden) continue
      const rect=node.getBoundingClientRect()
      if (clientX>=rect.left && clientX<=rect.right && clientY>=rect.top && clientY<=rect.bottom) return {group,index}
    }
    return null
  }
  return {add,prepare,update,hit}
}
