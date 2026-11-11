# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare independent original health trees using explicitly present XML attributes, without DTD-injected annotations."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from xml.parsers import expat

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/meme-runtime'


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def explicit_tree(raw):
    parser = expat.ParserCreate(encoding='UTF-8'); parser.specified_attributes = True
    parser.SetParamEntityParsing(expat.XML_PARAM_ENTITY_PARSING_NEVER)
    parser.ExternalEntityRefHandler = lambda *args: 0
    stack = []; roots = []
    def start(name, attrs):
        node = dict(name=name, attributes={k:v for k,v in attrs.items() if k != 'elapsed_time'}, text='', children=[])
        if stack: stack[-1]['children'].append(node)
        else: roots.append(node)
        stack.append(node)
    def end(name):
        node = stack.pop(); assert node['name'] == name; node['text'] = node['text'].strip()
        if node['children']: assert not node['text']
    def text(value):
        if stack: stack[-1]['text'] += value
        else: assert not value.strip()
    parser.StartElementHandler = start; parser.EndElementHandler = end; parser.CharacterDataHandler = text
    parser.Parse(raw, True); assert len(roots) == 1 and not stack
    return roots[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve(); assert out.is_relative_to(BASE) and not out.exists(); out.mkdir()
    target = ROOT / 'src/main/resources/org/jlinalg/omics/meme-discovery/health-reference.json'
    previous = target.read_bytes(); assert pin(previous) == dict(bytes=1232295, sha256='3805836104cbd576fb1d13c32dd030c35e26154854a7b9a60e7a8be52a5d1868')
    (out / 'previous-health-reference.json').write_bytes(previous); old = json.loads(previous); result = {}; audit = []
    for profile, edition in [('MEME_4_9_1_BASE','4.9.1-base'),('MEME_4_9_1_PATCH2','4.9.1-patch2'),('MEME_5_5_4','5.5.4'),('MEME_5_5_9','5.5.9')]:
        path = BASE / ('sites-original-' + edition + '-1/captured/edge-both/result/meme.xml'); raw = path.read_bytes()
        assert pin(raw) == old[profile]['source_XML']; root = explicit_tree(raw)
        motifs = next(n for n in root['children'] if n['name'] == 'motifs')['children']
        summary = next(n for n in root['children'] if n['name'] == 'scanned_sites_summary')
        projection = dict(version=root['attributes']['version'], motifs=motifs, scanned_sites_summary=summary)
        defaults = []
        def compare(actual, former, where):
            assert actual['name'] == former['name'] and actual['text'] == former['text'] and len(actual['children']) == len(former['children'])
            assert all(former['attributes'][k] == v for k,v in actual['attributes'].items())
            for key in former['attributes'].keys() - actual['attributes'].keys():
                assert key == 'url' and former['attributes'][key] == '', 'Unreviewed scientific/reference difference'
                defaults.append(dict(node=where, attribute=key, injected_default=''))
            for i,(a,b) in enumerate(zip(actual['children'],former['children'])): compare(a,b,where+'/'+str(i))
        assert projection['version'] == old[profile]['scientific_projection']['version']
        for i,(a,b) in enumerate(zip(motifs,old[profile]['scientific_projection']['motifs'])): compare(a,b,'motif/'+str(i))
        compare(summary,old[profile]['scientific_projection']['scanned_sites_summary'],'scanned_sites_summary')
        result[profile] = dict(version=old[profile]['version'], source_XML=pin(raw), scientific_projection=projection)
        audit.append(dict(profile=profile, original_XML=pin(raw), removed_DTD_injected_empty_URL_attributes=defaults,
            every_explicit_attribute_text_child_and_scientific_field_unchanged=True))
    updated = (json.dumps(result, indent=2) + '\n').encode('utf8'); target.write_bytes(updated)
    proof = dict(status='passed-independent-explicit-XML-attribute-health-reference-intake', previous_resource=pin(previous), current_resource=pin(updated), profiles=audit,
        reason='ElementTree injected the internal DTD default url="" while the bounded Java reader retains explicit attributes only. Expat specified_attributes=True aligns representation without dropping any explicitly present annotation or scientific value. Elapsed timing remains the only deliberately excluded runtime field.',
        source_pins={Path(__file__).relative_to(ROOT).as_posix(): pin(Path(__file__).read_bytes())})
    (out / 'COMPLETED.json').write_text(json.dumps(proof, indent=2) + '\n', encoding='utf8')
    print(json.dumps(dict(status=proof['status'], resource=pin(updated), removed_defaults=sum(len(r['removed_DTD_injected_empty_URL_attributes']) for r in audit))))


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable independent reference preservation checks')
    main()
