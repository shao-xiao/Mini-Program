import { CurvePath, LineCurve, QuadraticBezierCurve, Vector2 } from 'three'

const point = (x,z) => new Vector2(x,z)
const laneSpeed = 3.4
const parkingSpeed = 1.25

// Use the transcribed arrow positions as lane centres, including the return side.
// The loop fits entirely inside the four communicating level aisles.
export function parkingRoutes(garage) {
  const lane = name => garage.lanes.find(l=>l.name===name)
  const arrow = (name,degrees) => lane(name)?.arrows.find(a=>a[2]===degrees)
  const north=arrow('北侧横向车道',0), east=arrow('东侧纵向车道',90)
  const south=arrow('南侧横向车道',180), west=arrow('西侧纵向车道',-90)
  if (!north || !east || !south || !west) return []
  const corners=[point(west[0],north[1]),point(east[0],north[1]),point(east[0],south[1]),point(west[0],south[1])]
  const routes=[]
  for (const [number,edge] of [['103',0],['117',0],['168',2]]) {
    const bay=garage.parking.find(b=>b.name===number)
    if (!bay) continue
    const spot=bay.points.reduce((v,[x,z])=>v.add(point(x,z)),point(0,0)).divideScalar(bay.points.length)
    const direction=edge===0 ? 1 : -1
    const junction=point(spot.x-direction*3.2,corners[edge].y)
    const lap=new CurvePath()
    let from=junction
    for (let step=1;step<=4;step++) {
      const i=(edge+step)%4, corner=corners[i]
      const entry=corner.clone().add(corners[(i+3)%4].clone().sub(corner).normalize().multiplyScalar(3.2))
      const exit=corner.clone().add(corners[(i+1)%4].clone().sub(corner).normalize().multiplyScalar(3.2))
      lap.add(new LineCurve(from,entry))
      lap.add(new QuadraticBezierCurve(entry,corner,exit))
      from=exit
    }
    lap.add(new LineCurve(from,junction))
    const turn=new QuadraticBezierCurve(junction,point(spot.x,junction.y),spot)
    routes.push({bay,lap,turn,driveTime:lap.getLength()/laneSpeed,turnTime:turn.getLength()/parkingSpeed})
  }
  return routes
}

export function vehiclePose(route,time) {
  const {lap,turn,driveTime,turnTime}=route
  const dwell=4, phase=time%(driveTime+2*turnTime+dwell)
  let path=lap, progress=phase/driveTime
  if (phase>=driveTime) {
    path=turn
    const t=phase-driveTime
    if (t<turnTime) progress=t/turnTime
    else if (t<turnTime+dwell) progress=1
    else progress=1-(t-turnTime-dwell)/turnTime
  }
  progress=Math.max(0,Math.min(1,progress))
  const pos=path.getPointAt(progress), tangent=path.getTangentAt(progress)
  return {x:pos.x,z:pos.y,heading:Math.atan2(tangent.x,tangent.y)}
}
