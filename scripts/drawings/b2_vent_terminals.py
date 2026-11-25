"""Placed B2 garage make-up air terminals with installation levels on M306 and M207."""
from hashlib import sha256
import json
from math import cos, dist, sin
from struct import pack

from b2_tray_segments import read_model


# Both B-revision sheets dimension each outlet as AH/D 1600 x 1250, one per
# tag, 25000 m3/h, bottom 0.300 m above floor. The room fan schedule has
# different design flows; these values are the outlet tags themselves.
PLAN_SOURCES = [
    ("M306", "6-06-E8711DB", "312d2fceceaa9854bfba908d914d20b6bad7249bc66ff08ce60effffbae8a3ba",
     "598085c909958327f018abf978034742435a4b0ae63220a46041d85d887ef100"),
    ("M207", "6-06-1ABEA58", "9c3af256157f7eed3c702de9b1f661303d9e380b3d61d4ccf78aec4030f6a750",
     "f06c4b8245ed239f55530761f4962ca5101b829733f919039ea4b31f5b72b9f0"),
]
FAMILY_ID = "1-06-BCF486A"
FAMILY_SHA = "ebd32e66f71aa8a7e4ceb6163dc9cdc44e5406db301edcfdc8da8fae1a3002a2"


def terminal_assets(root, records_by_id, terminals):
    sources = []
    for code, plan_id, plan_sha, pdf_sha in PLAN_SOURCES:
        plan, _ = read_model(root, records_by_id[plan_id], plan_sha)
        source, = plan["nodes"][0]["extras"]["sources"]
        if (source["code"] != code or source["sha256"] != pdf_sha or
                source["sourceRef"] != "A101" or not source["registered"] or
                source["matchedColumns"] != 75 or source["maxResidual"] > .005):
            raise ValueError(f"{code} registration differs from the checked B2 plan")
        sources.append(source)
    plan, corroboration = sources
    if (plan["scale"], plan["offset"]) != (corroboration["scale"], corroboration["offset"]):
        raise ValueError("Coordinate transforms disagree for matching M306 and M207 terminals")

    prototype, binary = read_model(root, records_by_id[FAMILY_ID], FAMILY_SHA)
    # The pinned family's +Z face is its discharge face, the local origin is
    # at the bottom and the transverse bounds are 0.50 x 0.23 m. Its actual
    # depth and blade construction are illustrative. Bring its forward-most
    # face to the measured line, without rescaling the unknown depth.
    front = .035783747686391636
    width, height, sill = 1.60, 1.25, .30
    for key, location, vent_path, smoke_path, start, end, yaw in terminals:
        if abs(dist(start, end) * plan["scale"] - width) > .001:
            raise ValueError(f"{key} plotted width differs from outlet annotation")
        x, z = [(a + b) / 2 * plan["scale"] + offset for a, b, offset in zip(start, end, plan["offset"])]
        origin = [round(x, 5), sill, round(z, 5)]
        object_id = f"B2-M306-AHD-{key}"
        name = f"消防补风口 · {location}"
        basis = ("M306 与 M207 的风口引线、面宽、朝向和底部距地标注相符。"
                 "原点取出风面底边中点，平面按 A101 柱心配准。"
                 "宽高和底标高按图，叶片、边框进深及紧固件沿用侧面风口示意原型。")
        fields = [
            ["图纸编号 / 服务", "AH/D · 车库消防排烟补风"],
            ["开口宽 × 高", "1600 × 1250 mm"],
            ["风口底 / 顶距地", "0.300 m / 1.550 m（顶高由开口高度推算）"],
            ["图注风量", "25000 m³/h"],
        ]
        evidence = dict(id=object_id, floor="B2", floorOrigin=origin, units="m", up="Y",
                        widthM=width, heightM=height, bottomAboveFloorM=sill,
                        floorReference="图注风口前本层地面", widthAxis="X", facingLocal="+Z",
                        pdfFaceLine=[start, end], sourceGeometry=[
                            dict(code=src["code"], sha256=src["sha256"], layer="ACS_SBG_FK", seqno=seq)
                            for src, seq in zip(sources, [vent_path, smoke_path])],
                        prototype=FAMILY_ID, prototypeSha256=FAMILY_SHA, basis=basis)
        nodes = list(prototype["nodes"])
        nodes.append(dict(name="按图纸宽高定位出风面", children=[0], scale=[width / .50, height / .23, 1],
                          translation=[0, 0, -front]))
        nodes.append(dict(name=name, children=[len(nodes) - 1], rotation=[0, sin(yaw / 2), 0, cos(yaw / 2)],
                          extras={"module": evidence}))
        scene = dict(prototype, nodes=nodes, scenes=[{"nodes": [len(nodes) - 1]}], scene=0)
        payload = json.dumps(scene, ensure_ascii=False, separators=(",", ":")).encode()
        payload += b" " * (-len(payload) % 4)
        blob = (pack("<4sIIII", b"glTF", 2, 20 + len(payload) + len(binary), len(payload), 0x4E4F534A)
                + payload + binary)
        yield dict(id=object_id, name=name, kind="installed", origin=origin,
                   views=["防烟与排烟", "普通通风"], sourceCodes=["M306", "M207"],
                   drawings="M306、M207", fields=fields, basis=basis, file=object_id + ".glb",
                   sha256=sha256(blob).hexdigest()), blob
