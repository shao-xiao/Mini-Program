// Indices refer to the reviewed P301 extraction order. The source checksum is
// required: another drawing revision must supply its own association table.
const planDigest = '405719a0eef316977231e95264bccfb0663bf0a4c0c382df600d33ccccd042e4'
export function hasStationSurvey(model) {
  return model?.version === 2 && model.extractionDigest === 'dd55440d108bc5ef2bfe13ecf9613cfbf3bd2ee9419f369ca2c436efead48c69' && model.sources?.some(s => s.code === 'P301' && s.sha256 === planDigest)
}
// Signed segment numbers follow the discharge direction. Gaps in annotated
// strokes are left open. The final symbol is the actual riser on the plan.
const discharges = {
  'JSK1-1':[23,43,42,41,39,40], 'JSK1-2':[26,-182,-183,181,188,-187,-186],
  'JSK1-3':[28,52,53,54], 'JSK1-4':[31,-92,91,90],
  'JSK2-1':[24,50,49,47,48], 'JSK2-2':[22,364,363,362,361,358,359,360],
  'JSK2-3':[27,371,-372,375,376,377,378,379,59,58,57],
  'JSK2-4':[25,64,-63], 'JSK2-5':[29,387,-415,-414],
  'JSK2-6':[30,83,-82,81,-80], 'JSK3-1':[152,-97,-153]
}
const liftRooms = [
  {name:'污水处理间（西侧）', machine:'一体化隔油提升设备', model:'BF-DZ-20', performance:'20 m³/h · 18 m · 6 kW',
    outlet:'YWL-2', vent:'TL-2', discharge:[35,70,69], air:[355,-72,356], rinse:[-95,-96]},
  {name:'污水处理间（核心筒东侧）', machine:'一体化污水提升设备', performance:'10 m³/h · 16 m · 1.5 kW',
    outlet:'YWL-3', vent:'TL-1', discharge:[37,157,158,159], air:[71,-354,76], rinse:[-398,-397,-402,-403]}
]
export const stationMedia = {排水:'#187f8a',冲洗:'#247ba6',通气:'#a07944'}
function route(items, ids, medium) {
  return ids.map(id => {
    const item=items[Math.abs(id)]
    if (item?.kind !== '管段') throw new Error('给排水图元与流路索引不一致')
    return {id:Math.abs(id), medium, points:id<0 ? [...item.points].reverse() : item.points}
  })
}
export function buildStations(model, rooms=[]) {
  if (!hasStationSurvey(model)) return []
  const items=model.items
  const stations=liftRooms.map(record=>{
    const [riser,...output]=record.discharge, [airRiser,...air]=record.air
    const room=rooms.find(r=>r.name===record.name)
    return {...record, kind:'处理间', anchor:items[output[0]].points[0],
      footprint:room?.points, roomFields:room?.fields || [], roomSheet:room?.source,
      outlets:[{id:riser,point:items[riser].points[0],medium:'排水'},{id:airRiser,point:items[airRiser].points[0],medium:'通气'}],
      runs:[...route(items,output,'排水'),...route(items,record.rinse,'冲洗'),...route(items,air,'通气')]}
  })
  for (const item of items.filter(i=>i.kind==='集水坑')) {
    const [riser,...output]=discharges[item.name]
    stations.push({...item, kind:'集水坑', machine:'潜水排污泵',anchor:item.points[0],model:item.pump,
      performance:item.flow+' T/h · '+item.head+' m · '+item.power+' kW',
      outlet:item.connection.riser, outlets:[{id:riser,point:items[riser].points[0],medium:'排水'}],
      runs:route(items,output,'排水')})
  }
  return stations
}
