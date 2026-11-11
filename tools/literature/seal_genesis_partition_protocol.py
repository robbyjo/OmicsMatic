# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Append portable replay/skip/semantic-refusal evidence without changing the frozen native core capsule."""
import argparse
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,unpack,byte_pin
from verify_genesis_partition_evidence import HOME,inspect
BASE=ROOT/'build/literature-review/genesis-runtime'
STATUS='PASS_FROZEN_GENESIS_NATIVE_PORTABLE_REPLAYS_AND7_PROTOCOL_CONTRACTS'


def verify():
    core,_=inspect();proof=json.loads((HOME/'PROTOCOL.json').read_bytes());assert proof['status']==STATUS and not proof['new_catalog_credit']
    assert pin(HOME/'VALIDATION.json')==proof['core'] and pin(HOME/'protocol-controls.zip')==proof['archive']
    files=unpack(HOME/'protocol-controls.zip',proof)
    for name,value in proof['source_pins'].items():assert pin(ROOT/name)==value and byte_pin(files['source/'+name])==value
    prefix='build/literature-review/genesis-runtime/'
    for folder in ['native-partition-offline-win21-1','native-partition-offline-linux21-1']:
        saved=json.loads(files[prefix+folder+'/COMPLETED.json'])
        assert saved['status']=='PASS150_NATIVE_GENESIS_CASE_DISPOSITIONS_AND1024_R64_SUMS_OFFLINE_WITHOUT_R' and len(saved['records'])==2
        assert not saved['original_R_analysis_executed']
    saved=json.loads(files[prefix+'native-partition-protocol-1/COMPLETED.json']);assert len(saved['records'])==7 and all(r['actual']==r['expected'] for r in saved['records'])
    for label in ['resealed-partition-ID','resealed-R64-sum']:
        assert json.loads(files[prefix+'native-partition-protocol-1/'+label+'/replay/COMPLETED.json'])['status'].startswith('FAIL2_')
    assert json.loads(files[prefix+'native-partition-protocol-1/missing-java/COMPLETED.json'])['status'].startswith('SKIP3_')
    assert proof['complete_case_replays']==300 and proof['bit_exact_scalar_sum_replays']==2048
    return proof


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path);p.add_argument('--verify',action='store_true');a=p.parse_args()
    if a.verify:verify();print(STATUS);return
    assert a.out;out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    core=pin(HOME/'VALIDATION.json');inspect();assert not (HOME/'PROTOCOL.json').exists() and not (HOME/'protocol-controls.zip').exists()
    protocol=json.loads((BASE/'native-partition-protocol-1/COMPLETED.json').read_bytes())
    assert protocol['status']=='PASS7_NATIVE_GENESIS_SKIP_PRESERVATION_RESEALED_REFUSAL_AND_API_EXAMPLE_CONTRACTS'
    source_pins={n.replace('\\','/'):v for n,v in protocol['source_pins'].items()}
    assert len(source_pins)==len(protocol['source_pins']) and all((ROOT/n).resolve().is_relative_to(ROOT) for n in source_pins)
    source_pins[Path(__file__).relative_to(ROOT).as_posix()]=pin(Path(__file__))
    files={};objects={}
    def add(name,path):
        assert name not in files and not path.is_symlink();raw=path.read_bytes();files[name]=objects.setdefault(byte_pin(raw)['sha256'],raw)
    for n,v in source_pins.items():assert pin(ROOT/n)==v;add('source/'+n,ROOT/n)
    for folder in ['native-partition-offline-win21-1','native-partition-offline-linux21-1','native-partition-protocol-1']:
        for path in sorted((BASE/folder).rglob('*')):
            if path.is_file() and '__pycache__' not in path.parts and 'retained-reference' not in path.parts:add(path.relative_to(ROOT).as_posix(),path)
    packing=pack(out/'protocol-controls.zip',files);assert pin(out/'protocol-controls.zip')['bytes']<95000000
    (HOME/'protocol-controls.zip').write_bytes((out/'protocol-controls.zip').read_bytes())
    proof=dict(schema_version=1,status=STATUS,core=core,source_pins=source_pins,archive=pin(HOME/'protocol-controls.zip'),
        complete_case_replays=300,bit_exact_scalar_sum_replays=2048,actual_protocol_contracts=7,resealed_scientific_refusals=2,
        complete_example_hosts=['windows21','linux21'],new_catalog_credit=False,GENESIS_tool_still_missing=True,whole_goal_complete=False,
        scope='Portable source-complete saved replay, actual missing-Java skip, existing-output preservation, two coherently resealed wrong scientific-field refusals and a complete public API example. No R execution or broader GENESIS workflow admission.',**packing)
    save(HOME/'PROTOCOL.json',proof);verify();assert pin(HOME/'VALIDATION.json')==core
    save(out/'COMPLETED.json',dict(status=STATUS,proof=pin(HOME/'PROTOCOL.json'),core_preserved=True,new_catalog_credit=False,whole_goal_complete=False));print(STATUS,proof['archive']['bytes'],flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native core preservation, exact portable replay or semantic refusals')
    main()
