"""Validate the model's cuboid bounds across the entire authored animation."""
from pathlib import Path
import itertools
import json
import math
import sys

model_path = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parent / 'iron_soul_cage.bbmodel'
HERE = model_path.parent
model = json.loads(model_path.read_text(encoding='utf-8'))
groups = {group['uuid']: group for group in model['groups']}
parents, owners = {}, {}


def visit(nodes, parent=None):
    for node in nodes:
        if isinstance(node, str):
            owners[node] = parent
        else:
            parents[node['uuid']] = parent
            visit(node['children'], node['uuid'])


visit(model['outliner'])


def rotate(point, angles):
    # THREE.Euler XYZ: apply Z, then Y, then X.
    x, y, z = point
    a, b, c = map(math.radians, angles)
    x, y = x*math.cos(c)-y*math.sin(c), x*math.sin(c)+y*math.cos(c)
    x, z = x*math.cos(b)+z*math.sin(b), -x*math.sin(b)+z*math.cos(b)
    y, z = y*math.cos(a)-z*math.sin(a), y*math.sin(a)+z*math.cos(a)
    return [x, y, z]


def transform(point, pivot, angles, offset=(0, 0, 0)):
    rotated = rotate([a-b for a, b in zip(point, pivot)], angles)
    return [a+b+c for a, b, c in zip(rotated, pivot, offset)]


def bounds(animation=None, time=0):
    def track(bone, channel):
        frames = animation.get('animators', {}).get(bone, {}).get('keyframes', []) if animation else []
        frames = sorted((frame for frame in frames if frame['channel'] == channel), key=lambda frame: frame['time'])
        if not frames:
            return [0, 0, 0]
        def value(frame):
            return [float(frame['data_points'][0][axis]) for axis in 'xyz']
        if time <= frames[0]['time']:
            return value(frames[0])
        for left, right in zip(frames, frames[1:]):
            if left['time'] <= time <= right['time']:
                factor = (time-left['time']) / (right['time']-left['time'])
                return [a+(b-a)*factor for a, b in zip(value(left), value(right))]
        return value(frames[-1])

    points = []
    for cube in model['elements']:
        for corner in itertools.product(*zip(cube['from'], cube['to'])):
            point = transform(corner, cube['origin'], cube.get('rotation', [0, 0, 0]))
            bone = owners[cube['uuid']]
            while bone:
                group = groups[bone]
                angles = [a+b for a, b in zip(group.get('rotation', [0, 0, 0]), track(bone, 'rotation'))]
                point = transform(point, group['origin'], angles, track(bone, 'position'))
                bone = parents[bone]
            points.append(point)
    return [[min(point[axis] for point in points) for axis in range(3)],
            [max(point[axis] for point in points) for axis in range(3)]]


states = {'rest': bounds()}
for animation in model['animations']:
    sampled = [bounds(animation, animation['length'] * index / 240) for index in range(241)]
    states[animation['name']] = [[min(pair[0][axis] for pair in sampled) for axis in range(3)],
                                 [max(pair[1][axis] for pair in sampled) for axis in range(3)]]
report = {}
for name, (low, high) in states.items():
    assert all(a >= bound for a, bound in zip(low, [-4, 0, -4])), (name, low, high)
    assert all(a <= bound for a, bound in zip(high, [4, 16, 4])), (name, low, high)
    report[name] = {'min': [round(a, 4) for a in low], 'max': [round(a, 4) for a in high],
                    'size': [round(b-a, 4) for a, b in zip(low, high)]}
(HERE / 'bounds-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, indent=2))
