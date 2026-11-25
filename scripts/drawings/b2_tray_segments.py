"""B2 tray samples from E401-C. Only the horizontal positions are registered."""
from hashlib import sha256
import json
from math import atan2, sin, cos, dist
from struct import pack, unpack_from


POWER_MODEL = "6-07-1161587"
TRAY_FAMILY = "2-07-56559D4"
REVISION_SHA = "55b939f10d74f9a9eadff20b3ebc78d54223a699a68a5da374abdb78d8a9f53b"
FAMILY_SHA = "fd5b4586acbdbdfbefe4d34df48cdfc998f0d3b914d1b830f401ee8edc9e9839"
POWER_SHA = "6f41dd39a30f739f5db17a417f86f6ad95ea8ae047931927f0b438c70e1af0f3"


def read_model(root, record, expected):
    blob = (root / record["file"]).read_bytes()
    if sha256(blob).hexdigest() != expected or record["sha256"] != expected:
        raise ValueError(f"Reviewed r1 GLB changed: {record['id']}")
    size, typ = unpack_from("<I4s", blob, 12)
    if blob[:4] != b"glTF" or typ != b"JSON":
        raise ValueError("Expected binary glTF with JSON header")
    return json.loads(blob[20:20 + size]), blob[20 + size:]


def tray_assets(root, records_by_id, trays):
    plan, _ = read_model(root, records_by_id[POWER_MODEL], POWER_SHA)
    source = plan["nodes"][0]["extras"]["sources"][0]
    if source["sha256"] != REVISION_SHA or source["sourceRef"] != "A101" or not source["registered"]:
        raise ValueError("E401 registration does not match the reviewed sheet")
    prototype, binary_chunk = read_model(root, records_by_id[TRAY_FAMILY], FAMILY_SHA)
    records = []
    for sequence, leader, edge_a, edge_b, width, height in trays:
        points = []
        for a, b in zip(edge_a, edge_b):
            if abs(dist(a, b) * source["scale"] - width / 1000) > .003:
                raise ValueError("Drawn outline and annotated tray width disagree")
            points.append([round((v + w) / 2 * source["scale"] + offset, 5)
                           for v, w, offset in zip(a, b, source["offset"])])
        length = dist(*points)
        yaw = -atan2(points[1][1] - points[0][1], points[1][0] - points[0][0])
        sample_id = f"B2-E401-TRAY-{sequence}"
        name = f"强电消防金属桥架 · {width}×{height} mm 直段"
        basis = (f"E401 C版标注 {width}×{height} mm，梁下300安装。"
                 f"直段两端取自 CABLETRAY 第 {sequence} 图元，按 A101 配准，图面长度约 {length:.3f} m。"
                 "房间附近的引线和竖向符号另计。梁底和桥架的绝对标高待核；仅作局部截面与平面定位。"
                 "外形沿用动力桥架目录原型，侧帮、底托及压板细节为示意。")
        placement = dict(floor="B2", source="E401", sourcePdfSha256=REVISION_SHA,
                         geometryPath=sequence, annotationLeader=leader, endpointsXZ=points,
                         nominalSizeMm=[width, height], lengthM=round(length, 5),
                         relativeMount="梁下300安装", elevationM=None, floorOrigin=None,
                         prototypeId=TRAY_FAMILY, prototypeSha256=FAMILY_SHA)
        # The pinned r1 prototype (18 nodes) faces +X. Its outside bounds are
        # X[-.625,.625], Y[.0185,.143], Z[-.176,.176]; the outer splice plates
        # are included. Preserve materials and binary meshes; change instance TRS.
        scale_y = height / 1000 / .1245
        assembly = dict(name=name, children=[0], translation=[0, -.0185 * scale_y, 0],
                        scale=[length / 1.25, scale_y, width / 1000 / .352],
                        rotation=[0, sin(yaw / 2), 0, cos(yaw / 2)],
                        extras=dict(placement, units="m", up="Y", sourceBasis=basis))
        scene = dict(prototype, scenes=[{"nodes": [len(prototype["nodes"])]}], scene=0,
                     nodes=[*prototype["nodes"], assembly])
        payload = json.dumps(scene, ensure_ascii=False, separators=(",", ":")).encode()
        payload += b" " * (-len(payload) % 4)
        blob = (pack("<4sIIII", b"glTF", 2, 20 + len(payload) + len(binary_chunk), len(payload), 0x4E4F534A)
                + payload + binary_chunk)
        midpoint = [round((a + b) / 2, 5) for a, b in zip(*points)]
        record = dict(id=sample_id, name=name, kind="segment", origin=None, planXZ=midpoint,
                      views=["动力配电"], drawings="E401", sourceCodes=["E401","S301","S401"], basis=basis, placement=placement,
                      file=sample_id + ".glb", sha256=sha256(blob).hexdigest())
        records.append((record, blob))
    return records
