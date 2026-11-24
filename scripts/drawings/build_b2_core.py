"""Register the architectural portion of B2 detail A401 to its private A101 model."""
import argparse
from collections import defaultdict
import hashlib
import json
import math
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

from build_b2_model import cad_layers, drawing


def wall_rectangles(objects, scale):
    # Accept parallel wall faces only when at least two 45-degree material
    # hatches actually bridge them. Openings split the continuous face.
    segments = []
    for obj in objects:
        if obj['fill'] or obj['tag'] != 'WALL':
            continue
        pts = obj['pts']
        segments.extend(zip(pts, pts[1:] + ([pts[0]] if obj['object_type'] == 'rect' else [])))
    rectangles = set()
    for dim in (0, 1):
        cross = 1 - dim
        edges, hatches = defaultdict(list), defaultdict(list)
        for a, b in segments:
            lo, hi = sorted((round(a[cross], 2), round(b[cross], 2)))
            if lo == hi:
                edges[lo].append(tuple(sorted((a[dim], b[dim]))))
            elif abs(abs(a[dim]-b[dim]) - (hi-lo)) < .06:
                hatches[lo, hi].append((min(a[dim], b[dim]), max(a[dim], b[dim])))
        for (lo, hi), marks in hatches.items():
            if not .095 <= (hi-lo)*scale <= .61:
                continue
            for a, b in edges[lo]:
                for c, d in edges[hi]:
                    left, right = max(a,c), min(b,d)
                    if sum(left-.04 <= x and y <= right+.04 for x,y in marks) < 2:
                        continue
                    box = (left,lo,right,hi) if dim == 0 else (lo,left,hi,right)
                    rectangles.add(tuple(round(v,2) for v in box))
    return [[(a,b),(c,b),(c,d),(a,d)] for a,b,c,d in sorted(rectangles)]


def build(root, base, output):
    source = drawing(root, '02建筑/*A401-核心筒放大图（一）.pdf')
    raw = source.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    if digest != '843d3ffe21965d8ec3620d55509b7341065a3d8a048dc5f7f43fa772f7ac0a9a':
        raise ValueError('A401 revision changed. Recheck detail level, scale and registration.')
    with ZipFile(base) as archive:
        model = json.loads(archive.read('model.json'))
    reference = next(s['sha256'] for s in model['sources'] if s['code'] == 'A101')
    if reference != '001bc5df93a31166fa62a5ee8d6223b1ee2ad228b4fe15d6c77bc85e7fc6008e':
        raise ValueError('The A101 reference revision does not match.')
    # Scale printed 1:50. The upper plan in A401 is B2; lower vignettes
    # have independent scales. Use only vectors within the plan's model area.
    items = [v for v in cad_layers(source) if 100 < v['x0'] and v['x1'] < 2160
             and 140 < v['top'] and v['bottom'] < 890]
    scale = 50 * .0254 / 72
    def metric(pt):
        return [round(origin+(coord-anchor)*scale, 3)
                for coord,anchor,origin in zip(pt,(161.70,223.30),(21.05,34.00))]
    columns = [[metric(pt) for pt in v['pts']] for v in items if v['tag']=='COLUMN' and v['object_type']=='rect']
    def discrepancy(a,b):
        return max(min(math.dist(p,q) for q in b) for p in a)
    matches = [min(enumerate(model['geometry']['columns']), key=lambda entry: discrepancy(col,entry[1])) for col in columns]
    error = max(discrepancy(col, ref) for col,(_,ref) in zip(columns,matches))
    if len(columns)!=14 or len({i for i,_ in matches})!=14 or error > .003:
        raise ValueError(f'Column outline registration failed: {len(columns)} outlines, {error:.4f} m')
    walls = [[metric(pt) for pt in ring] for ring in wall_rectangles(items, scale)]
    lines = []
    for obj in items:
        if obj['tag'] != 'WALL' or obj['fill']:
            continue
        pts = obj['pts']
        # Diagonal parallel hatch strokes describe material, not additional edges.
        if len(pts) == 2 and abs(abs(pts[1][0]-pts[0][0])-abs(pts[1][1]-pts[0][1])) < .06:
            continue
        lines.append([metric(pt) for pt in pts + ([pts[0]] if obj['object_type']=='rect' else [])])
    if not walls:
        raise ValueError('Missing hatch-confirmed wall faces')
    detail = dict(buildingCode=model['buildingCode'],floor=-2,version=1,reference=reference,
                  lines=lines, columns=columns, columnIndices=[i for i,_ in matches], walls=walls,
                  maxErrorMm=round(error*1000,2),source=dict(group='建筑', code='A401',name=source.name,file='A401.pdf',sha256=digest))
    with ZipFile(output,'w',ZIP_DEFLATED) as out:
        out.writestr('core.json',json.dumps(detail,ensure_ascii=False,separators=(',',':')))
        out.writestr('A401.pdf',raw)
    print(f'{len(columns)} registered column footprints, {len(walls)} verified wall portions, maximum vertex difference {error*1000:.2f} mm')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('root','base','output'):
        parser.add_argument(name, type=Path)
    args = parser.parse_args()
    build(args.root,args.base,args.output)
