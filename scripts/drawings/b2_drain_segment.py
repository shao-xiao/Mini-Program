"""Create the evidenced P301 floor-drain branch as a detached B2 review detail."""
import json
from hashlib import sha256
from math import atan2, cos, dist, hypot, sin
from struct import pack

from b2_tray_segments import read_model


FLOOR_WATER = "6-05-C8E938D"
WASTE_BRANCH = "2-05-5A8AB5B"
WATER_SHA = "08c796874a47984f92176d9e2f81ee3c1608c7cbbddc4d53e29d7511afc698f9"
FAMILY_SHA = "5343ff6eb8e20dcd896c956518bd3f64c3a20cd86b0a3535fa9c8b15f5238831"
DRAWING_SHA = {
    "P301": "405719a0eef316977231e95264bccfb0663bf0a4c0c382df600d33ccccd042e4",
    "P202": "8b180878ea71cf551f8b690fea775d3006b96aa5b7c5654fba576b7b9d0417f3",
    "P003": "6965b59763b5686cf89034991529e9e82c63ec31e0de50cffdcdeac37121bcf1",
}


def drain_assets(catalog_root, records_by_id, endpoints):
    water, _ = read_model(catalog_root, records_by_id[FLOOR_WATER], WATER_SHA)
    source = water["nodes"][0]["extras"]["sources"][0]
    if not source["registered"] or source["sourceRef"] != "A101" or source["sha256"] != DRAWING_SHA["P301"]:
        raise ValueError("P301 registration no longer matches the reviewed drawing")
    catalog = json.loads((catalog_root / "全目录模型清单.json").read_text())
    for code, expected in DRAWING_SHA.items():
        matches = [r for r in catalog["drawingIndex"] if r["directory"] == "05给排水" and r["code"] == code]
        if len(matches) != 1 or matches[0]["sha256"] != expected:
            raise ValueError(f"Drawing revision changed: {code}")
    plan = [[round(v * source["scale"] + offset, 6) for v, offset in zip(pt, source["offset"])]
            for pt in endpoints]
    length = dist(*plan)
    slope = .01
    fall = length * slope
    model_id = "B2-P301-JSK2-5-DRAIN-28911"
    name = "JSK2-5 地漏入坑支管 · DN100"
    fields = [
        ["服务对象", "B2 地面排水地漏 → JSK2-5 集水坑"],
        ["材料与公称直径", "预埋衬塑钢管，DN100"],
        ["图面可辨直段", f"约 {length:.3f} m，从地漏符号边缘至坑侧。"],
        ["坡度与方向", f"1.0% 坡向集水坑，本段折算落差约 {fall * 1000:.1f} mm。"],
        ["管内底标注", "P202 注 H−0.25，即相对建筑 ±0.000 为 −9.450 m。"],
        ["安装高程核对", "P202 未标该高程在 P301 直段上的里程，两端绝对标高待核。"],
        ["横截面", "管内外径、衬层和钢壁厚待核。剖口沿用目录废水横支管原型。"],
    ]
    basis = ("P301 图元 28911 对应废水直段，28913 为预埋 DN100 衬塑钢管、0.01 坡向集水坑的引线，"
             "29042 指向 DN100 地漏。P202 的 H−0.25 标在该地漏支管上；P003 十二节第4、6条定义管内底和完成面。"
             "本段按比例表达图面长度与坡向，安装标高仅核到支管控制值，两端尚未定高。")
    extras = {
        "units": "m", "up": "Y", "drawingSha256": DRAWING_SHA, "planLine": plan,
        "geometrySequence": 28911, "leaderSequence": 28913, "drainLabelSequence": 29042,
        "floor": "B2", "floorOrigin": None, "floorElevationM": -9.2, "sourceInvertM": -9.45,
        "sourceInvertStationM": None, "installedInvertEndsM": [None, None], "nominalDiameter": "DN100",
        "slope": slope, "planLengthM": length, "fallM": fall, "sourceBasis": basis,
        "prototype": WASTE_BRANCH, "prototypeSha256": FAMILY_SHA,
    }
    family, binaries = read_model(catalog_root, records_by_id[WASTE_BRANCH], FAMILY_SHA)
    # The archived thin-walled main body has a local Y axis. Its end faces and
    # inner wall share that axis. Leave the undocumented couplings out of this run.
    body, start, end, lining = family["nodes"][2:6]
    if body["name"] != "主管薄壁" or lining["name"] != "内壁" or body["children"] != [3, 4, 5]:
        raise ValueError("Unexpected topology of the archived waste branch")
    body_pos = family["meshes"][body["mesh"]]["primitives"][0]["attributes"]["POSITION"]
    lining_pos = family["meshes"][lining["mesh"]]["primitives"][0]["attributes"]["POSITION"]
    bounds = family["accessors"][body_pos]
    family_length = bounds["max"][1] - bounds["min"][1]
    inner = family["accessors"][lining_pos]
    # DN100 is the nominal size. 100 mm is only used as an illustrative bore.
    # No as-built outer diameter or wall thickness is assigned in the record.
    radial = .1 / (inner["max"][0] - inner["min"][0])
    angle = atan2(plan[1][1] - plan[0][1], -fall)
    scene_body = dict(body, name=name, children=[1, 2, 3], extras=extras,
                      rotation=[sin(angle / 2), 0, 0, cos(angle / 2)],
                      scale=[radial, hypot(length, fall) / family_length, radial])
    del scene_body["matrix"]
    scene = dict(family, scene=0, scenes=[{"nodes": [0]}], nodes=[scene_body, start, end, lining])
    payload = json.dumps(scene, separators=(",", ":"), ensure_ascii=False).encode()
    payload += b" " * (-len(payload) % 4)
    blob = pack("<4sIIII", b"glTF", 2, 20 + len(payload) + len(binaries), len(payload), 0x4E4F534A)
    blob += payload + binaries
    marker = [round((a + b) / 2, 5) for a,b in zip(*plan)]
    return [(dict(id=model_id, name=name, kind="segment", origin=None, planXZ=marker, planLine=plan,
                  views=["给排水"], drawings="P301、P202、P003", sourceCodes=list(DRAWING_SHA),
                  basis=basis, fields=fields, file=model_id + ".glb", sha256=sha256(blob).hexdigest()), blob)]
