"""Package the reviewed B2 r1 models and their source sheets as a private floor attachment."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def checked(path, expected):
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError(f"Source digest mismatch: {path.name}")
    return data


def build(root, drawings, output):
    catalog = read_json(root / "全目录模型清单.json")
    floor = next(f for f in read_json(root / "楼层与跨层装配索引.json")["floors"] if f["level"] == "B2")
    records = {r["id"]: r for r in catalog["records"]}
    sheets = {s["id"]: s for s in catalog["drawingIndex"]}
    manifest = dict(version=catalog["version"], floor=-2, reference=floor["sourceSha256"], models=[], sources=[])
    included = set()
    output.parent.mkdir(parents=True, exist_ok=True)
    temp = output.with_suffix(".tmp")
    try:
        with ZipFile(temp, "w", ZIP_DEFLATED) as archive:
            for item in [*floor["registeredSystems"], records["6-03-74CB110"]]:
                r = records[item["id"]]
                file = r["id"] + ".glb"
                archive.writestr(file, checked(root / r["file"], item["sha256"]))
                refs = []
                for sid in r["sources"].split():
                    sheet = sheets[sid]
                    # For the B2 column model include the column plan. Foundation
                    # and pile drawings remain catalog references for the building.
                    if r["id"] == "6-03-74CB110" and sheet["code"] != "S201":
                        continue
                    refs.append(sid)
                    if sid not in included:
                        pdf = sid + ".pdf"
                        archive.writestr(pdf, checked(drawings / sheet["relativeFile"], sheet["sha256"]))
                        manifest["sources"].append(dict(id=sid, code=sheet["code"], file=pdf, name=sheet["name"], sha256=sheet["sha256"]))
                        included.add(sid)
                manifest["models"].append(dict(id=r["id"], name=r["name"].split("·", 1)[-1], file=file,
                                               sha256=r["sha256"], basis=r["basis"], drawings=r["drawings"], sources=refs))
            payload = json.dumps(manifest, ensure_ascii=False, separators=(",", ":")).encode()
            archive.writestr("disciplines.json", payload)
        if temp.stat().st_size > 20 * 1024 * 1024:
            raise ValueError("Floor attachment exceeds 20 MiB")
        temp.replace(output)
        print(f'{len(manifest["models"])} GLBs, {len(included)} PDFs, {output.stat().st_size} bytes')
        print("Manifest SHA-256", hashlib.sha256(payload).hexdigest())
    finally:
        temp.unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ("catalog", "drawings", "output"):
        parser.add_argument(arg, type=Path)
    args = parser.parse_args()
    build(args.catalog, args.drawings, args.output)
