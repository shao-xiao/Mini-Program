import {describe,it,expect} from 'vitest'
import {defineRoomModule} from '../../src/components/spaces/roomModule'
import {localRoomStrokes} from '../../src/components/spaces/roomLinework'

describe('registered room modules',()=>{
  it('places local vertices and equipment at their surveyed positions without scaling',()=>{
    const room={name:'设备间',points:[[10,20],[13,20],[13,22],[10,22]],fields:[['建筑标高（图注）','−9.250 m']]}
    const source={anchor:[11.2,21.4]}
    const part=defineRoomModule(room,[room],source,-9.2)
    expect(part.origin[1]).toBeCloseTo(-.05)
    expect(part.points.map(([x,z])=>[x+part.origin[0],z+part.origin[2]])).toEqual(room.points)
    expect(part.equipmentAnchor[0]+part.origin[0]).toBe(source.anchor[0])
    expect(part.equipmentAnchor[1]+part.origin[2]).toBe(source.anchor[1])
  })
  it('retains both internal fragments of a line crossing a concave room',()=>{
    const room=[[0,0],[3,0],[3,3],[2,3],[2,1],[1,1],[1,3],[0,3]]
    const lines=localRoomStrokes([[[-1,2],[4,2]]],room,[0,0,0])
    expect(lines).toEqual([0,.035,2,1,.035,2,2,.035,2,3,.035,2])
  })
})
