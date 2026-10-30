import json, struct, base64, sys, io
import numpy as np
src, out_bin, out_png = sys.argv[1:4]
g = json.load(open(src))
buf = base64.b64decode(g['buffers'][0]['uri'].split(',')[1])
def acc(i):
    a = g['accessors'][i]; bv = g['bufferViews'][a['bufferView']]
    off = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
    nc = {'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4}[a['type']]
    n = a['count']*nc
    if a['componentType'] == 5126:
        d = struct.unpack('<%df'%n, buf[off:off+4*n])
    elif a['componentType'] == 5123:
        d = struct.unpack('<%dH'%n, buf[off:off+2*n])
    elif a['componentType'] == 5125:
        d = struct.unpack('<%dI'%n, buf[off:off+4*n])
    else: raise Exception(a['componentType'])
    return [list(d[i*nc:(i+1)*nc]) for i in range(a['count'])]
nodes = g['nodes']; N = len(nodes)
parent = [-1]*N
for i, n in enumerate(nodes):
    for c in n.get('children', []): parent[c] = i
def quads_of(mesh):
    res = []
    for p in mesh['primitives']:
        pos = acc(p['attributes']['POSITION']); uv = acc(p['attributes']['TEXCOORD_0']); nor = acc(p['attributes']['NORMAL'])
        idx = [x[0] for x in acc(p['indices'])]
        for f in range(0, len(idx), 6):
            t1 = idx[f:f+3]; t2 = idx[f+3:f+6]
            shared = [v for v in t1 if v in t2]
            assert len(shared) == 2, (t1, t2)
            x = [v for v in t1 if v not in t2][0]
            d = [v for v in t2 if v not in t1][0]
            k = t1.index(x); pp, qq = t1[(k+1) % 3], t1[(k+2) % 3]
            quad = [x, pp, d, qq]
            # make sure winding is counter-clockwise seen from the face normal
            P = [np.array(pos[v]) for v in quad]; nrm = np.array(nor[quad[0]])
            if np.dot(np.cross(P[1]-P[0], P[3]-P[0]), nrm) < 0: quad = quad[::-1]
            res.append([(pos[v], uv[v], nor[v]) for v in quad])
    return res
o = io.BytesIO()
w = lambda fmt, *a: o.write(struct.pack('>'+fmt, *a))
def wstr(s):
    b = s.encode(); w('H', len(b)); o.write(b)
w('i', 0x4E435259); w('H', N)
total_q = 0
for i, n in enumerate(nodes):
    wstr(n.get('name', 'n%d' % i)); w('h', parent[i])
    w('3f', *n.get('translation', [0,0,0])); w('4f', *n.get('rotation', [0,0,0,1]))
    qs = quads_of(g['meshes'][n['mesh']]) if 'mesh' in n else []
    total_q += len(qs)
    w('H', len(qs))
    for q in qs:
        for (p, uv, nn) in q: w('8f', p[0], p[1], p[2], uv[0], uv[1], nn[0], nn[1], nn[2])
anims = g['animations']
w('H', len(anims))
for a in anims:
    wstr(a['name'].split('.')[-1])
    chans = []
    length = 0
    for c in a['channels']:
        s = a['samplers'][c['sampler']]
        t = [x[0] for x in acc(s['input'])]; v = acc(s['output'])
        assert s.get('interpolation', 'LINEAR') == 'LINEAR'
        chans.append((c['target']['node'], 0 if c['target']['path'] == 'translation' else 1, t, v))
        length = max(length, t[-1])
    w('f', length); w('H', len(chans))
    for (nd, path, t, v) in chans:
        w('H', nd); w('B', path); w('H', len(t))
        for x in t: w('f', x)
        for x in v:
            for y in x: w('f', y)
open(out_bin, 'wb').write(o.getvalue())
print('nodes', N, 'quads', total_q, 'bytes', len(o.getvalue()), 'anims', [a['name'] for a in anims])
from PIL import Image
im = Image.open(sys.argv[4]).convert('RGBA'); im.save(out_png); print(im.size, im.getextrema())
