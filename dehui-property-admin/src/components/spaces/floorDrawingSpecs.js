// The survey supplies printed room dimensions. Polygon measurements describe the drawn shape only.
function measurements(points) {
  const sides = []
  let twiceArea = 0
  for (let i = 0; i < points.length; i++) {
    const [x,y] = points[i], [nextX,nextY] = points[(i+1) % points.length]
    sides.push(Math.hypot(nextX-x,nextY-y))
    twiceArea += x*nextY - nextX*y
  }
  return { sides, area:Math.abs(twiceArea)/2 }
}

export function objectSpecs(group,index,{core,garage,room,points,label}) {
  if (group === 'coreOverview') return [
    ['详图范围','地下二层核心筒 · 上部平面'],['详图比例','1:50'],
    ['轴线间尺寸','4–9 轴 34.50 m · F–H 轴 10.50 m'],
    ['已配准柱截面',core.columns.length+' 个'],['可选墙段',core.walls.length+' 段'],
    ['柱角最大配准差',core.maxErrorMm+' mm'],['标注依据','A401 核心筒放大图（一）与 A101 平面图']
  ]
  if (!points?.length) return []
  const {sides,area} = measurements(points)
  if (group === 'rooms') return [
    ['所选空间',room.name],...(room.fields || []),
    ['模型表达',room.name.includes('电梯井') ? '图上井道轮廓与开顶轿厢示意，运行状态未接入' : '图上空间轮廓，按 1.20 m 展示剖切'],
    ...(!room.fields?.length ? [['核对状态','图上名称已核对，详细注记待关联']] : []),
    ['标注依据',room.source === 'A401' ? 'A401 地下二层核心筒放大平面' : 'A101 地下二层平面图']
  ]
  if (group === 'crossings') return [
    ['所选标线','人行横道 '+(index+1)],['图示形式','横向白色条纹'],['编号口径','模型定位序号'],['标注依据','交通竣工图 · 原第 5 页']
  ]
  if (group === 'lanes' || group === 'parking') {
    const item = garage[group][index]
    const size = Math.max(...sides).toFixed(1)+' × '+Math.min(...sides).toFixed(1)+' m'
    if (group === 'parking') return [
      ['车位编号',item.name],['所属分区',item.zone+' 区'],['车位类型','一般小型车位'],
      ['车位线轮廓（扫描图估算）','约 '+size],['标注依据','交通竣工图第 5 页编号、第 6 页分区']
    ]
    const arrows = [...new Set(item.arrows.map(a=>a[2]))]
    const symbol = {0:'→',90:'↓',180:'←','-90':'↑'}
    return [['所选车道',item.name],...(item.name.includes('坡道') ? [['坡道详图',item.name.startsWith('北侧') ? 'A409 · 6–6 剖面' : 'A410 · 7–7 剖面'],['起坡段','3.60 m，7.5%'],['主坡段','15%'],['模型范围','交通图配准范围内的 B2 可见段']] : []),['图面箭头',arrows.map(a=>symbol[a] || a+'°').join('  ')],
      ['标线',item.centerline ? '图示黄色中心线' : '模型未记录中心线'],
      ['所选显示段长 × 宽（扫描图估算）','约 '+size],['标注依据','地下二层停车场交通设施图 · 原第 5 页']]
  }
  const sheet = group.startsWith('core') ? 'A401' : 'A101'
  const rows = [['所选图元',label+' '+(index+1)],['轮廓各边（矢量量取）',sides.map(n=>n.toFixed(2)).join(' / ')+' m'],
    ['平面投影面积（矢量量取）',area.toFixed(2)+' ㎡']]
  const match = group === 'columns' ? core?.columnIndices.indexOf(index) : -1
  if (match >= 0) rows.push(['详图对应','A401 核心筒柱 '+(match+1)])
  if (group === 'coreColumns') rows.push(['总图对应','A101 柱 '+(core.columnIndices[index]+1)])
  if (group === 'bays') rows.push(['编号口径','A101 图元序号，交通车位编号见「车位与车道」'])
  rows.push(['所在楼层','地下二层'],['标注依据',sheet+' 建筑竣工图'])
  return rows
}
