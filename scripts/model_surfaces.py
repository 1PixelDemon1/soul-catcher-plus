# SPDX-License-Identifier: MIT
"""Bake exposed faces per rigid animation island. No runtime boolean operations.

Source cubes/pivots remain editable. Clip in rest-world coordinates, then return
vertices to the owning bone. Never clip across independently animated bones.
"""
import math
import re
import copy
import json
import uuid

EPS = 1e-7
FACES = ('north', 'south', 'west', 'east', 'down', 'up')


def add(a, b): return tuple(x+y for x, y in zip(a, b))
def sub(a, b): return tuple(x-y for x, y in zip(a, b))
def mul(a, s): return tuple(x*s for x in a)
def dot(a, b): return sum(x*y for x, y in zip(a, b))
def cross(a, b): return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])


def rotation(angles):
    columns = []
    for column in ((1,0,0), (0,1,0), (0,0,1)):
        p = list(column)
        for axis, angle in enumerate(angles):
            a, b = ((1,2), (2,0), (0,1))[axis]
            co, si = math.cos(math.radians(angle)), math.sin(math.radians(angle))
            p[a], p[b] = co*p[a]-si*p[b], si*p[a]+co*p[b]
        columns.append(p)
    return columns


def vector(matrix, p): return tuple(sum(matrix[j][i]*p[j] for j in range(3)) for i in range(3))
def point(frame, p): return add(vector(frame[0], p), frame[1])
def inverse_vector(matrix, p): return tuple(dot(column, p) for column in matrix)
def inverse(frame, p): return inverse_vector(frame[0], sub(p, frame[1]))
IDENTITY = (rotation((0,0,0)), (0,0,0))


def compose(parent, angles, translation):
    return ([vector(parent[0], c) for c in rotation(angles)], point(parent, translation))


def clip(vertices, normal, distance, inside):
    """Sutherland-Hodgman with XYZ/UV interpolation; input polygon is convex."""
    result = []
    previous = vertices[-1]
    previous_d = dot(previous[:3], normal)-distance
    for current in vertices:
        current_d = dot(current[:3], normal)-distance
        keep = current_d <= EPS if inside else current_d >= -EPS
        previous_keep = previous_d <= EPS if inside else previous_d >= -EPS
        if keep != previous_keep:
            t = previous_d/(previous_d-current_d)
            result.append(tuple(a+(b-a)*t for a,b in zip(previous, current)))
        if keep: result.append(current)
        previous, previous_d = current, current_d
    # Plane intersections can coincide with existing corners.
    clean = []
    for v in result:
        if not clean or dot(sub(v[:3], clean[-1][:3]), sub(v[:3], clean[-1][:3])) > EPS*EPS:
            clean.append(v)
    if len(clean)>1 and dot(sub(clean[0][:3], clean[-1][:3]), sub(clean[0][:3], clean[-1][:3])) < EPS*EPS:
        clean.pop()
    return clean if len(clean)>=3 else []


def subtract(vertices, planes):
    # Reject a disjoint box before splitting; otherwise its extended planes
    # would needlessly tessellate surfaces far outside the actual cuboid.
    if any(min(dot(v[:3],n)-d for v in vertices) >= -EPS for n,d in planes):
        return [vertices]
    outside = []
    remaining = vertices
    for normal, distance in planes:
        ds = [dot(v[:3], normal)-distance for v in remaining]
        if min(ds) >= -EPS: return outside+[remaining]
        if max(ds) <= EPS: continue
        piece = clip(remaining, normal, distance, False)
        if piece: outside.append(piece)
        remaining = clip(remaining, normal, distance, True)
        if not remaining: break
    return outside


def cuboid_faces(cube, pivot):
    x0,y0,z0 = sub(cube['from'], pivot)
    x1,y1,z1 = sub(cube['to'], pivot)
    corners = [((x0,y0,z0),(x0,y1,z0),(x1,y1,z0),(x1,y0,z0)),
               ((x1,y0,z1),(x1,y1,z1),(x0,y1,z1),(x0,y0,z1)),
               ((x0,y0,z1),(x0,y1,z1),(x0,y1,z0),(x0,y0,z0)),
               ((x1,y0,z0),(x1,y1,z0),(x1,y1,z1),(x1,y0,z1)),
               ((x0,y0,z1),(x0,y0,z0),(x1,y0,z0),(x1,y0,z1)),
               ((x0,y1,z0),(x0,y1,z1),(x1,y1,z1),(x1,y1,z0))]
    normals = ((0,0,-1),(0,0,1),(-1,0,0),(1,0,0),(0,-1,0),(0,1,0))
    for face_name, points, normal in zip(FACES, corners, normals):
        face = cube['faces'][face_name]
        if face.get('texture') is None: continue
        if face.get('rotation',0): raise ValueError('Rotated UVs require an explicit rule')
        u0,v0,u1,v1 = face['uv']
        uv = ((u1,v1),(u1,v0),(u0,v0),(u0,v1)) if normal[1]==0 else ((u0,v0),(u0,v1),(u1,v1),(u1,v0))
        yield points, uv, normal, face.get('texture')


def mesh_faces(mesh):
    for face in mesh['faces'].values():
        keys = face['vertices']
        if len(keys) not in (3, 4):
            raise ValueError(f'Expected triangle or quad in {mesh["name"]}')
        points = [mesh['vertices'][key] for key in keys]
        uv = [face['uv'][key] for key in keys]
        normal = cross(sub(points[1], points[0]), sub(points[2], points[0]))
        length = math.sqrt(dot(normal, normal))
        if length <= EPS:
            raise ValueError(f'Degenerate face in {mesh["name"]}')
        normal = mul(normal, 1/length)
        # A twisted loft side needs two planar triangles for clipping and normals.
        if len(points) == 4 and abs(dot(sub(points[3], points[0]), normal)) > EPS:
            yield points[:3], uv[:3], normal, face.get('texture')
            other = cross(sub(points[2], points[0]), sub(points[3], points[0]))
            yield [points[0], *points[2:]], [uv[0], *uv[2:]], mul(other, 1/math.sqrt(dot(other, other))), face.get('texture')
        else:
            yield points, uv, normal, face.get('texture')


def bake(model, animated, material):
    groups = {g['uuid']:g for g in model['groups']}
    elements = {element['uuid']:element for element in model['elements']}
    textures = {t['uuid']:t for t in model['textures']}
    textures.update(enumerate(model['textures']))
    ordered, records = [], []

    def walk(node, parent=None, frame=IDENTITY, parent_origin=(0,0,0), island=None):
        g = groups[node['uuid']]
        name, origin = g['name'], g['origin']
        own = compose(frame, g.get('rotation', (0,0,0)), sub(origin, parent_origin))
        if island is None or name in animated: island = name
        ordered.append((g, parent, parent_origin))
        for child in node['children']:
            if isinstance(child, dict):
                walk(child, name, own, origin, island)
                continue
            element = elements[child]
            pivot = element.get('origin', origin)
            frame = compose(own, element.get('rotation', (0,0,0)), sub(pivot, origin))
            mesh = element.get('type') == 'mesh'
            faces = mesh_faces(element) if mesh else cuboid_faces(element, pivot)
            world_faces = []
            planes = []
            for points, uv, normal, texture_key in faces:
                points = [point(frame, p) for p in points]
                normal = vector(frame[0], normal)
                if mesh: planes.append((normal, dot(normal, points[0])))
                if texture_key is not None:
                    texture = textures[texture_key]
                    vertices = [p+(u/texture['uv_width'],v/texture['uv_height']) for p,(u,v) in zip(points,uv)]
                    world_faces.append((vertices, normal, texture))
            if mesh:
                # A concave loft still contributes surfaces but cannot clip its neighbors
                # with the intersection of all its face half-spaces.
                points = [point(frame, p) for p in element['vertices'].values()]
                if any(dot(n,p) > d+EPS for n,d in planes for p in points):
                    planes = []
            else:
                lo, hi = sub(element['from'], pivot), sub(element['to'], pivot)
                for axis, n in enumerate(frame[0]):
                    distance = dot(n, frame[1])
                    planes.extend(((n, distance+hi[axis]), (mul(n,-1), -distance-lo[axis])))
            records.append((world_faces, own, planes, island, name, mesh))
    for root in model['outliner']: walk(root)
    result = {g['name']:[] for g,_,_ in ordered}
    original_area = exposed_area = 0
    for index, (faces, own, _, island, name, _) in enumerate(records):
        for vertices, world_normal, texture in faces:
            pieces = [vertices]
            original_area += area(vertices)
            for other_index, (_, _, planes, other_island, _, _) in enumerate(records):
                if index == other_index or island != other_island or not planes: continue
                # Test infinitesimally outside the face. Opposite touching faces disappear;
                # equal outward faces prefer the later authored cube/material exactly once.
                shifted = []
                for n,d in planes:
                    alignment = dot(n,world_normal)
                    bias = -EPS*4*alignment
                    if alignment > 1-EPS and abs(dot(vertices[0][:3], n)-d)<EPS:
                        bias = EPS*4 if other_index>index else -EPS*4
                    shifted.append((n,d+bias))
                pieces = [part for poly in pieces for part in subtract(poly,shifted)]
                if not pieces: break
            for poly in pieces:
                if area(poly)<1e-8: continue
                exposed_area += area(poly)
                local = [inverse(own,v[:3])+v[3:] for v in poly]
                mat, local = material(texture,local)
                result[name].append((mat, local, inverse_vector(own[0],world_normal), texture['uuid']))
    meshes = sum(record[-1] for record in records)
    report = {'source_cubes':len(records)-meshes,'source_area':round(original_area,3),'exposed_area':round(exposed_area,3)}
    if meshes: report['source_meshes'] = meshes
    return ordered, result, report


def area(poly):
    return sum(math.sqrt(dot(n,n))/2 for n in [cross(sub(poly[i][:3],poly[0][:3]),sub(poly[i+1][:3],poly[0][:3])) for i in range(1,len(poly)-1)])


def identifier(name): return 'part_'+re.sub('[^a-zA-Z0-9_]', '_', name)
def number(v): return f'{v:.7f}'.rstrip('0').rstrip('.')+'f'


def java_geometry(class_name, part_type, material_import, ordered, surfaces):
    lines = ['// SPDX-License-Identifier: MIT', '// Generated exposed surfaces; edit the Blockbench cuboids, then run its exporter.',
             'package dev.soulcatcherplus.client.model;', '', 'import java.util.LinkedHashMap;', 'import java.util.Map;',
             'import '+material_import+';', '', 'final class '+class_name+' {', '    private '+class_name+'() {}',
             '    static Map<String, '+part_type+'> create() {', '        Map<String, '+part_type+'> parts = new LinkedHashMap<>();']
    helpers = []
    for g,parent,parent_origin in ordered:
        name = identifier(g['name'])
        values = sub(g['origin'],parent_origin)+tuple(g.get('rotation',(0,0,0)))
        lines.append(f'        {part_type} {name} = new {part_type}('+', '.join(map(number,values))+');')
        lines.append(f'        parts.put("{g["name"]}", {name});')
        if parent: lines.append(f'        {identifier(parent)}.children.add({name});')
        calls = []
        for mat,poly,normal,_ in surfaces[g['name']]:
            quads = [poly] if len(poly)==4 else [[poly[0],poly[i],poly[i+1],poly[min(i+2,len(poly)-1)]]
                                               for i in range(1,len(poly)-1,2)]
            for quad in quads:
                calls.append(f'        part.surface(Material.{mat}, new float[] {{'+', '.join(number(v) for p in quad for v in p)+'}, '+', '.join(map(number,normal))+');')
        for start in range(0,len(calls),48):
            method = name+'_'+str(start//48)
            lines.append(f'        {method}({name});')
            helpers += [f'    private static void {method}({part_type} part) {{']+calls[start:start+48]+['    }']
    return '\n'.join(lines+['        return parts;', '    }']+helpers+['}', ''])


def write_preview(source, model, ordered, surfaces, report):
    """Companion Blockbench project showing the exact baked shell, with the same bones/clips."""
    preview = copy.deepcopy(model)
    textures = {t['uuid']:t for t in model['textures']}
    meshes = {}
    preview['elements'] = []
    for g,_,_ in ordered:
        mesh = {'name':g['name']+'_shell', 'type':'mesh', 'uuid':str(uuid.uuid5(uuid.UUID(g['uuid']), 'shell')),
                'origin':g['origin'], 'rotation':[0,0,0], 'vertices':{}, 'faces':{}, 'visibility':True, 'export':True}
        for i,(_,poly,_,texture) in enumerate(surfaces[g['name']]):
            texture_info = textures[texture]
            keys, uv = [], {}
            for j,v in enumerate(poly):
                key = f'v{i}_{j}'
                keys.append(key)
                # Mesh coordinates are local to their origin (unlike cube from/to).
                mesh['vertices'][key] = list(v[:3])
                uv[key] = [v[3]*texture_info['uv_width'],v[4]*texture_info['uv_height']]
            mesh['faces'][f'f{i}'] = {'vertices':list(reversed(keys)), 'uv':uv, 'texture':texture}
        if mesh['faces']:
            preview['elements'].append(mesh)
            meshes[g['uuid']] = mesh['uuid']
    def clean(node):
        node['children'] = [clean(c) for c in node['children'] if isinstance(c,dict)]
        if node['uuid'] in meshes: node['children'].insert(0,meshes[node['uuid']])
        return node
    preview['outliner'] = [clean(n) for n in preview['outliner']]
    preview['name'] = source.stem+' exposed surfaces'
    source.with_name(source.stem+'_surface.bbmodel').write_text(json.dumps(preview,indent=2)+'\n',encoding='utf-8')
    source.with_name('surface-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
