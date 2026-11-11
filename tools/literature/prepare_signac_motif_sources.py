# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain original motif enrichment, scanning and historical chromVAR wrappers with exact source pins."""
import json
from pathlib import Path
from prepare_signac_link_sources import extract,pin
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime/source-intake-1'
DEST=ROOT/'src/main/resources/org/jlinalg/omics/signac-motifs'

def main():
    assert not DEST.exists();DEST.mkdir();records=[]
    for edition in ['1.6.0','1.9.0','1.14.0','1.17.1']:
        functions={'motifs.R':['FindMotifs','AddMotifs.default'],'preprocessing.R':['CreateMotifMatrix','RegionStats.default'],'utilities.R':['MatchRegionStats','GRangesToString']}
        if edition!='1.17.1':functions['motifs.R']+=['RunChromVAR.ChromatinAssay']
        folder=DEST/edition;folder.mkdir();rows=[]
        for file,names in functions.items():
            path=BASE/edition/'source/Signac/R'/file;raw=path.read_bytes();text=raw.decode('utf8')
            for name in names:
                value=extract(text,name);resource=edition+'/'+name+'.R';(DEST/resource).write_bytes(value);rows.append(dict(name=name,resource=resource,function=pin(value),original_source=path.relative_to(ROOT).as_posix(),original_source_pin=pin(raw)))
        records.append(dict(edition=edition,profile=edition+('-package' if edition=='1.17.1' else '-source'),functions=rows,historical_whole_package=False))
    (DEST/'SOURCE-PROFILES.json').write_text(json.dumps(dict(schema_version=1,records=records),indent=2)+'\n',encoding='utf8',newline='\n');(DEST/'MIT-LICENSE.txt').write_bytes((ROOT/'src/main/resources/org/jlinalg/omics/signac-links/MIT-LICENSE.txt').read_bytes());(DEST/'.gitattributes').write_text('*.R -text\n*.json -text\n*.txt -text\n',encoding='utf8',newline='\n');print(json.dumps(dict(status='retained-original-motif-sources',profiles=len(records),functions=sum(len(r['functions']) for r in records))))

if __name__=='__main__':main()
