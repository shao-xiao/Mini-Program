"""Rebuild existing B2 sump instances as registered below-floor sectional spaces."""
from hashlib import sha256
import json
from struct import pack

from b2_tray_segments import read_model
from build_b2_disciplines import read_json


SOURCES = {
    "P301": "405719a0eef316977231e95264bccfb0663bf0a4c0c382df600d33ccccd042e4",
    "P202": "8b180878ea71cf551f8b690fea775d3006b96aa5b7c5654fba576b7b9d0417f3",
    "A101": "001bc5df93a31166fa62a5ee8d6223b1ee2ad228b4fe15d6c77bc85e7fc6008e",
}


def sump_geometry(module, lo, hi, depth, levels):
    nodes = [dict(name=module["room"], extras={"module": module}, children=[])]
    meshes, accessors, buffers, materials = [], [], [], []
    binary = bytearray()
    origin = module["floorOrigin"]
    left, near = lo[0] - origin[0], lo[1] - origin[2]
    right, far = hi[0] - origin[0], hi[1] - origin[2]

    def ring(y):
        return [(left, y, near), (left, y, far), (right, y, far), (right, y, near)]

    def add(name, points, mode, rgba):
        offset = len(binary)
        for point in points:
            binary.extend(pack("<3f", *point))
        idx = len(meshes)
        buffers.append(dict(buffer=0, byteOffset=offset, byteLength=len(binary)-offset, target=34962))
        accessors.append(dict(bufferView=idx, componentType=5126, count=len(points), type="VEC3",
                              min=[min(v) for v in zip(*points)], max=[max(v) for v in zip(*points)]))
        materials.append(dict(name=name, doubleSided=True, alphaMode="BLEND" if rgba[3] < 1 else "OPAQUE",
                              pbrMetallicRoughness=dict(baseColorFactor=rgba, metallicFactor=0, roughnessFactor=1),
                              extensions={"KHR_materials_unlit": {}}))
        meshes.append(dict(name=name, primitives=[dict(attributes={"POSITION": idx}, mode=mode, material=idx)]))
        nodes[0]["children"].append(len(nodes))
        nodes.append(dict(name=name, mesh=idx))

    top, bottom = ring(0), ring(-depth)
    # Transparent open-topped section for the plot envelope; zero wall thick-
    # ness. Reference lines show control settings, not a live water level.
    faces = [bottom[i] for i in (0, 1, 2, 0, 2, 3)]
    for i in range(4):
        j = (i + 1) % 4
        faces.extend((top[i], top[j], bottom[j], top[i], bottom[j], bottom[i]))
    add("图面外轮廓至图注坑底 · 空间剖切", faces, 4, [.16, .39, .46, .27])
    edges = []
    for i in range(4):
        j = (i + 1) % 4
        edges.extend((top[i], top[j], bottom[i], bottom[j], top[i], bottom[i]))
    add("坑口及坑底范围", edges, 1, [.07, .19, .24, 1])
    for name, level, rgba in zip(("启泵水位", "停泵水位", "报警水位"), levels,
                                 ([.03, .38, .50, 1], [.12, .30, .51, 1], [.94, .42, .07, 1])):
        line = ring(level)
        add(name, line + [line[0]], 3, rgba)
    doc = dict(asset=dict(version="2.0"), scene=0, scenes=[dict(nodes=[0])],
               extensionsUsed=["KHR_materials_unlit"], nodes=nodes, meshes=meshes,
               materials=materials, accessors=accessors, bufferViews=buffers,
               buffers=[dict(byteLength=len(binary))])
    payload = json.dumps(doc, ensure_ascii=False, separators=(",", ":")).encode()
    payload += b" " * (-len(payload) % 4)
    return (pack("<4sIIII", b"glTF", 2, 28 + len(payload) + len(binary), len(payload), 0x4E4F534A)
            + payload + pack("<I4s", len(binary), b"BIN\0") + binary)


def pit_assets(root, records_by_id, outlines):
    floor = next(f for f in read_json(root / "楼层与跨层装配索引.json")["floors"] if f["level"] == "B2")
    if floor["sourceSha256"] != SOURCES["A101"] or floor["levelOrigin"] != [0, -9.2, 0]:
        raise ValueError("B2 architectural datum differs from the checked A101")
    sheets = read_json(root / "全目录模型清单.json")["drawingIndex"]
    for code, digest in SOURCES.items():
        discipline = "02建筑" if code == "A101" else "05给排水"
        indexed = [s for s in sheets if s["code"] == code and s["directory"] == discipline]
        if len(indexed) != 1 or indexed[0]["sha256"] != digest:
            raise ValueError(f"{code} drawing revision changed; review pit heights and outlines")
    pits = [room for room in floor["rooms"] if room["name"].startswith("JSK")]
    if len(pits) != len(outlines) or {room["name"] for room in pits} != outlines.keys():
        raise ValueError("B2 sump inventory changed; review P301 and P202")
    for room in pits:
        key, name = room["id"], room["name"]
        catalog_record = records_by_id[key]
        old, _ = read_model(root, catalog_record, room["sha256"])
        module = old["nodes"][0]["extras"]["module"]
        seq, lo, hi = outlines[name]
        origin = room["registration"]["floorOrigin"]
        if (module["room"] != name or module["floorOrigin"] != origin or origin[1] != 0 or
                max(abs((a + b)/2 - coord) for a, b, coord in zip(lo, hi, origin[::2])) > .002):
            raise ValueError(f"{name} original instance and P301 outline disagree")
        # P202: H designates the B2 floor. The H-1.80 line at the lift sump
        # refers to the adjoining lift pit, not the JSK3-1 bottom H-3.30.
        depth = 3.30 if name == "JSK3-1" else 1.50
        levels = [-.4, -1.25, -.3] if name.startswith("JSK1") else [-.4, -1.3, -.3]
        levels = {"JSK2-5": [-.45, -1.3, -.35], "JSK3-1": [-2.1, -3.05, -2.0]}.get(name, levels)
        if levels != module["levels"]:
            raise ValueError(f"{name} r1 settings differ from P202")
        basis = ("坑口按 P301 的平面外轮廓定位，剖切范围由本层地面 H 向下至 P202 图注坑底。"
                 "彩线表示启泵、停泵及报警控制水位。净空尺寸、壁厚、泵体安装标高待核。")
        module = dict(module, version=2, id=key, verticalExtentVerified=True,
                      pitBottomRelativeM=-depth, floorElevationM=-9.2,
                      sectionBoundsXZ=[lo, hi], geometrySequence=seq, sourceSha256=SOURCES,
                      equipmentGeometry="本件呈现坑体空间剖切。设备参数保留，泵体可在设备原型中查看。",
                      previousSha256=room["sha256"], basis=basis)
        blob = sump_geometry(module, lo, hi, depth, levels)
        fields = [["坑底距本层地面 H", f"H − {depth:.3f} m"],
                  ["坑底建筑标高", f"{-9.2 - depth:.3f} m（本层 H 为 −9.200 m）"],
                  ["启泵 / 停泵 / 报警（相对 H）", " / ".join(f"{v:.3f}" for v in levels) + " m"],
                  ["外轮廓图量 · X × Z", f"{hi[0]-lo[0]:.3f} × {hi[1]-lo[1]:.3f} m"],
                  ["潜水泵 / 运行方式", f"{module['equipment']['model']} · {module['equipment']['duty']}"]]
        yield dict(id=key, name=name, kind="room", origin=origin, views=["承重结构", "给排水"],
                   sourceCodes=["P301", "P202"], drawings="P301、P202", basis=basis,
                   verticalExtentVerified=True, fields=fields, file=key+".glb",
                   sha256=sha256(blob).hexdigest(), previousSha256=room["sha256"]), blob
