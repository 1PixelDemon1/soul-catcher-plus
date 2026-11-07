from pathlib import Path
import itertools,json,math
ROOT=Path(__file__).resolve().parent

def rotate(v,degrees):
    # THREE.Euler XYZ: rotate about Z, then Y, then X.
    x,y,z=v;a,b,c=map(math.radians,degrees)
    x,y=x*math.cos(c)-y*math.sin(c),x*math.sin(c)+y*math.cos(c)
    x,z=x*math.cos(b)+z*math.sin(b),-x*math.sin(b)+z*math.cos(b)
    y,z=y*math.cos(a)-z*math.sin(a),y*math.sin(a)+z*math.cos(a)
    return [x,y,z]
def transform(p,pivot,angles,offset=(0,0,0)):
    q=rotate([a-b for a,b in zip(p,pivot)],angles)
    return [a+b+c for a,b,c in zip(q,pivot,offset)]
def bounds(model,animation=None,t=0):
    groups={g['uuid']:g for g in model['groups']};parent={};owner={}
    def tree(nodes,par=None):
        for n in nodes:
            if isinstance(n,str):owner[n]=par
            else:
                parent[n['uuid']]=par;tree(n['children'],n['uuid'])
    tree(model['outliner'])
    def track(bone,channel):
        frames=animation.get('animators',{}).get(bone,{}).get('keyframes',[]) if animation else []
        frames=sorted((f for f in frames if f['channel']==channel),key=lambda f:f['time'])
        if not frames:return [0,0,0]
        def value(f):return [float(f['data_points'][0][k]) for k in 'xyz']
        if t<=frames[0]['time']:return value(frames[0])
        for a,b in zip(frames,frames[1:]):
            if a['time']<=t<=b['time']:
                u=(t-a['time'])/(b['time']-a['time']);return [x+(y-x)*u for x,y in zip(value(a),value(b))]
        return value(frames[-1])
    points=[]
    for cube in model['elements']:
        for point in itertools.product(*zip(cube['from'],cube['to'])):
            p=transform(point,cube['origin'],cube['rotation']);bone=owner[cube['uuid']]
            while bone:
                group=groups[bone];angles=[a+b for a,b in zip(group.get('rotation',[0,0,0]),track(bone,'rotation'))]
                p=transform(p,group['origin'],angles,track(bone,'position'));bone=parent.get(bone)
            points.append(p)
    return [[min(p[a] for p in points) for a in range(3)],[max(p[a] for p in points) for a in range(3)]]
report={}
for filename in ['legendary_raven.bbmodel','legendary_raven_flight_pose.bbmodel']:
    model=json.loads((ROOT/filename).read_text());states={'rest':bounds(model)}
    for anim in model.get('animations',[]):
        sampled=[bounds(model,anim,anim['length']*i/240) for i in range(241)]
        states[anim['name']]=[[min(b[0][a] for b in sampled) for a in range(3)],[max(b[1][a] for b in sampled) for a in range(3)]]
    for name,(lo,hi) in states.items():
        assert lo[0]>=-8 and lo[1]>=0 and lo[2]>=-8 and hi[0]<=8 and hi[1]<=16 and hi[2]<=8,(filename,name,lo,hi)
    report[filename]={name:{'min':[round(x,3) for x in lo],'max':[round(x,3) for x in hi],'size':[round(b-a,3) for a,b in zip(lo,hi)]} for name,(lo,hi) in states.items()}
(ROOT/'bounds-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report,indent=2))
