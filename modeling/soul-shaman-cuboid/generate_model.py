# SPDX-License-Identifier: MIT
"""Cuboid-only Soul Shaman. The approved non-mesh pieces are frozen in the base plan."""
import base64
import copy
import importlib.util
import itertools
import json
import math
from pathlib import Path
import uuid

HERE=Path(__file__).resolve().parent
SKILL=Path.home()/'.codex/skills/blockbench-model-bridge'
NS=uuid.UUID('a86e667d-4a6a-4f18-b9a0-770f127f78f6')
plan=json.loads((HERE/'base-cube-plan.json').read_text(encoding='utf-8'))
cubes=plan['cubes']
groups={group['name']:group for group in plan['groups']}
materials={texture['name']:i for i,texture in enumerate(plan['textures'])}
# Replace the old raised top mark together with the old hood construction.
cubes[:]=[c for c in cubes if c['name']!='hood_top_rune']
groups['head']['children'].remove('hood_top_rune')

# Two stepped boxes per sleeve, with a narrow shoulder and a wider cuff.
# Keep the flare modest while retaining arm pivots and hand positions.
for side in ('left','right'):
    sleeve=next(c for c in cubes if c['name']=='sleeve_'+side)
    split=(sleeve['from'][1]+sleeve['to'][1])/2
    pieces=[]
    for suffix,width_scale,depth_scale in [('upper',1.1,1.2),('cuff',1.35,1.3)]:
        piece=copy.deepcopy(sleeve)
        piece['name']=sleeve['name']+'_'+suffix
        piece['uuid']=str(uuid.uuid5(NS,piece['name']))
        piece['from'][1]=split if suffix=='upper' else sleeve['from'][1]
        # Slightly overlap the join so the halves do not leave a seam in motion.
        piece['to'][1]=sleeve['to'][1] if suffix=='upper' else split+.02
        height_scale=(piece['to'][1]-piece['from'][1])/(sleeve['to'][1]-sleeve['from'][1])
        for axis,scale in ((0,width_scale),(2,depth_scale)):
            center=(sleeve['from'][axis]+sleeve['to'][axis])/2
            half=(sleeve['to'][axis]-sleeve['from'][axis])*scale/2
            piece['from'][axis],piece['to'][axis]=center-half,center+half
        for name,face in piece['faces'].items():
            u0,v0,u1,v1=face['uv']
            u_scale=depth_scale if name in ('east','west') else width_scale
            v_scale=depth_scale if name in ('up','down') else height_scale
            face['uv']=[u0,v0,u0+(u1-u0)*u_scale,v0+(v1-v0)*v_scale]
        pieces.append(piece)
    index=cubes.index(sleeve)
    cubes[index:index+1]=pieces
    children=groups['arm_'+side]['children']
    index=children.index(sleeve['name'])
    children[index:index+1]=[p['name'] for p in pieces]


def dump(path,obj):
    path.write_text(json.dumps(obj,indent=2)+'\n',encoding='utf-8')


def cube(name,lo,hi,material='cloth',rotation=None,origin=None):
    width,height,depth=[b-a for a,b in zip(lo,hi)]
    faces={}
    for face in ('north','south','east','west','up','down'):
        if material=='cloth':
            w,h=(depth,height) if face in ('east','west') else (width,depth) if face in ('up','down') else (width,height)
            uv=[2,3,2+w*1.25,3+h*1.25]
        else:
            uv=[0,0,8,8]
        faces[face]={'texture':materials[material],'uv':uv}
    cubes.append({'name':name,'uuid':str(uuid.uuid5(NS,name)), 'from':lo,'to':hi,
                  'origin':origin or [(a+b)/2 for a,b in zip(lo,hi)],
                  'rotation':rotation or [0,0,0], 'faces':faces})
    groups['head']['children'].append(name)


def beam(name,start,end,width,z0,z1,material='cloth'):
    x,y=[(a+b)/2 for a,b in zip(start,end)]
    length=math.dist(start,end)
    angle=-math.degrees(math.atan2(end[0]-start[0],end[1]-start[1]))
    cube(name,[x-width/2,y-length/2,z0],[x+width/2,y+length/2,z1],material,
         [0,0,angle],[x,y,(z0+z1)/2])


# A full head volume from a few broad overlapping boxes, rather than thin face panels.
cube('hood_occiput',[-2.95,14.2,-.4],[2.95,19.95,3.15])
cube('hood_back_fold',[-2.9,14.65,2.8],[2.9,18.95,3.6],
     rotation=[12,0,0],origin=[0,16.8,3.2])
cube('hood_nape',[-2.5,13.35,.5],[2.5,14.8,2.7])
# The upper lip projects beyond the mask; the lower rim stays at Z -2.7.
cube('hood_visor',[-2.8,20.05,-3.8],[2.8,20.75,1.6])
cube('hood_crown_back',[-2.8,19.65,1.1],[2.8,20.35,3.15],
     rotation=[15,0,0],origin=[0,20.0,1.7])
for side,sign in [('left',-1),('right',1)]:
    lo_x,hi_x=(-4.1,-3.35) if sign<0 else (3.35,4.1)
    pivot=[sign*3.6,17.1,-.275]
    tilt=[0,0,sign*12]
    cube('hood_side_'+side,[lo_x,14.2,-2.75],[hi_x,19.95,2.2],
         rotation=tilt,origin=pivot)
    # A dark solid lining bridges the mask and sloping wall, including oblique views.
    xa,xb=(-3.4,-1.6) if sign<0 else (1.6,3.4)
    cube('hood_cheek_lining_'+side,[xa,14.15,-2.70],[xb,20.0,.1],
         'cavity',tilt,pivot)
    beam('hood_brow_'+side,[sign*2.65,20.45],[sign*3.15,19.8],.75,-3.45,1.5)
    beam('hood_lower_fold_'+side,[sign*4.05,14.3],[sign*2.0,13.35],.6,-2.7,1.2)
    # Cyan side motifs are equally editable thin cuboids, transformed with the wall.
    xa,xb=(-4.145,-4.105) if sign<0 else (4.105,4.145)
    for part,ya,yb,za,zb in [('front',16.65,17.7,-2.5,-1.9),
                            ('bridge',17.25,17.7,-1.9,.2),
                            ('back',17.25,18.05,.2,.85)]:
        cube('hood_side_mark_'+side+'_'+part,[xa,ya,za],[xb,yb,zb],
             'cyan_trim',tilt,pivot)
    for part,start,end in [('stem',[sign*4.1,15.7],[sign*3.65,18.0]),
                           ('hook',[sign*4.1,15.7],[sign*3.7,15.9]),
                           ('upper',[sign*3.65,18.0],[sign*3.3,18.2])]:
        beam('hood_front_mark_'+side+'_'+part,start,end,.35,-2.80,-2.76,'cyan_trim')
cube('hood_top_mark',[-1.65,20.76,-2.75],[1.65,20.80,-2.25],'cyan_trim')

# Keep each hem strip and its embroidery together under an independent waist pivot.
hem_names=['front_left','front_right','front_center','back_left','back_right',
           'back_center','side_-1','side_1']
for suffix in hem_names:
    name='hem_'+suffix
    cloth=next(c for c in cubes if c['name']=='skirt_'+suffix)
    children=[c['name'] for c in cubes if c['name']=='skirt_'+suffix
              or c['name'].endswith('_'+suffix) and c['name'].startswith('hem_rune_')]
    for child in children:
        groups['body']['children'].remove(child)
    group={'name':name,'uuid':str(uuid.uuid5(NS,name)),
           'origin':[cloth['origin'][0],8.05,cloth['origin'][2]],'children':children}
    plan['groups'].append(group)
    groups[name]=group
    plan['hierarchy']['body'].append(name)

# Shrink the figure while enlarging the staff about the hand, preserving the grip.
body_scale,staff_scale,lift=.88,1.06,1.2
grip=[6.0,9.2,-1.475]
staff_groups={'staff','charm','staff_flame'}
staff_cubes={n for name in staff_groups for n in groups[name]['children']}

def rescale(point,is_staff=False):
    result=[grip[i]*body_scale+(point[i]-grip[i])*staff_scale if is_staff
            else point[i]*body_scale for i in range(3)]
    result[1]+=lift
    return result

for c in cubes:
    for field in ('from','to','origin'):
        c[field]=rescale(c[field],c['name'] in staff_cubes)
for group in plan['groups']:
    group['origin']=rescale(group['origin'],group['name'] in staff_groups)

plan['name']='Soul Shaman Cuboid'
plan['model_format']='free'
plan['target_size']={'blockbench_units':[17,26,8], 'minecraft_blocks':[1.0625,1.625,.5],
                     'staff_height_units':24*staff_scale,
                     'fit_policy':'Rest pose: figure scaled to 88%, staff to 106% about the grip. Animation may extend beyond this envelope.'}
dump(HERE/'plan.json',plan)
spec=importlib.util.spec_from_file_location('bridge',SKILL/'scripts/plan_to_bbmodel.py')
bridge=importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)
model,warnings=bridge.convert(plan)
by_cube={c['name']:c['uuid'] for c in model['elements']}
def node(name):
    group=groups[name]
    return {'uuid':group['uuid'],'isOpen':name=='root',
            'children':[by_cube[n] for n in group['children']]
                       +[node(child) for child in plan['hierarchy'].get(name,[])]}
model['outliner']=[node('root')]
for group in model['groups']:
    group.update({'rotation':[0,0,0],'export':True,'visibility':True})
for texture in model['textures']:
    texture['source']='data:image/png;base64,'+base64.b64encode((HERE/texture['relative_path']).read_bytes()).decode()
assert all(element['type']=='cube' for element in model['elements'])


def animation(name,length,tracks,loop):
    """Editable preview clips; in-game curves are evaluated in SoulShamanModel."""
    animators={}
    for bone,channel,samples in tracks:
        animator=animators.setdefault(groups[bone]['uuid'],
            {'name':bone,'type':'bone','keyframes':[]})
        for index,(time,values) in enumerate(samples):
            animator['keyframes'].append({'channel':channel,
                'data_points':[dict(zip(('x','y','z'),map(str,values)))],
                'uuid':str(uuid.uuid5(NS,f'{name}:{bone}:{channel}:{index}')),
                'time':time,'interpolation':'linear'})
    return {'name':name,'uuid':str(uuid.uuid5(NS,name)),'loop':loop,
            'length':length,'snapping':20,'override':False,'animators':animators}


idle_tracks=[]
cycle=math.pi
idle_samples=[(cycle*i/64,math.sin(2*math.pi*i/64)) for i in range(65)]
idle_tracks.append(('root','position',[(t,[0,b*.32,0]) for t,b in idle_samples]))
idle_tracks.append(('wisp','rotation',[(t,[0,b*5,0]) for t,b in idle_samples]))
for i,suffix in enumerate(hem_names):
    idle_tracks.append(('hem_'+suffix,'position',
                        [(t,[0,b*(.18 if i%3==2 else .28),0]) for t,b in idle_samples]))
    idle_tracks.append(('hem_'+suffix,'rotation',
                        [(t,[b*(3 if i<3 else -3),0,0] if i<6
                          else [0,0,b*(-3 if i==6 else 3)]) for t,b in idle_samples]))

def smooth(value):
    t=max(0,min(1,value))
    return t*t*(3-2*t)

cast_tracks={name:[] for name in ('arm_right','arm_left','staff','charm')}
for tick in range(37):
    raise_arm=smooth(tick/12) if tick<12 else 1-smooth((tick-24)/12)
    sweep=raise_arm*(2*smooth((tick-12)/12)-1)
    rotations={'arm_right':[25*raise_arm,-18*sweep,28*raise_arm],
               'arm_left':[8*raise_arm,0,-6*raise_arm],
               'staff':[-8*raise_arm,12*sweep,-10*raise_arm], 'charm':[8*sweep,0,0]}
    for name,values in rotations.items():
        cast_tracks[name].append((tick/20,values))
model['animations']=[animation('idle_hover',cycle,idle_tracks,'loop'),
                     animation('cast_preview',1.8,[(n,'rotation',v) for n,v in cast_tracks.items()],'once')]


def rotated(point,c):
    p=[v-o for v,o in zip(point,c['origin'])]
    for axis,angle in enumerate(c['rotation']):
        a,b=((1,2),(2,0),(0,1))[axis]
        co,si=math.cos(math.radians(angle)),math.sin(math.radians(angle))
        p[a],p[b]=co*p[a]-si*p[b],si*p[a]+co*p[b]
    return [v+o for v,o in zip(p,c['origin'])]


points=[rotated(p,c) for c in cubes for p in itertools.product(*zip(c['from'],c['to']))]
low=[min(p[a] for p in points) for a in range(3)]
high=[max(p[a] for p in points) for a in range(3)]
assert high[0]-low[0]<=17 and high[1]<=26 and low[1]>=0 and high[2]-low[2]<=8
dump(HERE/'bounds-report.json',{'cuboids':len(cubes),'meshes':0,'quads':len(cubes)*6,
                               'triangles':len(cubes)*12,'min':low,'max':high,
                               'staff_height_units':24*staff_scale,
                               'staff_height_blocks':24*staff_scale/16})
dump(HERE/'soul_shaman.bbmodel',model)
print(f'Created {len(cubes)} cuboids, zero meshes, {len(cubes)*6} quads.')
for warning in warnings:
    print('Warning:',warning)
