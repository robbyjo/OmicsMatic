# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain original numeric controls and source/serialization provenance as immutable fixtures."""
import json,zipfile
from pathlib import Path
from validate_mashr_native import pin
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime';TARGET=ROOT/'src/test/resources/org/jlinalg/sequence/chromvar-deviations'
def main():
    assert not TARGET.exists();files={};sources={}
    for label,folder in [('basic','chromvar-deviations-original-3'),('extended','chromvar-extended-original-1')]:
        source=BASE/folder;proof=json.loads((source/'COMPLETED.json').read_bytes());assert pin(source/'cases.json')==proof['cases'] and pin(source/'original.json')==proof['original']
        for n,v in proof['source_pins'].items():assert pin(ROOT/n)==v;sources[n]=v
        for p in source.rglob('*'):
            if p.is_file():files[label+'/'+p.relative_to(source).as_posix()]=p.read_bytes()
    for folder in ['chromvar-deviations-original-1','chromvar-deviations-original-2']:
        for p in (BASE/folder).rglob('*'):
            if p.is_file():files['retained-attempts/'+folder+'/'+p.relative_to(BASE/folder).as_posix()]=p.read_bytes()
    meta=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/chromvar-deviations/SOURCE-PROFILES.json').read_bytes())
    for profile in meta['records']:
        for f in profile['functions']:
            n=f['original_source'];assert pin(ROOT/n)==f['original_source_pin'];sources[n]=f['original_source_pin'];files['original-source/'+n]=(ROOT/n).read_bytes()
    sources['tools/literature/freeze_chromvar_deviation_fixtures.py']=pin(Path(__file__));TARGET.mkdir();archive=TARGET/'original-controls.zip'
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    record=dict(status='retained-original-chromVAR-deviation-controls',archive=pin(archive),members=len(files),source_pins=sources,basic_protocols=52,extended_protocols=50,numerical_method_calls=306,historical_whole_packages_executed=False,background_sampling_performed=False)
    (TARGET/'CAPTURE.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8',newline='\n');(TARGET/'.gitattributes').write_text('*.zip -text\n*.json -text\n',encoding='utf8',newline='\n');print(json.dumps(dict(status=record['status'],archive=record['archive'])),flush=True)
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source fixture guards')
    main()
