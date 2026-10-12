"""Read-only diagnostic: list MobVats block entities from an Anvil world save."""
import gzip
import io
import json
import struct
import sys
import zlib
from pathlib import Path


class NbtReader:
    def __init__(self, data):
        self.stream = io.BytesIO(data)

    def read(self, fmt):
        size = struct.calcsize(fmt)
        return struct.unpack(fmt, self.stream.read(size))[0]

    def string(self):
        return self.stream.read(self.read('>H')).decode('utf-8')

    def payload(self, kind):
        formats = {1: '>b', 2: '>h', 3: '>i', 4: '>q', 5: '>f', 6: '>d'}
        if kind in formats:
            return self.read(formats[kind])
        if kind == 7:
            return self.stream.read(self.read('>i'))
        if kind == 8:
            return self.string()
        if kind == 9:
            child, count = self.read('>B'), self.read('>i')
            return [self.payload(child) for _ in range(count)]
        if kind == 10:
            result = {}
            while (child := self.read('>B')) != 0:
                name = self.string()
                result[name] = self.payload(child)
            return result
        if kind in (11, 12):
            return [self.read('>i' if kind == 11 else '>q') for _ in range(self.read('>i'))]
        raise ValueError(f'Unsupported NBT tag {kind}')

    def root(self):
        kind = self.read('>B')
        self.string()
        return self.payload(kind)


def chunks(path):
    region = path.read_bytes()
    for index in range(1024):
        entry = int.from_bytes(region[index * 4:index * 4 + 4], 'big')
        sector = entry >> 8
        if not sector:
            continue
        start = sector * 4096
        length = int.from_bytes(region[start:start + 4], 'big')
        compression = region[start + 4]
        payload = region[start + 5:start + 4 + length]
        if compression == 1:
            payload = gzip.decompress(payload)
        elif compression == 2:
            payload = zlib.decompress(payload)
        elif compression != 3:
            raise ValueError(f'Unsupported chunk compression {compression} in {path}')
        yield NbtReader(payload).root()


def inspect(world):
    parts = []
    for region in sorted((world / 'region').glob('*.mca')):
        for chunk in chunks(region):
            for entity in chunk.get('block_entities', []):
                if entity.get('id', '').startswith('mobvats:'):
                    part = {key: entity[key] for key in ('id', 'x', 'y', 'z')}
                    for key in ('entity', 'milkTicks', 'milkReady'):
                        if key in entity:
                            part[key] = entity[key]
                    parts.append(part)
    return sorted(parts, key=lambda p: (p['y'], p['x'], p['z']))


if __name__ == '__main__':
    print(json.dumps(inspect(Path(sys.argv[1])), indent=2))
