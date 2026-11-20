"""Convert the CC0 Grab3D glow meshes to a tiny, dependency-free render resource.

Usage: python tools/convert_black_hole.py path/to/black-hole.glb
The display stand/body and its cyan base light are intentionally omitted.
"""
import json
import pathlib
import struct
import sys

source = pathlib.Path(sys.argv[1]).read_bytes()
length = struct.unpack_from('<I', source, 12)[0]
document = json.loads(source[20:20 + length])
binary = source[28 + length:]


def accessor(index):
    entry = document['accessors'][index]
    view = document['bufferViews'][entry['bufferView']]
    offset = view.get('byteOffset', 0) + entry.get('byteOffset', 0)
    size = {'SCALAR': 1, 'VEC3': 3}[entry['type']]
    code = {5123: 'H', 5125: 'I', 5126: 'f'}[entry['componentType']]
    fmt = '<' + code * size
    stride = view.get('byteStride', struct.calcsize(fmt))
    return [struct.unpack_from(fmt, binary, offset + n * stride) for n in range(entry['count'])]


meshes = []
for mesh in document['meshes']:
    if mesh['name'].endswith(('body', 'base_ring_glow')):
        continue
    primitive = mesh['primitives'][0]
    positions = accessor(primitive['attributes']['POSITION'])
    indices = [value[0] for value in accessor(primitive['indices'])]
    material = document['materials'][primitive['material']]
    color = material['pbrMetallicRoughness']['baseColorFactor'][:3]
    # GLTF colors are linear. The position/color shader expects display-space RGB.
    color = [round(value ** (1 / 2.2) * 255) for value in color]
    meshes.append((mesh['name'], positions, indices, color))
    print(mesh['name'], len(indices) // 3,
          [(round(min(p[k] for p in positions), 4), round(max(p[k] for p in positions), 4)) for k in range(3)])

# Centre of the disk in the source's Y-up coordinate system.
disk = next(mesh[1] for mesh in meshes if 'disk_hot' in mesh[0])
center = [(min(p[k] for p in disk) + max(p[k] for p in disk)) / 2 for k in range(3)]
outer = next(mesh[1] for mesh in meshes if 'disk_lilac' in mesh[0])
radius = max(sum((p[k] - center[k]) ** 2 for k in range(3)) ** .5 for p in outer)
target = pathlib.Path('src/main/resources/assets/cursecycle/models/black_hole.bin')
target.parent.mkdir(parents=True, exist_ok=True)
with target.open('wb') as output:
    output.write(struct.pack('>ii', 0x42484D31, len(meshes)))
    for name, positions, indices, color in meshes:
        output.write(struct.pack('>iiii', len(indices), *color))
        for index in indices:
            output.write(struct.pack('>fff', *((positions[index][k] - center[k]) / radius for k in range(3))))
print('Wrote', target, target.stat().st_size, 'bytes; source centre', center)
