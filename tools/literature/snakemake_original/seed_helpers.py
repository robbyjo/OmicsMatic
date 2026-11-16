#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seed isolated copies of existing capture/process primitives for the new adapter.

The originals remain unchanged. Exact source pins and all substitutions are
recorded for review; no old verifier, source tree or Git identity is overlaid.
"""
from pathlib import Path
import hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[3]
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def main():
    base='a3d212d4d23801667b152566c342ad696b17494c';records=[]
    for suffix in ['Files','Parameters','Json','Process','Capture','Outputs']:
        name='src/main/java/org/jlinalg/cli/Nextflow'+suffix+'.java';original=subprocess.check_output(['git','show',base+':'+name],cwd=ROOT)
        assert (ROOT/name).read_bytes().replace(b'\r\n',b'\n')==original.replace(b'\r\n',b'\n')
        text=original.decode().replace('Nextflow','SnakemakeOriginal').replace('NEXTFLOW','SNAKEMAKE').replace('eagle-owned-process-shutdown','snakemake-owned-process-shutdown')
        changes=['Namespace and diagnostic tool labels only; preserve the original primitive algorithm.']
        if suffix=='Files':
            old='return SimpleJson.parse(utf8(bytes(path,maximum)));'
            assert text.count(old)==1;text=text.replace(old,'return SnakemakeOriginalParameters.parse(utf8(bytes(path,maximum)),maximum,1000000);')
            changes.append('Metadata JSON uses the existing lossless parser, retaining numeric lexemes rather than converting through binary floating point. Capacity integers already accept its NumberToken type.')
        target=ROOT/('src/main/java/org/jlinalg/cli/SnakemakeOriginal'+suffix+'.java')
        with target.open('xb') as stream:stream.write(text.encode())
        records.append(dict(original=name,baseline=base,source=pin(original),new_path=target.relative_to(ROOT).as_posix(),new_source=pin(text.encode()),changes=changes))
    target=ROOT/'tools/literature/snakemake_original/GENERIC-HELPER-PROVENANCE.json'
    with target.open('x',encoding='utf-8',newline='\n') as stream:json.dump(dict(status='NEW_ISOLATED_ADAPTER_PRIMITIVES_NOT_YET_QUALIFIED',files=records,old_sources_changed=False,new_catalog_credit=0),stream,indent=2);stream.write('\n')
    print('ISOLATED_HELPER_SOURCES_CREATED_NOT_YET_QUALIFIED')
if __name__=='__main__':main()
