# SPDX-License-Identifier: MIT
"""Rebuild Soul Shaman from the supplied three-view concept, independently of v1-v3."""
import base64
import importlib.util
import itertools
import json
import math
from pathlib import Path
import struct
import uuid
import zlib

HERE = Path(__file__).resolve().parent
SKILL = Path.home() / '.codex/skills/blockbench-model-bridge'
NS = uuid.UUID('c24a17b5-5c25-4c47-876d-d4362f3303ea')
MATS = [('cloth', None), ('bone', 'D6CFB6'), ('bone_light', 'E5DEC9'),
        ('bone_shade', 'BEB69F'), ('wood', '795A3B'), ('wood_light', '8C6945'),
        ('wood_shade', '59402C'), ('cord', '362A22'), ('cavity', '0C1619'),
        ('cyan_trim', '278E9E'), ('soul_blue', '28B9C9'),
        ('soul_light', '78E5E5'), ('soul_middle', '42CFD8')]
EMISSIVE = {'soul_blue', 'soul_light', 'soul_middle'}
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
HOOD_FRONT_Z = -3.8
HOOD_LOWER_FRONT_Z = -2.7
cubes, members = [], {}


def uid(name):
    return str(uuid.uuid5(NS, name))


def dump(path, obj):
    path.write_text(json.dumps(obj, indent=2) + '\n', encoding='utf-8')


def hood_front_z(y):
    """Keep the lower rim at its former depth; only the crown projects forward."""
    weight=max(0.0,min(1.0,(y-13.35)/(19.8-13.35)))
    return HOOD_LOWER_FRONT_Z+(HOOD_FRONT_Z-HOOD_LOWER_FRONT_Z)*weight


def swatch(color):
    rgb = bytes.fromhex(color)
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 8, 8, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress((b'\0' + rgb * 8) * 8)) + chunk(b'IEND', b'')


textures = []
for name, color in MATS:
    path = HERE / 'textures' / (name + '.png')
    if color:
        path.write_bytes(swatch(color))
    size = 16 if name == 'cloth' else 8
    textures.append({'name': name, 'path': 'textures/' + path.name,
                     'uuid': uid('texture/' + name), 'width': size, 'height': size,
                     'render_mode': 'emissive' if name in EMISSIVE else 'default'})


def cube(name, lo, hi, group, mat='cloth', rotation=None, origin=None):
    width, height, depth = [b - a for a, b in zip(lo, hi)]
    faces = {}
    for face in FACES:
        material = mat
        if mat == 'wood':
            material = {'east': 'wood_shade', 'west': 'wood_light', 'up': 'wood_light'}.get(face, mat)
        if mat == 'bone':
            material = {'east': 'bone_shade', 'up': 'bone_light'}.get(face, mat)
        if mat == 'cloth':
            # Equal texel density on each plane, fixed deterministic offsets.
            w, h = (depth, height) if face in ('east', 'west') else (width, depth) if face in ('up', 'down') else (width, height)
            ox, oy = (2, 3) if face in ('north', 'up', 'east') else (6, 5)
            uv = [ox, oy, ox + w * 1.25, oy + h * 1.25]
        else:
            uv = [0, 0, 8, 8]
        faces[face] = {'texture': next(i for i, m in enumerate(MATS) if m[0] == material), 'uv': uv}
    cubes.append({'name': name, 'uuid': uid('cube/' + name), 'from': lo, 'to': hi,
                  'origin': origin or [(a + b) / 2 for a, b in zip(lo, hi)],
                  'rotation': rotation or [0, 0, 0], 'faces': faces})
    members.setdefault(group, []).append(name)


def beam(name, start, end, width, z_front, z_back, group, mat='cloth'):
    """A cuboid aligned to an XY line; no tessellated or rounded surfaces."""
    x, y = [(a + b) / 2 for a, b in zip(start, end)]
    length = math.dist(start, end)
    angle = -math.degrees(math.atan2(end[0] - start[0], end[1] - start[1]))
    cube(name, [x - width / 2, y - length / 2, z_front],
         [x + width / 2, y + length / 2, z_back], group, mat,
         rotation=[0, 0, angle], origin=[x, y, (z_front + z_back) / 2])


# Proportions measured against the front view: head ~7 units, torso/skirt ~10.
cube('torso', [-2.35, 7.9, -1.35], [2.35, 13.9, 1.35], 'body')
# A solid cloth lining closes the split robe from below, behind its hanging panels.
cube('robe_inner_lining', [-2.45,3.2,-1.1], [2.45,8.05,1.1], 'body')
cube('belt', [-2.5, 8.0, -1.5], [2.5, 8.65, 1.5], 'body', 'wood')
cube('belt_buckle', [-0.45, 8.03, -1.57], [0.25, 8.62, -1.5], 'body', 'wood_shade')

# Three broad front and back skirt panels; sides flare with the same simple tilt.
for back in (False, True):
    za, zb = (1.1, 1.65) if back else (-1.65, -1.1)
    front = 1.69 if back else -1.69
    for side, sign in [('left', -1), ('right', 1)]:
        lo_x, hi_x = (-3.1, -1.05) if sign < 0 else (1.05, 3.1)
        pivot = [sign * 1.5, 8.05, 0]
        rot = [0, 0, sign * 12]
        suffix = ('back_' if back else 'front_') + side
        cube('skirt_' + suffix, [lo_x, 3.0, za], [hi_x, 8.05, zb], 'body', rotation=rot, origin=pivot)
        # Pixel L motif from the reference, carried with its panel's rotation.
        x = sign * 2.25
        cube('hem_rune_stem_' + suffix, [x - .22, 3.1, front - .025], [x + .22, 4.9, front + .025],
             'body', 'cyan_trim', rotation=rot, origin=pivot)
        a, b = sorted([x, x - sign * .85])
        cube('hem_rune_hook_' + suffix, [a, 4.0, front - .025], [b, 4.45, front + .025],
             'body', 'cyan_trim', rotation=rot, origin=pivot)
    cube('skirt_' + ('back' if back else 'front') + '_center', [-.92, 2.65, za], [.92, 8.05, zb], 'body')
for sign in (-1, 1):
    cube('skirt_side_' + str(sign), [sign * 2.6 - .28, 3.2, -1.2], [sign * 2.6 + .28, 8.0, 1.2],
         'body', rotation=[0, 0, sign * 12], origin=[sign * 1.5, 8.0, 0])

# Long loose sleeves, posed independently from the layered shoulder cape.
for side, sign in [('left', -1), ('right', 1)]:
    lo_x, hi_x = (-4.4, -2.4) if sign < 0 else (2.4, 4.4)
    pivot = [sign * 2.4, 13.4, 0]
    cube('sleeve_' + side, [lo_x, 8.0, -1.3], [hi_x, 13.4, 1.3], 'arm_' + side,
         rotation=[0, 0, sign * 19], origin=pivot)
cube('staff_hand', [5.35, 8.55, -2.05], [6.65, 9.85, -.85], 'arm_right', 'bone')

# Broad overlapping cape layers form sloped shoulders, rather than a rectangular bar.
for layer, drop, width, depth in [('upper', 0, 1.1, 1.75), ('lower', .65, .85, 1.9)]:
    for side, sign in [('left', -1), ('right', 1)]:
        beam('collar_' + layer + '_' + side, [sign * .75, 13.55 - drop], [sign * 4.2, 12.1 - drop],
             width, -depth, depth, 'body')
    cube('collar_' + layer + '_back', [-3.0, 12.45 - drop, 1.25], [3.0, 13.25 - drop, depth], 'body')
for sign in (-1, 1):
    beam('collar_rune_long_' + str(sign), [sign * 2.65, 12.05], [sign * 3.1, 13.05], .4,
         -1.95, -1.91, 'body', 'cyan_trim')
    beam('collar_rune_hook_' + str(sign), [sign * 1.9, 12.35], [sign * 2.8, 11.98], .4,
         -1.95, -1.91, 'body', 'cyan_trim')

# The volumetric hood shell is constructed below; front mask and rim motifs stay here.
cube('hood_inner_shadow', [-2.8, 13.5, -2.35], [2.8, 20.0, -2.1], 'head', 'cavity')
# Closed cloth under the chin and a short neck transition hide the interior
# from low viewing angles without moving the mask or narrowing the face opening.
cube('hood_under_chin', [-1.9,13.12,HOOD_LOWER_FRONT_Z+.02], [1.9,13.55,1.15], 'head')
cube('hood_neck_lining', [-1.8,12.8,-1.3], [1.8,13.9,1.3], 'head')
# Front and side motifs are projected onto the hood mesh below.
cube('hood_top_rune', [-1.65,20.94,-2.75], [1.65,20.98,-2.25], 'head', 'cyan_trim')

# Tapered ivory mask and original dark-upper / cyan-lower rectangular eyes.
cube('mask_upper', [-2.6, 15.0, -2.94], [2.6, 19.55, -2.56], 'head', 'bone')
cube('mask_chin', [-1.75, 13.65, -2.94], [1.75, 15.0, -2.56], 'head', 'bone')
for side, sign in [('left', -1), ('right', 1)]:
    x = sign * 1.55
    cube('eye_socket_' + side, [x - .72, 16.0, -3.02], [x + .72, 17.1, -2.94], 'head', 'cavity')
    cube('eye_' + side, [x - .62, 16.05, -3.06], [x + .62, 16.7, -3.02], 'head', 'soul_light')
for name, lo, hi in [
    ('brow', [.45, 18.3, -3.0], [1.05, 19.55, -2.94]),
    ('brow_step', [-.1, 17.75, -3.0], [.65, 18.3, -2.94]),
    ('brow_tip', [-.2, 17.35, -3.0], [.35, 17.8, -2.94]),
    ('chin_top', [.1, 14.8, -3.0], [.65, 15.5, -2.94]),
    ('chin_base', [-.45, 13.65, -3.0], [.15, 14.95, -2.94])]:
    cube('mask_mark_' + name, lo, hi, 'head', 'cord')
# Few broad ivory patches give the mask the reference's quiet material changes.
for i, (x, y, w, h, mat) in enumerate([(-2.5,18.7,.8,.7,'bone_light'),(-1.7,18.0,1.1,.7,'bone_light'),
                                       (1.3,18.2,1.1,.6,'bone_shade'),(-2.4,15.1,.8,.6,'bone_shade')]):
    cube('mask_ivory_patch_' + str(i), [x,y,-2.95], [x+w,y+h,-2.94], 'head', mat)

# Leather cord and a teal framed, raised square stone, matching the concept.
beam('amulet_strap_left', [-1.65, 13.2], [-.4, 11.65], .32, -1.95, -1.72, 'body', 'wood')
beam('amulet_strap_right', [1.65, 13.2], [.4, 11.65], .32, -1.95, -1.72, 'body', 'wood')
cube('amulet_frame', [-.78, 10.0, -2.1], [.78, 11.85, -1.65], 'body', 'cyan_trim')
cube('amulet_stone', [-.5, 10.2, -2.26], [.5, 11.65, -2.1], 'body', 'soul_blue')
cube('amulet_glint', [-.22, 10.55, -2.29], [.3, 11.2, -2.26], 'body', 'soul_light')

# Asymmetric curled ghost flame, exposed below the split robe.
for name, lo, hi, mat in [
    ('crown', [-1.15,1.65,-.65], [1.15,3.05,.65], 'soul_blue'),
    ('left', [-1.15,3.05,-.5], [-.5,3.75,.5], 'soul_blue'),
    ('bend', [-.3,.95,-.4], [.85,1.65,.4], 'soul_blue'),
    ('tip', [-.5,.15,-.28], [.1,.95,.28], 'soul_blue'),
    ('core', [-.5,1.8,-.72], [.45,2.8,.72], 'soul_middle'),
    ('hot', [-.1,1.8,-.76], [.4,2.45,.76], 'soul_light'),
    ('tail_core', [.0,1.2,-.45], [.45,1.8,.45], 'soul_light')]:
    cube('wisp_' + name, lo, hi, 'wisp', mat)

# Long plain wood shaft and the reference's angular crook, not a straight fork pole.
beam('staff_shaft', [6.3,.5], [6.05,15.5], .85, -1.9, -1.05, 'staff', 'wood')
beam('staff_crook_lower', [6.05,15.5], [7.65,16.2], .9, -1.9, -1.05, 'staff', 'wood')
beam('staff_crook_middle', [7.65,16.2], [7.85,17.5], .9, -1.9, -1.05, 'staff', 'wood')
beam('staff_crook_upper', [7.85,17.5], [6.15,18.25], .9, -1.9, -1.05, 'staff', 'wood')
beam('staff_neck', [6.15,18.25], [6.55,19.95], .9, -1.9, -1.05, 'staff', 'wood')
cube('staff_binding', [6.1,19.45,-2.03], [7.0,19.95,-.92], 'staff', 'cord')
cube('fork_base', [4.85,19.8,-2.0], [8.35,20.4,-.95], 'staff', 'bone')
beam('fork_left', [4.95,20.1], [4.65,22.6], .65, -2.0, -.95, 'staff', 'bone')
beam('fork_right', [8.25,20.1], [8.55,22.6], .65, -2.0, -.95, 'staff', 'bone')
cube('bone_cord', [7.5,14.6,-1.51], [7.67,16.75,-1.34], 'charm', 'cord')
cube('bone_charm', [7.25,13.75,-1.75], [7.9,15.45,-1.1], 'charm', 'bone')
cube('bone_charm_top', [7.05,15.05,-1.8], [8.1,15.5,-1.05], 'charm', 'bone')
cube('bone_charm_bottom', [7.05,13.65,-1.8], [8.1,14.1,-1.05], 'charm', 'bone')
for name, lo, hi, mat in [
    ('base', [5.8,20.6,-1.95], [7.3,22.0,-1.0], 'soul_blue'),
    ('left', [5.8,22.0,-1.8], [6.35,22.8,-1.15], 'soul_blue'),
    ('rise', [6.7,22.0,-1.8], [7.3,23.65,-1.15], 'soul_blue'),
    ('turn', [6.0,23.15,-1.72], [6.8,23.65,-1.23], 'soul_blue'),
    ('tip', [5.95,23.65,-1.7], [6.5,24.5,-1.25], 'soul_blue'),
    ('core', [6.15,20.8,-2.02], [6.95,22.0,-.93], 'soul_light'),
    ('core_up', [6.45,22.0,-1.91], [6.95,22.55,-1.0], 'soul_middle')]:
    cube('staff_flame_' + name, lo, hi, 'staff_flame', mat)

pivots = {'root':[0,0,0], 'body':[0,10,0], 'head':[0,16.5,.5],
          'arm_left':[-2.4,13.4,0], 'arm_right':[2.4,13.4,0], 'staff':[6.0,9.2,-1.475],
          'charm':[7.585,16.75,-1.425], 'wisp':[0,2.2,0], 'staff_flame':[6.5,21,-1.475]}
hierarchy = {'root':['body'], 'body':['head','arm_left','arm_right','wisp'],
             'arm_right':['staff'], 'staff':['charm','staff_flame']}
groups = [{'name':name, 'uuid':uid('bone/'+name), 'origin':pivot, 'children':members.get(name,[])}
          for name,pivot in pivots.items()]
# Four coarse cross sections give the head a real crown, occiput and nape.
# The front outline stays fixed. The rear contracts in both width and height,
# rather than extruding that outline into another flat silhouette plate.
profile=[[-2.6,20.85],[2.6,20.85],[3.35,19.8],[4.35,14.7],
         [2.0,13.35],[-2.0,13.35],[-4.35,14.7],[-3.35,19.8]]
sections=[(HOOD_FRONT_Z,1.0),(1.1,1.02),(2.8,.92),(3.7,.70)]
mesh_vertices={}
for ring,(z,scale) in enumerate(sections+[(HOOD_FRONT_Z+.01,.82),(2.45,.61)]):
    for i,(x,y) in enumerate(profile):
        vertex_z=hood_front_z(y)+(0.01 if ring==4 else 0) if ring in (0,4) else z
        mesh_vertices[f'r{ring}v{i}']=[x*scale,17.1+(y-17.1)*scale,vertex_z]
mesh_vertices['rear_center']=[0,17.1,3.98]
mesh_vertices['inner_center']=[0,17.1,2.45]
mesh_faces={}


def mesh_face(vertices, faces, keys, material='cloth', inside=False):
    a,b,c=[vertices[key] for key in keys[:3]]
    ab=[b[k]-a[k] for k in range(3)]
    ac=[c[k]-a[k] for k in range(3)]
    normal=[ab[1]*ac[2]-ab[2]*ac[1],ab[2]*ac[0]-ab[0]*ac[2],ab[0]*ac[1]-ab[1]*ac[0]]
    assert sum(n*n for n in normal)>1e-10
    center=[sum(vertices[v][k] for v in keys)/len(keys) for k in range(3)]
    outward=sum(normal[k]*(center[k]-[0,17.1,.5][k]) for k in range(3))>0
    if outward==inside:
        keys=list(reversed(keys))
    axis=max(range(3),key=lambda k:abs(normal[k]))
    def project(v):
        x,y,z=vertices[v]
        if material!='cloth':
            return [0,0]
        if axis==0:
            return [(z+3)*1.1+1,(21-y)*1.1+2]
        if axis==1:
            return [(x+4.5)*1.1+1,(z+3)*1.1+1]
        return [(x+4.5)*1.1+1,(21-y)*1.1+2]
    faces[str(len(faces))]={'vertices':keys,'uv':{v:project(v) for v in keys},
                           'texture':uid('texture/'+material)}


def connect_rings(first,second,inside=False):
    for i in range(8):
        j=(i+1)%8
        mesh_face(mesh_vertices,mesh_faces,[f'r{first}v{i}',f'r{first}v{j}',
                                          f'r{second}v{j}',f'r{second}v{i}'],
                  'cavity' if inside else 'cloth',inside)


for ring in range(3):
    connect_rings(ring,ring+1)
connect_rings(0,4)
connect_rings(4,5,inside=True)
for i in range(8):
    j=(i+1)%8
    mesh_face(mesh_vertices,mesh_faces,[f'r3v{i}',f'r3v{j}','rear_center'])
    mesh_face(mesh_vertices,mesh_faces,[f'r5v{i}',f'r5v{j}','inner_center'],'cavity',inside=True)
edge_counts={}
for face in mesh_faces.values():
    keys=face['vertices']
    for a,b in zip(keys,keys[1:]+keys[:1]):
        edge=tuple(sorted((a,b)))
        edge_counts[edge]=edge_counts.get(edge,0)+1
assert all(count==2 for count in edge_counts.values())
assert all(0<=n<=16 for face in mesh_faces.values() for uv in face['uv'].values() for n in uv)


def native_mesh(name,vertices,faces):
    return {'name':name,'type':'mesh','uuid':uid('mesh/'+name),'origin':[0,0,0],
            'rotation':[0,0,0],'vertices':vertices,'faces':faces,
            'visibility':True,'export':True,'color':0}


hood_mesh=native_mesh('hood_volume',mesh_vertices,mesh_faces)
# Side motifs are sampled from the actual hood planes. They wrap around the
# belly and rear bevel without floating strips or a second planar rear drawing.
rune_vertices,rune_faces={},{}


def shell_point(sign,t,u):
    ring=min(int(t),2)
    fraction=t-ring
    i,j=(2,3) if sign>0 else (7,6)
    def edge_point(r):
        a,b=mesh_vertices[f'r{r}v{i}'],mesh_vertices[f'r{r}v{j}']
        return [a[k]+(b[k]-a[k])*u for k in range(3)]
    a,b=edge_point(ring),edge_point(ring+1)
    point=[a[k]+(b[k]-a[k])*fraction for k in range(3)]
    point[0]+=sign*.025
    return point


for sign in (-1,1):
    rectangles=[(.08,.32,.55,.63),(.32,.44,.38,.63),(.44,1.48,.38,.46),
                (1.48,1.60,.22,.46),(1.60,2.30,.22,.30)]
    for ta,tb,ua,ub in rectangles:
        cuts=[ta]+[float(r) for r in (1,2) if ta<r<tb]+[tb]
        for start,end in zip(cuts,cuts[1:]):
            keys=[]
            for t,u in [(start,ua),(start,ub),(end,ub),(end,ua)]:
                key='v'+str(len(rune_vertices))
                rune_vertices[key]=shell_point(sign,t,u)
                keys.append(key)
            mesh_face(rune_vertices,rune_faces,keys,'cyan_trim')
    # Flat front marks follow the inclined rim instead of hovering at the crown depth.
    for start,end in [([sign*4.1,15.7],[sign*3.65,18.0]),
                      ([sign*4.1,15.7],[sign*3.7,15.9]),
                      ([sign*3.65,18.0],[sign*3.3,18.2])]:
        dx,dy=end[0]-start[0],end[1]-start[1]
        length=math.hypot(dx,dy)
        px,py=-dy/length*.2,dx/length*.2
        keys=[]
        for x,y in [(start[0]+px,start[1]+py),(start[0]-px,start[1]-py),
                    (end[0]-px,end[1]-py),(end[0]+px,end[1]+py)]:
            key='v'+str(len(rune_vertices))
            rune_vertices[key]=[x,y,hood_front_z(y)-.045]
            keys.append(key)
        mesh_face(rune_vertices,rune_faces,keys,'cyan_trim')
rune_mesh=native_mesh('hood_wrapped_runes',rune_vertices,rune_faces)
native_meshes=[hood_mesh,rune_mesh]
plan = {'name':'Soul Shaman Reference Rebuild', 'model_identifier':'soulcatcherplus:soul_shaman',
        'model_format':'free', 'format_version':'5.0', 'texture_width':16, 'texture_height':16,
        'target_size':{'blockbench_units':[16,25,8], 'minecraft_blocks':[1,1.5625,.5],
                       'staff_height_units':24, 'fit_policy':'Previously approved 1.5-block staff, including flame. Other proportions measured from supplied front/back concept; unseen surfaces inferred.'},
        'textures':textures, 'cubes':cubes, 'groups':groups, 'hierarchy':hierarchy,
        'native_meshes':native_meshes}
dump(HERE/'plan.json',plan)
spec=importlib.util.spec_from_file_location('bridge',SKILL/'scripts/plan_to_bbmodel.py')
bridge=importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)
model,warnings=bridge.convert(plan)
model['elements'].extend(native_meshes)
members['head'].extend(mesh['name'] for mesh in native_meshes)
by_cube={c['name']:c['uuid'] for c in model['elements']}
def node(name):
    return {'uuid':uid('bone/'+name), 'isOpen':name=='root',
            'children':[by_cube[n] for n in members.get(name,[])]+[node(child) for child in hierarchy.get(name,[])]}
model['outliner']=[node('root')]
for group in model['groups']:
    group.update({'rotation':[0,0,0], 'export':True, 'visibility':True})
for texture in model['textures']:
    texture['source']='data:image/png;base64,'+base64.b64encode((HERE/texture['relative_path']).read_bytes()).decode()
dump(HERE/'soul_shaman.bbmodel',model)

def rotated(point,c):
    p=[v-o for v,o in zip(point,c['origin'])]
    for axis,angle in enumerate(c['rotation']):
        a,b=((1,2),(2,0),(0,1))[axis]
        co,si=math.cos(math.radians(angle)),math.sin(math.radians(angle))
        p[a],p[b]=co*p[a]-si*p[b],si*p[a]+co*p[b]
    return [v+o for v,o in zip(p,c['origin'])]
def bounds(items):
    points=[rotated(p,c) for c in items for p in itertools.product(*zip(c['from'],c['to']))]
    return {'min':[min(p[a] for p in points) for a in range(3)],
            'max':[max(p[a] for p in points) for a in range(3)]}
all_bounds=bounds(cubes)
for point in mesh_vertices.values():
    for axis in range(3):
        all_bounds['min'][axis]=min(all_bounds['min'][axis],point[axis])
        all_bounds['max'][axis]=max(all_bounds['max'][axis],point[axis])
staff_bounds=bounds([c for c in cubes if c['name'] in members['staff']+members['staff_flame']+members['charm']])
assert staff_bounds['max'][1]-staff_bounds['min'][1] <=24.01
assert all_bounds['max'][0]-all_bounds['min'][0]<=16
assert all_bounds['max'][1]<=25 and all_bounds['min'][1]>=0
assert all_bounds['max'][2]-all_bounds['min'][2]<=8
dump(HERE/'bounds-report.json', {'cuboids':len(cubes), 'quads':len(cubes)*6, 'triangles':len(cubes)*12,
                               'bounds':all_bounds, 'staff_bounds':staff_bounds,
                               'staff_height_units':staff_bounds['max'][1]-staff_bounds['min'][1],
                               'hood_mesh_quads':sum(len(f['vertices'])==4 for f in mesh_faces.values()),
                               'hood_mesh_triangles':sum(len(f['vertices'])==3 for f in mesh_faces.values()),
                               'hood_mesh_manifold_edges':len(edge_counts),
                               'hood_rune_quads':len(rune_faces),
                               'hood_depth_units':3.98-HOOD_FRONT_Z,
                               'hood_overhang_from_eye_units':-3.06-HOOD_FRONT_Z,
                               'hood_lower_rim_front_z':HOOD_LOWER_FRONT_Z,
                               'underside_closures':['hood_under_chin','hood_neck_lining','robe_inner_lining']})
print(f'Created {len(cubes)} cuboids, a volumetric {len(mesh_faces)}-face hood, {len(rune_faces)} wrapped rune quads.')
for warning in warnings:
    print('Warning:',warning)
