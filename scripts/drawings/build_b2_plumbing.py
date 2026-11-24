"""Build the authenticated B2 plumbing supplement from the checked as-built PDF revisions (PyMuPDF)."""
import argparse
import hashlib
import json
from pathlib import Path
from statistics import median
from zipfile import ZIP_DEFLATED, ZipFile

import pymupdf

SHEETS = {
    "A101": ("地下二层平面图", "001bc5df93a31166fa62a5ee8d6223b1ee2ad228b4fe15d6c77bc85e7fc6008e"),
    "P301": ("地下二层给排水消防平面图", "405719a0eef316977231e95264bccfb0663bf0a4c0c382df600d33ccccd042e4"),
    "P202": ("地下室排水系统原理图", "8b180878ea71cf551f8b690fea775d3006b96aa5b7c5654fba576b7b9d0417f3"),
    "P203": ("地下室生活给水系统图、地下室冲洗水系统图", "455b22bb8b82c3d4760fe3afac71fc27429203c12e9dc7aaa6829d6ac870214d"),
    "P204": ("地下室 B2 层室内消火栓系统图", "5ea11a8cbb178ce990a2831bace48e7d02cea16b6292313211e710571fab9889"),
    "P401": ("地下二层自动喷淋平面图", "ad275d1513bf0cbf60ce544b4512bf69f0af0457e73a68d01d8bd8f101c9f622"),
    "P206": ("自动喷淋系统原理图", "7572ce7c6e91ad7b73f0a7e0ed050ee07e651554da66e776f1b732e0bd11e2c2"),
    "P501": ("消防泵房给排水大样图", "18a618ecb9cbca16430fb6480218bce1ad69ab20ffa5c25d9e43326216f44953"),
    "P502": ("生活泵房给排水大样图", "fa7f1b8d91259a2634ae1f6e74ffb2cd26838c4a9dc1b38589bf04ad70f454ce"),
    "P302": ("地下一层给排水消防平面图（跨层对照）", "b29958049ed2c98771f60fa859f6cc242268772856cacf2cda1e0d18f2240408"),
}
# Confirmed in P301 at the pit symbols in this CAD revision's paint order.
PIT_NAMES = ["JSK1-1", "JSK1-3", "JSK3-1", "JSK1-4", "JSK2-4", "JSK2-2",
             "JSK2-5", "JSK2-6", "JSK2-1", "JSK1-2", "JSK2-3"]

# Inlet label, pressure riser, nominal riser diameter and outlet reference on P202.
PIT_CONNECTIONS = {
    "JSK1-1": ("汽车坡道及楼梯平台地漏 · DFL-5、DFL-6", "YYL-1", 100, "Y1"),
    "JSK1-4": ("汽车坡道地漏 · DFL-9", "YYL-2", 100, "Y5"),
    "JSK1-2": ("消防泵房地漏 · DFL-10", "YFL-4", 100, "Y2"),
    "JSK1-3": ("生活泵房地漏 · DFL-8", "YFL-6", 100, "W1"),
    "JSK2-4": (None, "YFL-3", 80, "W1"),
    "JSK2-5": ("车库冲洗地漏 DFL-1、地面排水地漏及 DFL-4 支路", "YFL-7", 80, "W1"),
    "JSK2-1": ("非机动车坡道地漏 · DFL-7", "YFL-1", 80, "W2"),
    "JSK2-2": (None, "YFL-2", 80, "W2"),
    "JSK2-3": ("车库冲洗地漏 · DFL-3", "YFL-5", 80, "W2"),
    "JSK2-6": ("车库冲洗地漏 · DFL-2", "YFL-8", 80, "W2"),
    "JSK3-1": ("消防电梯基坑 · 预埋 DN150 管", "YYL-3", 100, "Y2"),
}


def pump_spec(name):
    standby = name.startswith("JSK2")
    levels = (-.4, -1.3, -.3) if standby else (-.4, -1.25, -.3)
    if name == "JSK2-5":
        levels = (-.45, -1.3, -.35)
    elif name == "JSK3-1":
        levels = (-2.1, -3.05, -2.)
    return {"pump": "50WQ15-15-1.5" if standby else "80WQ40-15-4.0",
            "duty": "一用一备" if standby else "两用", "flow": 15 if standby else 40,
            "head": 15, "power": 1.5 if standby else 4, "levels": levels}


def center(d):
    r = d["rect"]
    return (r.tl + r.br) / 2


def register(plan, base):
    a = [center(d) for d in base if d["layer"] == "COLUMN" and d["type"] == "fs"]
    b = [center(d) for d in plan if (d["layer"] or "").endswith("$0$COLUMN")]
    pairs = [(q, min(a, key=lambda p: abs(p-q))) for q in b]
    scale = 81.600 / 1542.03  # same A101 dimension-chain calibration as build_b2_model.py
    if len(pairs) != 75 or len({tuple(p) for _, p in pairs}) != len(a):
        raise ValueError("Column registration failed")
    offset = pymupdf.Point(*(median(p[i]-q[i] for q,p in pairs) for i in (0,1)))
    error = max(abs(q+offset-p) for q,p in pairs)*scale*1000
    if error > 10:
        raise ValueError(f"Column alignment exceeds tolerance: {error:.2f} mm")

    def metric(p):
        pos = p + offset - pymupdf.Point(326.58, 93.82)
        return [round(v*scale, 3) for v in pos]

    return metric, {"columns": len(a), "errorMm": round(error, 2)}


def centerline(d):
    # CAD outputs each solid stroke as two triangular faces. Join midpoints
    # of their short end edges, preserving diagonal runs.
    r = d["rect"]
    if min(r.width, r.height) <= 1.25:
        c = center(d)
        if r.width >= r.height:
            return pymupdf.Point(r.x0,c.y), pymupdf.Point(r.x1,c.y)
        return pymupdf.Point(c.x,r.y0), pymupdf.Point(c.x,r.y1)
    pts = set(tuple(p) for item in d["items"] if item[0] == "l" for p in item[1:])
    if len(pts) != 4:
        raise ValueError("Unexpected pipe stroke geometry")
    pts = [pymupdf.Point(p) for p in sorted(pts)]
    p = pts.pop(0)
    q = min(pts, key=lambda v: abs(v-p))
    pts.remove(q)
    return (p+q)/2, (pts[0]+pts[1])/2


def build(root, output):
    sources = {}
    for code, (_, checksum) in SHEETS.items():
        path, = root.glob(f"*/*-{code}[-_]*.pdf")
        if hashlib.sha256(path.read_bytes()).hexdigest() != checksum:
            raise ValueError(f"{code} revision changed; review title, calibration and parameters")
        sources[code] = path
    with pymupdf.open(sources["A101"]) as doc:
        base = doc[0].get_drawings()
    with pymupdf.open(sources["P301"]) as doc:
        plan = doc[0].get_drawings()
    metric, registration = register(plan, base)
    items, pits = [], []
    for d in plan:
        tag = d["layer"]
        if tag == "建筑底图$0$LVTRY":
            r = d["rect"]
            if 25 < r.width < 60 and 23 < r.height < 60 and all(abs(center(d)-p)>3 for p in pits):
                pits.append(center(d))
        if tag.startswith("PIPE-") and d["type"] == "fs":
            if max(d["rect"].width, d["rect"].height) <= 1.25:
                continue  # corner fills and line caps
            a,b = centerline(d)
            if abs(a-b) < .01:
                continue
            kind, points = "管段", [metric(a), metric(b)]
        elif tag.startswith("VPIPE-"):
            kind, points = "立管", [metric(center(d))]
        else:
            continue
        layer = tag.split("-")[1]
        system = {"给水":"supply", "消防":"hydrant"}.get(layer, "drain")
        items.append({"system":system, "layer":layer, "kind":kind, "points":points})
    if len(pits) != len(PIT_NAMES):
        raise ValueError("Pit symbol inventory differs from the reviewed plan")
    for name, point in zip(PIT_NAMES, pits):
        items.append({"system":"drain", "layer":"集水坑", "kind":"集水坑", "name":name,
                      "points":[metric(point)], "connection":dict(zip(("inlet","riser","dn","outlet"), PIT_CONNECTIONS[name])), **pump_spec(name)})
    with pymupdf.open(sources["P401"]) as doc:
        sprinkler = doc[0].get_drawings()
    locate, sprinkler_registration = register(sprinkler, base)
    for drawing in sprinkler:
        tag = drawing["layer"]
        if tag == "EQUIP_喷头":
            kind, points = "喷头", [locate(center(drawing))]
        elif tag == "VPIPE-喷淋":
            kind, points = "立管", [locate(center(drawing))]
        elif tag in ("PIPE-喷淋", "PIPE-喷淋中") and drawing["type"] == "fs":
            if max(drawing["rect"].width, drawing["rect"].height) <= 1.25:
                continue
            a, b = centerline(drawing)
            kind, points = "管段", [locate(a), locate(b)]
        else:
            continue
        items.append({"system":"sprinkler", "layer":"喷淋", "kind":kind, "points":points})
    sources_list = []
    with ZipFile(output, "w", ZIP_DEFLATED) as archive:
        for code, (title, checksum) in SHEETS.items():
            if code == "A101":
                continue
            archive.write(sources[code], code+".pdf")
            sources_list.append({"group":"给排水","code":code,"name":title,"file":code+".pdf","sha256":checksum})
        model = {"version":2, "sprinklerRegistration":sprinkler_registration, "floor":-2, "buildingCode":"BLD-3C5CD7268F55",
                 "reference":SHEETS["A101"][1], "sources":sources_list, "registration":registration, "items":items}
        archive.writestr("plumbing.json", json.dumps(model, ensure_ascii=False, separators=(",",":")))
    print(registration, {kind:sum(i["kind"]==kind for i in items) for kind in ("管段","立管","集水坑","喷头")})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    build(args.root, args.output)
