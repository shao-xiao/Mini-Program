"""Build two B2 communications tray details from the ELV-PDS leaders on 21-001."""
from hashlib import sha256
import json
from math import ceil, dist
from struct import pack

from b2_tray_segments import read_model


DRAWING = "0c0cf166f774a0d4d6e55105213f4cabe6a25ac447b0d17265627af6d9c0a5b1"
FLOOR = "6-11-23A2DF9"
FLOOR_SHA = "57b2fcad09991268d3151061ed836b218c966f6caf8a9eeb2d8440c79e6e40d7"
PROTOTYPE = "2-11-EAC1C36"
FAMILY_SHA = "92aa3b085cab3d180a428583e08a0aee37b86498fac621c7a42f1110f8263e70"


def section_model(prototype, binary, length, root_name, attributes):
    # The r1 prototype has an outside length of 1.25 m along X, source
    # bounds Y[.0185,.143], Z[-.176,.176]. Reuse its mesh buffers; distribute
    # the illustrative rungs over a long run without stretching one rung.
    count = ceil(length / 1.25)
    pitch = length / count
    y_scale = .1 / .1245
    nodes = []
    copies = []
    for i in range(count):
        start = len(nodes)
        for original in prototype["nodes"]:
            node = dict(original)
            if "children" in node:
                node["children"] = [idx + start for idx in node["children"]]
            nodes.append(node)
        copies.append(len(nodes))
        nodes.append(dict(name="示意构造单元", children=[start], scale=[pitch / 1.25, y_scale, .2 / .352],
                          translation=[(i + .5) * pitch - length / 2, -.0185 * y_scale, 0]))
    nodes.append(dict(name=root_name, children=copies, extras={"module": attributes}))
    model = dict(prototype, scene=0, nodes=nodes, scenes=[{"nodes": [len(nodes)-1]}])
    payload = json.dumps(model, ensure_ascii=False, separators=(",", ":")).encode()
    payload += b" " * (-len(payload) % 4)
    return (pack("<4sIIII", b"glTF", 2, 20 + len(payload) + len(binary), len(payload), 0x4E4F534A)
            + payload + binary)


def data_tray_assets(root, records_by_id, survey):
    plan, _ = read_model(root, records_by_id[FLOOR], FLOOR_SHA)
    source, = plan["nodes"][0]["extras"]["sources"]
    if (not source["registered"] or source["sourceRef"] != "A101" or source["sha256"] != DRAWING or
            source.get("axisMatches") != [14, 13] or source["maxResidual"] > .008 or
            "0CSCEC-布线桥架" not in source["selectedLayers"]):
        raise ValueError("21-001 revision, layer or grid registration changed")
    prototype, binary = read_model(root, records_by_id[PROTOTYPE], FAMILY_SHA)
    if abs(survey["outlineSeparationPt"] * source["scale"] - .200) > .005:
        raise ValueError("Annotated 200 mm width no longer matches the registered outline")
    for label, line, outline, leader, landing, x1, x2, bl in survey["runs"]:
        if not x1 < landing < x2:
            raise ValueError("Leader does not terminate on the reviewed straight section")
        endpoints = [[round(x * source["scale"] + source["offset"][0], 6),
                      round(survey["centerlineY"] * source["scale"] + source["offset"][1], 6)] for x in (x1, x2)]
        length = dist(*endpoints)
        name = f"综合布线桥架 · 南侧{label} · BL+{bl:.3f}"
        identifier = f"B2-21-001-ELV-PDS-{line}"
        basis = ("21-001 A 版的 ELV-PDS 引线指向布线桥架直段，图层为 0CSCEC-布线桥架。"
                 "按 A101 轴网配准并量取直段长度。两段分别保存，变高过渡、支吊架、连接件间距和厂型待核。"
                 "模型近看复用综合布线桥架的侧帮及底托原型，不代表内部横档的实测数量。")
        attributes = dict(id=identifier, floor="B2", units="m", up="Y", floorOrigin=None,
                          endpointsXZ=endpoints, lengthM=length, nominalSizeMm=[200, 100],
                          sourceSha256=DRAWING, sourceCode="21-001", sourceLayer="0CSCEC-布线桥架",
                          centerlineSequence=line, outlineSequence=outline, leaderSequence=leader,
                          leaderTipPdf=[landing, survey["centerlineY"]], sourceHeightText=f"BL+{bl:.3f}",
                          absoluteElevationM=None, relativeDifferenceWestMinusEastM=.30,
                          prototypeId=PROTOTYPE, prototypeSha256=FAMILY_SHA, basis=basis)
        blob = section_model(prototype, binary, length, name, attributes)
        fields = [["原图系统 / 规格", "ELV-PDS · 弱电综合布线桥架 CT 200 × 100 mm"],
                  ["原图标高文字", f"BL+{bl:.3f}"],
                  ["图面直段长", f"约 {length:.3f} m"],
                  ["两段相对高差", "西侧 BL+2.700、东侧 BL+2.400，东侧读数低 0.300 m。"],
                  ["标高待核", "BL 对应的测量面及高程基准待核。此段可以在平面定位，近看使用构件局部坐标。"]]
        yield dict(id=identifier, name=name, kind="segment", origin=None, planLine=endpoints,
                   views=["综合弱电"], drawings="21-001", sourceCodes=["21-001"], fields=fields,
                   basis=basis, file=identifier + ".glb", sha256=sha256(blob).hexdigest()), blob
