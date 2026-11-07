# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal the exact official source intake, archive and receipts; no implementation or analysis credit."""
import json
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack


def main():
    source=ROOT/'build/literature-review/dndscv-runtime/source-intake-1';target=ROOT/'docs/literature/dndscv-source';assert not target.exists();receipt=json.loads((source/'SOURCE.json').read_bytes());profile=ROOT/'src/main/resources/org/jlinalg/omics/dndscv-workflow/SOURCE-PROFILE.json';p=json.loads(profile.read_bytes());assert receipt['commit']==p['commit'] and receipt['source_members']==p['files'];assert all(pin(source/'source'/n)==v for n,v in p['files'].items());files={path.relative_to(source).as_posix():path.read_bytes() for path in source.iterdir() if path.is_file()};files['SOURCE-PROFILE.json']=profile.read_bytes();target.mkdir();(target/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n');layout=pack(target/'source.zip',files);assert (target/'source.zip').stat().st_size<100000000;save(target/'VALIDATION.json',dict(schema_version=1,status='captured-exact-official-dndscv-source-only',source_commit=p['commit'],source_members=len(p['files']),source_receipt=pin(source/'SOURCE.json'),profile=pin(profile),archive=pin(target/'source.zip'),analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,**layout));(target/'SHA256SUMS').write_text(''.join(pin(target/n)['sha256']+'  '+n+'\n' for n in ['source.zip','VALIDATION.json']),encoding='utf8',newline='\n');print('Sealed',len(p['files']),'exact original source members;',pin(target/'source.zip')['bytes'],'bytes')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/evidence checks')
    main()
