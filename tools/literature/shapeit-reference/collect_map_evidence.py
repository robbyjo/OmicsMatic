#!/usr/bin/env python3
"""Freeze original SHAPEIT map calls, checked failures and complete source closure."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[3]
BASE = ROOT / 'build/literature-review/shapeit-runtime'
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--capture', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    capture = args.capture.resolve()
    proof = json.loads((capture / 'observed.json').read_bytes())
    assert proof['status'] == 'passed-original-map-parsing-interpolation-and-checked-boundary-controls'
    assert proof['protocol_sha256'] == sha(ROOT / 'tools/literature/shapeit-reference/MapProtocol.cpp')
    original = ROOT / 'src/test/resources/org/jlinalg/genetics/shapeit-reference/initial-reader.zip'
    assert sha(original) == '7ce0722fbe1499ed72d5a46d81c9945d1141b24a7abbec07e5e1d3ce799e8f02'
    work = Path('/opt/omicsmatic-shapeit-dev-1')
    reconciled = json.loads((BASE / 'reader-protocol-attempt-1/build-reconciliation.json').read_bytes())
    changed = {row['path']: row['after'] for row in reconciled['allowed_changed_original_member']}
    guarded = []
    for name, source, manifest in [('4.1.3', work/'source4-attempt-1', BASE/'upstream/4.1.3-selected-source.json'),
                                   ('5-c34d', work/'source5-attempt-1', BASE/'upstream/5-c34d-selected-source.json')]:
        entries = json.loads(manifest.read_bytes())['files']
        for item in entries:
            path = source/item['path']
            actual = hashlib.sha256(path.readlink().as_posix().encode()).hexdigest() if item['mode']=='120000' else sha(path)
            expected = changed.get(item['path'], item['sha256']) if name=='5-c34d' else item['sha256']
            assert actual == expected, (name, item['path'])
        guarded.append({'profile':name,'source_members_verified':len(entries),'original_reference_sha256':sha(original)})
    source_new = BASE/'upstream/source4-v4.2.2'
    new_manifest = json.loads((capture/'4.2.2-selected-source.json').read_bytes())
    members = {}
    for item in new_manifest['files']:
        raw = (source_new/item['path']).read_bytes()
        assert hashlib.sha256(raw).hexdigest()==item['sha256']
        assert hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==item['git_blob_sha1']
        members['source/4.2.2/'+item['path']] = raw
    members['source/original-reader-source-closure.zip'] = original.read_bytes()
    for path in capture.iterdir():
        if path.is_file() and path.name!='running.json': members['capture/'+path.name] = path.read_bytes()
    members['protocol/MapProtocol.cpp'] = (ROOT/'tools/literature/shapeit-reference/MapProtocol.cpp').read_bytes()
    members['protocol/collect_map_evidence.py'] = Path(__file__).read_bytes()
    for name in ['run_map_protocols_1.py','run_map_protocols_2.py','map-protocol-controller-1.log','map-protocol-controller-2.log','4.2.2-checkout-before-byte-recovery.zip']:
        members['history/'+name] = (BASE/name).read_bytes()
    for name in ['SHAPEIT4-MIT.txt','SHAPEIT5-MIT.txt']:
        members['licenses/'+name] = (ROOT/'LICENSES'/name).read_bytes()
    for name in ['pixi.toml','pixi.lock','observed.json']:
        members['toolchain/'+name] = (BASE/'toolchain-attempt-1'/name).read_bytes()
    arithmetic_differences = 0
    for observation in proof['observations']:
        if observation['arithmetic']=='fused' and observation['complete']:
            split_name = observation['result'].replace('-fused-', '-split-')
            first = (capture/observation['result']).read_text().splitlines()
            second = (capture/split_name).read_text().splitlines()
            assert len(first)==len(second)
            arithmetic_differences += sum(a!=b for a,b in zip(first,second))
        if observation['complete']:
            assert observation['exit_code']==0
            assert (capture/observation['result']).read_text().splitlines()[-1].startswith('E\t')
        else:
            assert observation['source_undefined_boundary'] or observation['source_parser_rejected']
            assert observation['exit_code']!=0
    assert arithmetic_differences>0, 'The two actual compiler profiles must exercise rounding differences'
    provenance = {'schema_version':1,'status':'complete-original-SHAPEIT-map-controls','source_guards':guarded,
                  'source4_2_2_commit':new_manifest['commit'],'original_observations':len(proof['observations']),
                  'complete_observations':sum(o['complete'] for o in proof['observations']),
                  'expected_source_failure_observations':sum(not o['complete'] for o in proof['observations']),
                  'fused_vs_split_differing_result_rows':arithmetic_differences,
                  'source_undefined_boundaries_are_not_successful_analysis':True,'public_phasing_adapter_available':False}
    members['provenance.json'] = (json.dumps(provenance,indent=2)+'\n').encode()
    members['SHA256SUMS'] = ''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(members.items())).encode()
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with args.output.open('xb') as stream, zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,raw in sorted(members.items()):
            entry=zipfile.ZipInfo(name,(2026,11,1,0,0,0)); entry.compress_type=zipfile.ZIP_DEFLATED; entry.external_attr=0o100644<<16
            archive.writestr(entry,raw)
    with zipfile.ZipFile(args.output) as archive: assert archive.testzip() is None
    print(json.dumps(dict(provenance,archive_sha256=sha(args.output),members=len(members),decoded_bytes=sum(map(len,members.values()))),indent=2))


if __name__=='__main__': main()
