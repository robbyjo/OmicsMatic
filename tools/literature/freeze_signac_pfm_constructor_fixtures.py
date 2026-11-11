# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve ten actual constructor cases and its unchanged original function body."""
import json,zipfile
from pathlib import Path
from validate_mashr_native import pin
ROOT=Path(__file__).resolve().parents[2]
def main():
    source=ROOT/'build/literature-review/signac-runtime/scan-pfm-constructor-1';target=ROOT/'src/test/resources/org/jlinalg/sequence/signac-pfm-construction';assert not target.exists();record=json.loads((source/'COMPLETED.json').read_bytes());assert record['controls']==10 and all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
    target.mkdir();names=['original.json','PFMatrix-original-function.R','probe.R','sessionInfo.txt','process.json','COMPLETED.json','collector-source.py','stderr.log','stdout.log'];archive=target/'original-controls.zip'
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED) as z:
        for name in names:
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,(source/name).read_bytes())
    sources={**record['source_pins'],'tools/literature/freeze_signac_pfm_constructor_fixtures.py':pin(Path(__file__))};(target/'CAPTURE.json').write_text(json.dumps(dict(status='captured-original-PFM-constructor-controls',archive=pin(archive),members=names,source_pins=sources),indent=2)+'\n',encoding='utf8',newline='\n');(target/'.gitattributes').write_text('*.zip -text\n*.json -text\n',encoding='utf8',newline='\n');print(json.dumps(pin(archive)))
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable constructor fixture checks')
    main()
