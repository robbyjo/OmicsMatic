# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""One-time original source-resource intake. Refuses existing original resources; never refresh frozen evidence."""
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def main():
    folder=ROOT/'src/main/resources/org/jlinalg/omics/biomart-workflow';intake=ROOT/'build/literature-review/biomart-runtime/source-intake-2';report=json.loads((intake/'COMPLETED.json').read_bytes());versions={}
    assert not (folder/'sources.json').exists(),'Never overwrite frozen original source resources'
    for version in ['2.60.1','2.68.0']:
        source=intake/(version+'-source/source/R');out=folder/'source'/version;out.mkdir(parents=True);versions[version]={}
        for f in sorted(source.glob('*.R')):
            assert pin(f)==report['artifacts'][version+'-source']['members']['R/'+f.name];target=out/f.name;target.write_bytes(f.read_bytes());versions[version][f.name]=pin(target)
    save(folder/'sources.json',dict(schema_version=1,license='Artistic-2.0',versions=versions,original_functions_monkey_patched=False));(folder/'ARTISTIC-2.0.txt').write_bytes((ROOT/'src/main/resources/org/jlinalg/omics/biomart-queries/ARTISTIC-2.0.txt').read_bytes());(folder/'NOTICE.txt').write_text('Original biomaRt R sources are redistributed under Artistic-2.0, with all original authorship/source headers retained. Authors include Steffen Durinck, Wolfgang Huber and Mike Smith; consult original DESCRIPTION and package sources in the source-intake evidence. Java source-bound query planning and the adapter do not replace original getBM parsing or perform enrichment, nearest-gene inference or paper reproduction. Dependency and R runtime files are not redistributed by these source resources and retain their own licenses.\n',encoding='utf8',newline='\n')
    print('Captured exact original source resources; no runtime analysis/admission')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original resource identity checks')
    main()
