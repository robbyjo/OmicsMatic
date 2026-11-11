# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain full original motif-scanning/metadata/enrichment call chains in separate immutable resources."""
import json
from pathlib import Path
from prepare_signac_link_sources import extract,pin
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime/source-intake-1'
DEST=ROOT/'src/main/resources/org/jlinalg/omics/signac-scan'

def main():
    assert not DEST.exists();DEST.mkdir();records=[]
    functions={'motifs.R':['AddMotifs.default','AddMotifs.ChromatinAssay','AddMotifs.Seurat','FindMotifs'],'preprocessing.R':['CreateMotifMatrix','RegionStats.default','RegionStats.ChromatinAssay','RegionStats.Seurat'],'objects.R':['CreateMotifObject'],'utilities.R':['MatchRegionStats','GRangesToString','PFMatrixToList']}
    for edition in ['1.6.0','1.9.0','1.14.0','1.17.1']:
        (DEST/edition).mkdir();rows=[]
        for file,names in functions.items():
            path=BASE/edition/'source/Signac/R'/file;raw=path.read_bytes()
            for name in names:
                body=extract(raw.decode('utf8'),name);resource=edition+'/'+name+'.R';(DEST/resource).write_bytes(body);rows.append(dict(name=name,resource=resource,function=pin(body),original_source=path.relative_to(ROOT).as_posix(),original_source_pin=pin(raw)))
        records.append(dict(profile=edition+('-package' if edition=='1.17.1' else '-source'),edition=edition,functions=rows,motifmatchr='1.34.0-package',historical_whole_package=False))
    (DEST/'SOURCE-PROFILES.json').write_text(json.dumps(dict(schema_version=1,records=records),indent=2)+'\n',encoding='utf8',newline='\n');(DEST/'MIT-LICENSE.txt').write_bytes((ROOT/'src/main/resources/org/jlinalg/omics/signac-links/MIT-LICENSE.txt').read_bytes());(DEST/'.gitattributes').write_text('*.R -text\n*.json -text\n*.txt -text\n',encoding='utf8',newline='\n');print(json.dumps(dict(status='retained-complete-original-motif-scan-call-chains',profiles=4,functions_per_profile=12)))

if __name__=='__main__':main()
