import * as T from 'three'
// A single linear-radiance sky, horizon and ground in world coordinates.
// Pre-filter once at startup so orbiting samples the same environment on each frame.
export function daylight(renderer){
 const w=512,h=256,data=new Float32Array(w*h*4)
 for(let j=0;j<h;j++)for(let i=0;i<w;i++){
  const elev=((j+.5)/h-.5)*Math.PI,az=((i+.5)/w-.5)*2*Math.PI
  const y=Math.sin(elev),haze=Math.exp(-Math.abs(y)*4.1)
  const hill=.025+.025*Math.sin(3*az)+.018*Math.sin(11*az+.8)
  const phi=(az+Math.PI*2)%(Math.PI*2),lot=Math.floor(phi/.145),u=phi/.145-lot
  const noise=n=>.5+.5*Math.sin(n*173.71+5.4)
  const building=u>.11&&u<.91&&noise(lot+4)>.24
  const height=building?.055+.26*noise(lot):0
  const treeLine=hill+.019*Math.sin(phi*97)*Math.cos(phi*41)
  let rgb
  if(y<treeLine){
   const tone=.09+.075*noise(lot+1)+.023*Math.sin(90*phi)
   rgb=y<-.055?[.26,.27,.285]:[tone,tone*1.15,tone*1.05]
  }else if(y<height){
   const warm=noise(lot+2),pane=(u*10)%1>.12&&(y*69)%1>.20
   const shade=pane?.29+.12*warm:.60
   rgb=[shade*(1+.20*warm),shade,shade*(1.14-.32*warm)]
  }else{
   const field=Math.sin(az*5+7*y)+.62*Math.sin(az*10-15*y)+.28*Math.cos(az*27+36*y)+.14*Math.sin(az*51-63*y)
   const clouds=Math.max(0,Math.min(1,(field+.32)*1.4))*Math.exp(-((y-.28)**2)/.125)
   const sunGlow=2.8*Math.exp(-((Math.cos(az-.85)-1)**2)/.019-((y-.46)**2)/.072)
   rgb=[.17+.91*haze+2.3*clouds+sunGlow,.42+.92*haze+2.2*clouds+sunGlow,1.04+.69*haze+1.88*clouds+.85*sunGlow]
  }
  data.set([...rgb,1],(j*w+i)*4)
 }
 const sky=new T.DataTexture(data,w,h,T.RGBAFormat,T.FloatType)
 sky.mapping=T.EquirectangularReflectionMapping;sky.colorSpace=T.LinearSRGBColorSpace;sky.needsUpdate=true
 const gen=new T.PMREMGenerator(renderer),target=gen.fromEquirectangular(sky);gen.dispose();sky.dispose()
 return target
}
export function instanceTrees(model){
 model.updateWorldMatrix(true,true)
 const zones=[];model.traverse(n=>{if(n.children.some(c=>c.name.startsWith('乔木树位')))zones.push(n)})
 for(const zone of zones){
  const sets=new Map(),inv=zone.matrixWorld.clone().invert()
  for(const t of zone.children.filter(c=>c.name.startsWith('乔木树位')))t.traverse(o=>{
   if(!o.isMesh||o.isSkinnedMesh||!o.visible||Array.isArray(o.material))return
   const key=o.geometry.uuid+'/'+o.material.uuid
   if(!sets.has(key))sets.set(key,[]);sets.get(key).push(o)
  })
  for(const items of sets.values()){
   if(items.length<3)continue
   const first=items[0],batch=new T.InstancedMesh(first.geometry,first.material,items.length)
   batch.name='树冠合批_'+first.name
   items.forEach((o,i)=>{batch.setMatrixAt(i,new T.Matrix4().multiplyMatrices(inv,o.matrixWorld));o.visible=false})
   batch.instanceMatrix.needsUpdate=true;batch.computeBoundingSphere();zone.add(batch)
  }
 }
}
