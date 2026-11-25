"""B2 E302 ceiling-mounted lighting trunking: as-built straight sections."""
import json
from hashlib import sha256
from math import atan2, cos, dist, sin
from struct import pack

from b2_tray_segments import FAMILY_SHA, TRAY_FAMILY, read_model


LIGHTING_ID = "6-07-2BAE58C"
LIGHTING_SHA = "82648b2d46837e1f125fe1bc79b0f139977081ba9e76bbd6aa116afb064287d9"
SHEET_SHA = "064c0e95aae92252455e34ef67837af6f30031c093b45c5957c6fd6e195acfc1"


def lighting_tray_assets(root, records_by_id, straights):
    drawing, _ = read_model(root, records_by_id[LIGHTING_ID], LIGHTING_SHA)
    source = next(s for s in drawing["nodes"][0]["extras"]["sources"] if s["code"] == "E302")
    if source["sha256"] != SHEET_SHA or source["sourceRef"] != "A101" or not source["registered"]:
        raise ValueError("E302 does not match the verified B2 lighting registration")

    family, binary = read_model(root, records_by_id[TRAY_FAMILY], FAMILY_SHA)
    if family["nodes"][1]["name"] != "桥架侧帮" or family["nodes"][3]["name"] != "穿孔底托横档":
        raise ValueError("Unexpected tray-family mesh")
    # Reuse the archived metal plate meshes and their materials. The as-built
    # note specifies a trunking, so use a closed shell, not the family's ladder.
    metal = family["nodes"][1]["mesh"]
    cover = family["nodes"][3]["mesh"]
    width = height = .05
    bottom = 3.2
    display_wall = .001
    entries = []
    for sequence, a, b in straights:
        line = [[round(v * source["scale"] + off, 5) for v, off in zip(pt, source["offset"])]
                for pt in (a, b)]
        length = dist(*line)
        midpoint = [round((u + v) / 2, 5) for u, v in zip(*line)]
        rotation = -atan2(line[1][1] - line[0][1], line[1][0] - line[0][0])
        origin = [midpoint[0], bottom, midpoint[1]]
        model_id = f"B2-E302-LIGHTING-TRUNK-{sequence}"
        name = f"照明金属线槽 · 50×50 · 直段 {sequence}"
        basis = ("E302 备注1注明金属线槽 50×50 mm、外涂防火漆、底距地 3.2 m。"
                 f"直段由 CABLETRAY 图元 {sequence} 与中心线 {sequence+1} 配对，按 A101 建筑坐标定位。"
                 "板厚、槽盖及涂层颜色为外形示意。")
        context = dict(floor="B2", catalogId=model_id, name=name, source="E302", sourcePdfSha256=SHEET_SHA,
                       geometryPath=sequence, centerlinePath=sequence+1, endpointsXZ=line, floorOrigin=origin,
                       sectionMm=[50, 50], bottomAboveFloorM=bottom, floorElevationM=-9.2,
                       bottomElevationM=-6.0, topElevationM=-5.95, units="m", up="Y", sourceBasis=basis,
                       prototypeId=TRAY_FAMILY, prototypeSha256=FAMILY_SHA)
        nodes = [dict(name=name, rotation=[0, sin(rotation/2), 0, cos(rotation/2)], children=[1, 2, 3, 4],
                      extras=context)]
        for title, y, z, dy, dz, mesh in (
                ("底板", display_wall/2, 0, display_wall, width, metal),
                ("盖板", height-display_wall/2, 0, display_wall, width, cover),
                ("侧板", height/2, -(width-display_wall)/2, height-2*display_wall, display_wall, metal),
                ("侧板", height/2, (width-display_wall)/2, height-2*display_wall, display_wall, metal)):
            nodes.append(dict(name=title, mesh=mesh, translation=[0, y, z], scale=[length, dy, dz]))
        scene = dict(family, nodes=nodes, scene=0, scenes=[{"nodes": [0]}])
        payload = json.dumps(scene, ensure_ascii=False, separators=(",", ":")).encode()
        payload += b" " * (-len(payload) % 4)
        blob = pack("<4sIIII", b"glTF", 2, 20+len(payload)+len(binary), len(payload), 0x4E4F534A)+payload+binary
        fields = [
            ["截面与表面做法", "50 × 50 mm 金属线槽，外涂防火漆"],
            ["本层安装标高", "槽底距车库地坪 3.200 m，槽顶 3.250 m"],
            ["相对建筑 ±0.000", "槽底 −6.000 m，槽顶 −5.950 m"],
            ["图面直段", f"约 {length:.3f} m；接头及弯、三通另核。"],
            ["定位依据", f"E302 备注 1，线槽轮廓 {sequence}、中心线 {sequence+1}"],
        ]
        entries.append((dict(id=model_id, name=name, kind="installed", origin=origin, planXZ=midpoint,
                             planLine=line, views=["室内照明与应急"], drawings="E302", sheetCodes=["E302"],
                             sourceCodes=["E302"], fields=fields, basis=basis,
                             file=model_id+".glb", sha256=sha256(blob).hexdigest()), blob))
    return entries
