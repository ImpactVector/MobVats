"""Verify consolidated block/item identities and installed material in disposable Anvil saves."""
from collections import Counter
from pathlib import Path
import json
import sys
from inspect_saved_vats import chunks

MECHANICAL = {'item_conduit', 'fluid_conduit', 'fluid_buffer', 'item_port', 'fluid_port',
              'tool_port', 'feed_port', 'blender', 'pull_pump', 'push_pump', 'fill_valve',
              'drain_valve', 'item_void', 'fluid_void'}
MATERIALS = {'none', 'vat_t1_casing', 'vat_t1_glass', 'vat_t2_casing', 'vat_t2_glass', 'vat_t3_casing', 'vat_t3_glass', 'mixer_t1_casing', 'mixer_t1_glass'}
OBSOLETE = {'mobvats:' + family + cladding + '_' + function
            for family in ('', 'mixer_') for cladding in ('casing', 'glass')
            for function in ('item_conduit', 'fluid_conduit', 'fluid_buffer')}
OBSOLETE |= {'mobvats:vat_' + function for function in MECHANICAL
             if function not in ('item_conduit', 'fluid_conduit', 'fluid_buffer')}
OBSOLETE |= {'mobvats:mixer_' + function for function in
             ('item_port', 'fluid_port', 'pull_pump', 'push_pump', 'fluid_void',
              'item_input', 'fluid_input', 'fluid_output')}
OBSOLETE |= {name + '_tap' for name in tuple(OBSOLETE) if name.endswith('_conduit')}

def item_stacks(value):
    if isinstance(value, dict):
        if 'id' in value and ('count' in value or 'Count' in value):
            yield value
        for child in value.values():
            yield from item_stacks(child)
    elif isinstance(value, list):
        for child in value:
            yield from item_stacks(child)

def verify(world):
    counts = Counter()
    states = Counter()
    number = 0
    for region in sorted((world / 'region').glob('*.mca')):
        for chunk in chunks(region):
            number += 1
            for section in chunk.get('sections', []):
                for state in section.get('block_states', {}).get('palette', []):
                    name = state['Name']
                    assert name not in OBSOLETE, f'Obsolete block {name} in {region}'
                    if name.startswith('mobvats:'):
                        counts[name] += 1
                    if name.removeprefix('mobvats:') in MECHANICAL:
                        properties = state.get('Properties', {})
                        material = properties.get('cladding_material')
                        assert material in MATERIALS, (name, properties)
                        if name.endswith('_conduit'):
                            assert properties.get('mode') in ('trunk', 'tap'), (name, properties)
                        states[material] += 1
            for item in item_stacks(chunk):
                assert item['id'] not in OBSOLETE, f'Obsolete item {item["id"]} in {region}'
    assert number and counts and states, f'No mechanical fixtures found in {world}'
    return {'world': str(world.resolve()), 'chunks': number,
            'mobvats_palette_ids': sorted(counts), 'material_palette_entries': dict(states),
            'obsolete_blocks_or_items': 0}

if __name__ == '__main__':
    print(json.dumps([verify(Path(arg)) for arg in sys.argv[1:]], indent=2))
