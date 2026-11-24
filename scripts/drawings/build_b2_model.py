"""Build a private B2 drawing attachment from verified CAD geometry, using pdfplumber and pypdf."""
import argparse
import hashlib
import json
import math
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

import pdfplumber
from pdfplumber.page import PDFPageAggregatorWithMarkedContent
from pypdf import PdfReader


def drawing(root, pattern):
    matches = list(root.glob(pattern))
    if len(matches) != 1:
        raise ValueError(f"Expected one source for {pattern}, found {len(matches)}")
    return matches[0]


def cad_layers(source):
    resources = PdfReader(source).pages[0]["/Resources"]["/Properties"]
    names = {key[1:]: value.get_object().get("/Name") for key, value in resources.items()}
    original = PDFPageAggregatorWithMarkedContent.begin_tag

    def begin_tag(device, tag, props=None):
        original(device, tag, props)
        device.cur_tag = names.get(getattr(props, "name", ""), device.cur_tag)

    PDFPageAggregatorWithMarkedContent.begin_tag = begin_tag
    try:
        with pdfplumber.open(source) as doc:
            page = doc.pages[0]
            return [obj for kind in ("line", "rect", "curve") for obj in page.objects[kind]
                    if obj.get("tag") in {"WALL", "COLUMN", "A-Car", "STAIR", "EVTR", "AXIS"}]
    finally:
        PDFPageAggregatorWithMarkedContent.begin_tag = original


def generate(source):
    items = cad_layers(source)
    # Outside dimension strings, A101. Check the Y chain independently of the X calibration.
    axes = [v for v in items if v["tag"] == "AXIS" and v["object_type"] == "line"]
    x_chain = max(v["width"] for v in axes)
    y_chain = max(v["height"] for v in axes)
    scale = 81.600 / x_chain
    if abs(y_chain * scale - 77.700) > .003 or abs(scale - 150 * .0254 / 72) > .0001:
        raise ValueError("A101 dimension chains do not match this revision")

    def metric(point):
        return [round((point[0] - 326.58) * scale, 3), round((point[1] - 93.82) * scale, 3)]

    result = {"walls": [], "columns": [], "bays": [], "lines": []}
    for tag, group in (("WALL", "walls"), ("COLUMN", "columns")):
        fills = [v for v in items if v["tag"] == tag and v.get("fill")]
        if len(fills) % 2:
            raise ValueError(f"Unpaired {tag} faces")
        for first, second in zip(fills[::2], fills[1::2]):
            points = set(tuple(metric(p)) for record in (first, second) for p in record["pts"])
            if len(points) != 4:
                raise ValueError(f"Unverified {tag} outline")
            cx, cy = [sum(p[i] for p in points) / 4 for i in (0, 1)]
            result[group].append(sorted(points, key=lambda p: math.atan2(p[1] - cy, p[0] - cx)))
    for obj in items:
        tag, kind = obj["tag"], obj["object_type"]
        pts = [metric(p) for p in obj["pts"]]
        if tag == "A-Car" and (kind == "rect" or (kind == "curve" and len(pts) == 5 and pts[0] == pts[-1])):
            result["bays"].append(pts[:4])
        elif tag in ("WALL", "STAIR", "EVTR") and not obj["fill"]:
            result["lines"].append(pts + ([pts[0]] if kind == "rect" else []))
    if len(result["bays"]) != 158:
        raise ValueError("Parking outlines disagree with the printed count of 158")
    outline = [(326.58, 93.82), (1817.58, 93.82), (1817.58, 530.35), (1868.61, 530.35),
               (1868.61, 1534.51), (1841.67, 1562.14), (326.58, 1562.14)]
    wall_vertices = {tuple(p) for face in result["walls"] for p in face}
    result["outline"] = [metric(p) for p in outline]
    if any(tuple(p) not in wall_vertices for p in result["outline"]):
        raise ValueError("Outer ring does not follow the verified WALL vertices")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    root = args.root
    plan = drawing(root, "02建筑/*A101-地下二层平面图.pdf")
    section = drawing(root, "02建筑/*A301-1-1剖面图.pdf")
    for path, expected in (
        (plan, "001bc5df93a31166fa62a5ee8d6223b1ee2ad228b4fe15d6c77bc85e7fc6008e"),
        (section, "958a5c08e495af74fbc3c71cc54bdc74f5334b48de8b475edc5c62c1b62f4748"),
    ):
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise ValueError(f"Revision changed; recheck scale, levels and printed quantities in {path.name}")
    geometry = generate(plan)
    sources = [("建筑", "A101", plan), ("剖面", "A301", section)]
    for group, code, pattern in [
        ("给排水", "P301", "05给排水/*P301-地下二层给排水消防平面图.pdf"),
        ("给排水", "P302", "05给排水/*P302-地下二层给排水消防平面图.pdf"),
        ("强电", "E302", "07强电/*E302-地下二层照明平面图.pdf"),
        ("消防", "P401", "05给排水/*P401*地下二层自动喷淋平面图.pdf"),
        ("弱电", "21-001", "11弱电/*21-001-地下二层综合弱电平面图.pdf"),
    ]:
        sources.append((group, code, drawing(root, pattern)))
    manifest = {"version": 1, "buildingCode": "BLD-3C5CD7268F55", "floor": -2, "geometry": geometry,
                "area": 6278, "elevation": -9.2, "upperElevation": -5.4, "span": [81.6, 77.7], "sources": []}
    with ZipFile(args.output, "w", ZIP_DEFLATED) as archive:
        for group, code, path in sources:
            raw = path.read_bytes()
            key = code + ".pdf" if group in ("建筑", "剖面") else None
            manifest["sources"].append({"group": group, "code": code, "name": path.name, "file": key,
                                        "sha256": hashlib.sha256(raw).hexdigest()})
            if key:
                archive.writestr(key, raw)
        archive.writestr("model.json", json.dumps(manifest, ensure_ascii=False, separators=(",", ":")))
    print("Verified CAD geometry:", {key: len(value) for key, value in geometry.items()})


if __name__ == "__main__":
    main()
