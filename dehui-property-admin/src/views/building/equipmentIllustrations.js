// Match the object before its accessories, location, or power source.
const shapes = [
  ['worktable', /工作台|操作台|打荷台|理鱼台/],
  ['hood', /烟罩|集气罩/],
  ['fan', /排气扇|排风|新风机|新风风机|送风|排烟|补风|风幕机/],
  ['pump', /泵|稳压成套|污水提升/],
  ['silencer', /消[声音][箱器]/],
  ['control', /控制[柜箱]|配电[柜箱]|并网柜/],
  ['purifier', /净化器|净水器/],
  ['rice-pail', /饭桶/], ['soup-pail', /汤桶/],
  ['glass-guard', /玻璃罩|回收窗口/],
  ['serving-counter', /发餐柜|保温汤池/],
  ['sink', /星盆|洗手池|残菜台|污碟台/],
  ['faucet', /龙头/],
  ['fridge', /冰箱|冷藏柜|冷冻柜/], ['ice-maker', /制冰机/],
  ['oven', /蒸饭箱|蒸柜|蒸烤箱|烤箱/],
  ['range', /炒|灶|煮面炉|四头炉|矮仔炉|煮汤炉/],
  ['heater', /开水器|热水器/],
  ['dishwasher', /洗碗机|洗米机/], ['disinfect', /消毒柜/],
  ['warming-lamp', /保温灯|消毒灯|灭蝇灯/],
  ['shelf', /货架|层架|仓架|茜架|层板/], ['cart', /板车|推车|回收车/],
  ['work-cabinet', /工作柜|挂墙柜|小件柜|碗碟柜/],
  ['plate', /补角板/], ['bin', /垃圾桶/], ['separator', /油水分离/],
  ['hydrant', /消火栓/], ['fire-unit', /灭火系统|气体灭火装置/],
  ['bollard', /升降[墩柱]/], ['elevator', /电梯|客梯|货梯/],
  ['revolving-door', /旋转门/], ['ev-charger', /充电桩/],
  ['solar', /光伏组件/], ['inverter', /逆变器/], ['transformer', /变压器/],
  ['light', /泛光照明/], ['ac', /空调/], ['display', /显示屏|拼接屏/],
  ['camera', /摄像机|摄像头|枪机|球机/], ['network-rack', /机柜/],
  ['network-switch', /交换机/], ['worktable', /洁碟台/]
]

export function equipmentIllustrationKind(name) {
  const text = String(name ?? '').normalize('NFKC').replace(/\s/g, '')
  // These components can contain a fan name, e.g. 新风消音箱、风机控制箱.
  if (/消[声音][箱器]/.test(text)) return 'silencer'
  if (/^(?:风机|新风)控制箱/.test(text)) return 'control'
  return shapes.find(([, pattern]) => pattern.test(text))?.[0] ?? 'record'
}
