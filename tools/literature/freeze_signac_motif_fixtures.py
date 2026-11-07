# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze literal-source/current-package motif controls including failed and bridged historical attempts."""
import json
from pathlib import Path
import zipfile
from validate_mashr_native import pin
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
TARGET=ROOT/'src/test/resources/org/jlinalg/sequence/signac-motifs'

def main():
    assert not TARGET.exists();record=json.loads((BASE/'motif-original-2/COMPLETED.json').read_bytes());assert record['controls']==144 and record['successful_controls']==111 and record['retained_source_failures']==33 and record['legacy_access_bridge'];assert all(pin(ROOT/n)==v for n,v in record['source_pins'].items());files={}
    for name in ['cases.json','original.json','original.stdout','original.stderr','original.process.json','session-info.txt','COMPLETED.json','SOURCE-PINS.json']:files[name]=(BASE/'motif-original-2'/name).read_bytes()
    for name in record['source_pins']:files['source/'+name]=(ROOT/name).read_bytes()
    TARGET.mkdir();archive=TARGET/'original-controls.zip'
    with zipfile.ZipFile(archive,'x') as z:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    with zipfile.ZipFile(archive) as z:assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))==len(files) and all(z.read(n)==v for n,v in files.items())
    sources=dict(record['source_pins']);sources['tools/literature/freeze_signac_motif_fixtures.py']=pin(Path(__file__));result=dict(status='frozen-original-motif-enrichment-and-matched-background-controls',source_pins=sources,archive=pin(archive),members=len(files),original_controls=144,successful_original_controls=111,retained_source_failures=33,legacy_metadata_access_bridge=True,original_functions_unchanged=True,historical_whole_packages_executed=False,full_current_package_executed=True)
    (TARGET/'CAPTURE.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8',newline='\n');(TARGET/'.gitattributes').write_text('*.json -text\n*.zip -text\n',encoding='utf8',newline='\n');print(json.dumps(dict(status=result['status'],archive=result['archive'],members=len(files))))

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original motif fixture checks')
    main()
