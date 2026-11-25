"""Collect original, verified B2 room instances and reusable component GLBs."""
import argparse
import hashlib
import io
import json
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

from build_b2_disciplines import checked, read_json
from b2_tray_segments import tray_assets
from b2_drain_segment import drain_assets
from b2_vent_terminals import terminal_assets
from b2_sump_sections import pit_assets
from b2_lighting_trays import lighting_tray_assets
from b2_data_trays import data_tray_assets

SURVEY_FILE = "B2图元复核.json"
SURVEY_SHA256 = "2c3abe2fa4e22309306e337c06ea991ffc7df8bffeb83f9124dd24db6b0711e5"

# These are catalog families related to a view. No floor coordinates are implied.
FAMILIES = {
    "动力配电": ["动力桥架", "动力配电箱"],
    "室内照明与应急": ["照明穿线管", "照明配电箱"],
    "普通通风": ["矩形风管", "送风管", "排风管", "风管弯头", "多叶调节风阀", "柜式离心风机箱"],
    "给排水": ["冲洗给水支管", "压力废水支路", "潜水泵", "止回阀"],
    "消火栓": ["消火栓环管", "消火栓立管", "室内消火栓箱"],
    "自动喷淋": ["自动喷淋干管", "喷淋配水支管", "喷淋喷头"],
    "防烟与排烟": ["排烟风管", "排烟阀"],
    "消防报警与联动": ["感烟探测器"],
    "消防物联网": ["工业交换机"],
    "综合安防": ["摄像机", "弱电分支线管"],
    "综合弱电": ["综合布线桥架", "弱电立管", "交换机"],
}


def room_views(name):
    views = ["承重结构"]
    if name.startswith(("JSK", "污水处理间", "水井")):
        views.append("给排水")
    if name.startswith(("送风机房", "送风井")):
        views.append("普通通风")
    if name.startswith("排烟机房"):
        views.append("防烟与排烟")
    if name.startswith("强电间"):
        views.extend(["动力配电", "室内照明与应急"])
    if name == "弱电间":
        views.extend(["综合安防", "综合弱电"])
    return views


def group_installations(archive, records):
    """One private request to open all located instances in a discipline."""
    groups = {}
    for record in records:
        if record["kind"] == "installed" or record.get("verticalExtentVerified"):
            for view in record["views"]:
                groups.setdefault(view, []).append(record["file"])
    bundles = {}
    for view, files in groups.items():
        if len(files) < 8:
            continue
        buffer = io.BytesIO()
        with ZipFile(buffer, "w") as batch:
            for file in files:
                batch.writestr(ZipInfo(file), archive.read(file), compress_type=ZIP_DEFLATED)
        content = buffer.getvalue()
        file = f"sets/{view}.zip"
        archive.writestr(file, content)
        bundles[view] = dict(file=file, sha256=hashlib.sha256(content).hexdigest())
    return bundles


def build(root, drawings, output, survey_path=None):
    survey_bytes = checked(survey_path or output.with_name(SURVEY_FILE), SURVEY_SHA256)
    survey = json.loads(survey_bytes)
    catalog = read_json(root / "全目录模型清单.json")
    floor = next(f for f in read_json(root / "楼层与跨层装配索引.json")["floors"] if f["level"] == "B2")
    by_id = {r["id"]: r for r in catalog["records"]}
    selected = {}
    for item in floor["rooms"]:
        record = by_id[item["id"]]
        origin = item["registration"]["floorOrigin"]
        if len(origin) != 3 or item["sha256"] != record["sha256"]:
            raise ValueError(item["id"])
        selected[item["id"]] = ("room", origin, room_views(item["name"]))
    for view, names in FAMILIES.items():
        for name in names:
            matches = [r for r in by_id.values() if r["name"] == name and r["category"] in ("设备与末端", "管线与连接件")]
            if len(matches) != 1:
                raise ValueError(name)
            selected.setdefault(matches[0]["id"], ("family", None, []))[2].append(view)
    output.parent.mkdir(parents=True, exist_ok=True)
    records = []
    temp = output.with_suffix(".tmp")
    try:
        with ZipFile(temp, "w", ZIP_DEFLATED) as archive:
            sections = {r["id"]: (r, blob) for r, blob in pit_assets(root, by_id, survey["sumpOutlines"])}
            for key, (kind, origin, views) in selected.items():
                r = by_id[key]
                if key in sections:
                    section, geometry = sections.pop(key)
                    if origin != section["origin"] or section["previousSha256"] != r["sha256"]:
                        raise ValueError(f"Unexpected source space for {key}")
                    archive.writestr(section["file"], geometry)
                    records.append(section)
                    continue
                filename = key + ".glb"
                archive.writestr(filename, checked(root / r["file"], r["sha256"]))
                records.append(dict(id=key, name=r["name"], kind=kind, origin=origin, views=views,
                                    file=filename, sha256=r["sha256"], drawings=r["drawings"], basis=r["basis"]))
            if sections:
                raise ValueError("A sump section has no matching B2 space")
            for derive, survey_section in (
                (tray_assets, "powerTrays"), (drain_assets, "drainLine"),
                (terminal_assets, "ventTerminals"), (lighting_tray_assets, "lightingTrays"),
                (data_tray_assets, "dataTrays"),
            ):
                for record, geometry in derive(root, by_id, survey[survey_section]):
                    archive.writestr(record["file"], geometry)
                    records.append(record)
            # Other source sheets (e.g. E401 and M306) are already in the professional pack.
            sheet_ids = {"S301": "03.014", "S401": "03.021", "P202": "05.011", "P003": "05.005"}
            sheets_by_id = {s["id"]: s for s in catalog["drawingIndex"]}
            sources = []
            used_codes = {code for record in records for code in record.get("sourceCodes", [])}
            for code, sheet_id in sheet_ids.items():
                if code not in used_codes:
                    continue
                sheet = sheets_by_id[sheet_id]
                if sheet["code"] != code:
                    raise ValueError(f"Unexpected source sheet for {code}")
                filename = sheet_id + ".pdf"
                archive.writestr(filename, checked(drawings / sheet["relativeFile"], sheet["sha256"]))
                sources.append(dict(id=sheet_id, code=code, name=sheet["name"], file=filename, sha256=sheet["sha256"]))
            bundles = group_installations(archive, records)
            manifest = dict(version=3, floor=-2, reference=floor["sourceSha256"], objects=records, sources=sources, installationPacks=bundles)
            payload = json.dumps(manifest, ensure_ascii=False, separators=(",", ":")).encode()
            archive.writestr("objects.json", payload)
            archive.writestr(SURVEY_FILE, survey_bytes)
        if temp.stat().st_size > 20 * 1024 * 1024:
            raise ValueError("Floor attachment exceeds 20 MiB")
        temp.replace(output)
        print(len(records), "GLBs;", output.stat().st_size, "bytes;", hashlib.sha256(payload).hexdigest())
    finally:
        temp.unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ("catalog", "drawings", "output"):
        parser.add_argument(arg, type=Path)
    parser.add_argument("--survey", type=Path, help=f"Private reviewed coordinates (default: {SURVEY_FILE} beside output)")
    args = parser.parse_args()
    build(args.catalog, args.drawings, args.output, args.survey)
