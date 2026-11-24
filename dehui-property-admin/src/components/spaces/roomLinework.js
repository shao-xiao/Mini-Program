// Clip drawing strokes at the room boundary, including concave rooms. A stair
// flight or a door swing crossing a boundary must not bring in its neighbour.
export function localRoomStrokes(paths,polygon,[ox,,oz]) {
  const xs=polygon.map(p=>p[0]), zs=polygon.map(p=>p[1])
  const minX=Math.min(...xs), maxX=Math.max(...xs), minZ=Math.min(...zs), maxZ=Math.max(...zs)
  const result=[]
  for (const path of paths) for (let k=1;k<path.length;k++) {
    const [x,z]=path[k-1], [u,v]=path[k]
    if (Math.max(x,u)<minX || Math.min(x,u)>maxX || Math.max(z,v)<minZ || Math.min(z,v)>maxZ) continue
    const dx=u-x,dz=v-z, cuts=[0,1]
    for(let i=0;i<polygon.length;i++) {
      const [a,b]=polygon[i], [c,d]=polygon[(i+1)%polygon.length]
      const ex=c-a,ez=d-b, det=dx*ez-dz*ex
      if(Math.abs(det)<1e-9) continue
      const t=((a-x)*ez-(b-z)*ex)/det, side=((a-x)*dz-(b-z)*dx)/det
      if(t>0 && t<1 && side>=0 && side<=1) cuts.push(t)
    }
    cuts.sort((a,b)=>a-b)
    for(let i=1;i<cuts.length;i++) {
      const start=cuts[i-1],end=cuts[i]
      if(end-start<1e-8) continue
      const mid=(start+end)/2, px=x+mid*dx, pz=z+mid*dz
      let inside=false
      for(let j=0;j<polygon.length;j++) {
        const [a,b]=polygon[j], [c,d]=polygon[(j+1)%polygon.length]
        if((b>pz)!==(d>pz) && px<(c-a)*(pz-b)/(d-b)+a) inside=!inside
      }
      if(inside) result.push(x+dx*start-ox,.035,z+dz*start-oz,x+dx*end-ox,.035,z+dz*end-oz)
    }
  }
  return result
}
