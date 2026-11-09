"""Author Soul Raven's simple cuboid silhouette and editable Blockbench project."""
from pathlib import Path
import base64, importlib.util, itertools, json, math, struct, uuid, zlib

HERE = Path(__file__).resolve().parent
SKILL = Path.home() / '.codex/skills/blockbench-model-bridge'
NAMESPACE = uuid.UUID('425a4539-140a-4731-8b74-0a2018a68b24')
def uid(name): return str(uuid.uuid5(NAMESPACE, name))
def dump(path, value): path.write_text(json.dumps(value, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
# Flat material samples, not painted surface art. UVs deliberately use the same solid swatch.
materials = [('plumage','#242e3b'),('mantle','#253d43'),('wing','#1d2631'),('beak','#171d25'),('feet','#444a52'),('eye','#ef253e'),('amulet','#69dfe5'),('soul_accent','#2dbcd7')]
def flat_png(hex_color):
    rgb=bytes.fromhex(hex_color.lstrip('#')); size=8
    def chunk(tag,data): return struct.pack('>I',len(data))+tag+data+struct.pack('>I',zlib.crc32(tag+data)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,2,0,0,0))+chunk(b'IDAT',zlib.compress((b'\x00'+rgb*size)*size))+chunk(b'IEND',b'')
textures=[]
(HERE/'textures').mkdir(exist_ok=True)
for name,color in materials:
    (HERE/'textures'/f'{name}.png').write_bytes(flat_png(color))
    textures.append({'name':name,'path':f'textures/{name}.png','width':8,'height':8,'uuid':uid('material/'+name)})
C=[]; members={}
def cube(name,lo,hi,bone,material='plumage',rotation=None,origin=None):
    i=next(i for i,m in enumerate(materials) if m[0]==material)
    c={'name':name,'uuid':uid('cube/'+name),'from':lo,'to':hi,'origin':origin or [(a+b)/2 for a,b in zip(lo,hi)],'faces':{f:{'texture':i,'uv':[0,0,8,8]} for f in ['north','south','east','west','up','down']}}
    if rotation:c['rotation']=rotation
    C.append(c); members.setdefault(bone,[]).append(name)

# Readable masses, rather than stacked cubes that imitate rounded surfaces.
# Front is -Z. A deep upright chest supports a smaller, low rectangular skull.
cube('torso',[-1.65,1.9,-.6],[1.65,6.6,2.8],'body',origin=[0,4.2,1.1],rotation=[-12,0,0])
cube('neck',[-1.15,5.7,-1.7],[1.15,7.9,.65],'neck','mantle')
# The Forgotten Souls share a small framed cyan pendant. Attach it to the neck,
# not the head, so looking around does not turn the necklace with the beak.
for sign,side in [(-1,'left'),(1,'right')]:
    center=[sign*.43,7.05,-1.75]
    cube('amulet_cord_'+side,[center[0]-.07,6.62,-1.79],
         [center[0]+.07,7.48,-1.71],'neck','beak',
         rotation=[0,0,-sign*35],origin=center)
cube('amulet_frame',[-.6,5.65,-1.98],[.6,6.9,-1.68],'neck','feet')
cube('amulet_stone',[-.38,5.84,-2.035],[.38,6.71,-1.975],'neck','amulet')
cube('skull',[-1.35,7.2,-3.2],[1.35,9.2,-.55],'head')
cube('beak_base',[-.67,7.55,-5.1],[.67,8.45,-3.05],'head','beak')
cube('beak_tip',[-.48,7.55,-5.85],[.48,8.12,-4.95],'head','beak')
for sign,side in [(1,'left'),(-1,'right')]:
    def mirrored(lo,hi):
        if sign>0:return lo,hi
        return [-hi[0],lo[1],lo[2]],[-lo[0],hi[1],hi[2]]
    def mc(name,lo,hi,bone,material):
        a,b=mirrored(lo,hi);cube(side+'_'+name,a,b,bone,material)
    mc('eye',[1.351,8.2,-2.5],[1.38,8.65,-2.05],'head','eye')
    bone='wing_'+side
    # Three long slabs: shoulder, primaries and a squared-off tip.
    mc('wing_coverts',[1.55,2.6,.1],[2.2,6.5,2.8],bone,'wing')
    mc('wing_primaries',[1.66,1.3,1.15],[2.15,4.4,3.65],bone,'wing')
    mc('wing_tip',[1.7,1.0,2.5],[2.1,2.85,4.0],bone,'soul_accent')
    bone='leg_'+side
    mc('leg',[.7,.3,.55],[1.08,2.7,.95],bone,'feet')
    mc('foot',[.49,.05,-.55],[1.29,.32,1.0],bone,'feet')
    mc('hind_toe',[.73,.08,.9],[1.02,.3,1.65],bone,'feet')
# Three broad feathers form a hanging wedge tail; only their tips carry the cyan accent.
for name,x0,x1,bottom in [('center',-.5,.5,.15),('left',-1.25,-.42,.65),('right',.42,1.25,.65)]:
    tip_end = bottom + .85
    cube('tail_'+name,[x0,tip_end,2.65],[x1,3.55,3.12],'tail','wing',
         origin=[0,3.45,2.8],rotation=[-30,0,0])
    cube('tail_'+name+'_tip',[x0,bottom,2.65],[x1,tip_end,3.12],'tail','soul_accent',
         origin=[0,3.45,2.8],rotation=[-30,0,0])

pivots={'raven':[0,0,0],'body':[0,4.2,1.1],'neck':[0,6.5,-.6],'head':[0,7.5,-1.4],
        'wing_left':[1.75,6.3,.45],'wing_right':[-1.75,6.3,.45],'tail':[0,3.45,2.8],
        'leg_left':[.9,2.5,.75],'leg_right':[-.9,2.5,.75]}

groups=[{'name':n,'uuid':uid('bone/'+n),'origin':p,'children':members.get(n,[]),'isOpen':False} for n,p in pivots.items()]
def animation(name,length,channels):
    animators={}
    for bone,tracks in channels.items():
        frames=[]
        for channel,keys in tracks.items():
            for t,v in keys:
                frames.append({'channel':channel,'data_points':[dict(zip('xyz',map(str,v)))],'uuid':uid(f'{name}/{bone}/{channel}/{t}'),'time':t,'interpolation':'linear'})
        animators[uid('bone/'+bone)]={'name':bone,'type':'bone','keyframes':frames}
    return {'uuid':uid('animation/'+name),'name':name,'loop':'loop','override':False,'length':length,'snapping':20,'animators':animators}
perch=animation('perch_idle',3.0,{
 'body':{'position':[(0,[0,0,0]),(1.5,[0,.08,0]),(3,[0,0,0])]},
 'head':{'rotation':[(0,[0,-3,0]),(.8,[1,-3,0]),(1.4,[1,5,0]),(2.25,[0,5,0]),(3,[0,-3,0])]},
 'tail':{'rotation':[(0,[0,0,0]),(1.5,[2,0,0]),(3,[0,0,0])]}})
flight_tracks={
 'body':{'position':[(0,[0,.45,0]),(.2,[0,.7,0]),(.4,[0,.45,0]),(.6,[0,.2,0]),(.8,[0,.45,0])]},
 'head':{'rotation':[(0,[-6,0,0]),(.8,[-6,0,0])]},
 'tail':{'rotation':[(0,[-52,0,0]),(.4,[-56,0,0]),(.8,[-52,0,0])]}}
for side,sign in [('left',1),('right',-1)]:
    flight_tracks['wing_'+side]={'rotation':[(0,[0,0,sign*65]),(.2,[0,0,sign*112]),(.4,[0,0,sign*65]),(.6,[0,0,sign*25]),(.8,[0,0,sign*65])]}
    flight_tracks['leg_'+side]={'rotation':[(0,[-75,0,0]),(.8,[-75,0,0])]}
flight=animation('flight',.8,flight_tracks)
plan={'name':'Legendary Raven','model_identifier':'soulcatcherplus:legendary_raven','model_format':'free','format_version':'5.0','target_size':{'minecraft_blocks':[1,1,1],'blockbench_units':[16,16,16],'fit_policy':'Entire transformed model and both preview loops stay within x/z [-8,8], y [0,16].'},'texture_width':8,'texture_height':8,'textures':textures,'cubes':C,'groups':groups,'animations':[perch,flight]}
dump(HERE/'plan.json',plan)
spec=importlib.util.spec_from_file_location('bridge',SKILL/'scripts/plan_to_bbmodel.py');bridge=importlib.util.module_from_spec(spec);spec.loader.exec_module(bridge)
model,warnings=bridge.convert(plan)
by_group={g['name']:g for g in model['groups']}
by_element={e['name']:e['uuid'] for e in model['elements']}
children={'raven':['body'],'body':['neck','wing_left','wing_right','tail','leg_left','leg_right'],'neck':['head']}
def node(name):
    return {'uuid':by_group[name]['uuid'],'isOpen':name=='raven','children':[by_element[e] for e in members.get(name,[])]+[node(c) for c in children.get(name,[])]}
model['outliner']=[node('raven')]
plan['hierarchy']=children
dump(HERE/'plan.json',plan)
for group in model['groups']:group.update({'rotation':[0,0,0],'export':True,'visibility':True})
# Embed all swatches as well as supplying files: the model stays portable when copied alone.
for t in model['textures']:t.update({'source':'data:image/png;base64,'+base64.b64encode((HERE/t['relative_path']).read_bytes()).decode(),'saved':True})
dump(HERE/'legendary_raven.bbmodel',model)
# Static flight pose for quickly comparing the silhouette without playing the timeline.
posed=json.loads(json.dumps(model));posed['name']='Soul Raven - Flight pose';posed['animations']=[]
for g in posed['groups']:
    if g['name'] in ['wing_left','wing_right']:g['rotation']=[0,0,85 if g['name']=='wing_left' else -85]
    if g['name'].startswith('leg_'):g['rotation']=[-75,0,0]
    if g['name']=='head':g['rotation']=[-6,0,0]
    if g['name']=='tail':g['rotation']=[-54,0,0]
dump(HERE/'legendary_raven_flight_pose.bbmodel',posed)
print(f'Created {len(C)} cuboids, {len(groups)} named bones, {len(materials)} flat materials, 2 loops.')
