#!/usr/bin/env python3
"""Regenerate desktop icon containers from the checked-in mobile master on macOS."""
import struct
import subprocess
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
master = root / 'src/main/resources/image/puritywallet-master.png'
images = root / 'src/main/resources/image'
for target in images.glob('sparrow*.png'):
    metadata = subprocess.check_output(['sips', '-g', 'pixelWidth', '-g', 'pixelHeight', str(target)], text=True)
    dimensions = [int(line.split(':')[1]) for line in metadata.splitlines() if 'pixel' in line]
    subprocess.run(['sips', '-z', str(dimensions[1]), str(dimensions[0]), str(master), '--out', str(target)], check=True, stdout=subprocess.DEVNULL)
subprocess.run(['sips', '-z', '256', '256', str(master), '--out', str(root / 'src/main/deploy/package/linux/Sparrow.png')], check=True, stdout=subprocess.DEVNULL)
with tempfile.TemporaryDirectory() as temp:
    pngs = {}
    for size in (16, 32, 48, 64, 128, 256, 512, 1024):
        target = Path(temp) / f'{size}.png'
        subprocess.run(['sips', '-z', str(size), str(size), str(master), '--out', str(target)], check=True, stdout=subprocess.DEVNULL)
        pngs[size] = target.read_bytes()
    sizes = (16, 32, 48, 64, 128, 256)
    entries, payload = [], bytearray()
    offset = 6 + 16 * len(sizes)
    for size in sizes:
        data = pngs[size]
        entries.append(struct.pack('<BBBBHHII', size % 256, size % 256, 0, 0, 1, 32, len(data), offset))
        payload.extend(data)
        offset += len(data)
    (root / 'src/main/deploy/package/windows/sparrow.ico').write_bytes(struct.pack('<HHH', 0, 1, len(sizes)) + b''.join(entries) + payload)
    chunks = b''.join(tag + struct.pack('>I', len(pngs[size]) + 8) + pngs[size] for size, tag in
                      ((16, b'icp4'), (32, b'icp5'), (64, b'icp6'), (128, b'ic07'), (256, b'ic08'), (512, b'ic09'), (1024, b'ic10')))
    (root / 'src/main/deploy/package/macos/sparrow.icns').write_bytes(b'icns' + struct.pack('>I', len(chunks) + 8) + chunks)
