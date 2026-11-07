# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain literal LinkPeaks and dependent function sources for explicit package/source profiles."""
import hashlib
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime/source-intake-1'
DEST=ROOT/'src/main/resources/org/jlinalg/omics/signac-links'
FUNCTIONS={'links.R':['LinkPeaks','LinksToGRanges','DistanceToTSS'],'utilities.R':['MatchRegionStats','CollapseToLongestTranscript','Extend','corSparse','SparsifiedRanks','SparseSpearmanCor','StringToGRanges','GRangesToString'],'preprocessing.R':['FindTopFeatures.default']}
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def extract(text,name):
    start=text.index(name+' <- function(');brace=text.index('{',start);depth=0;quote=None;comment=False;escaped=False
    for i in range(brace,len(text)):
        char=text[i]
        if comment:
            if char=='\n':comment=False
            continue
        if quote:
            if escaped:escaped=False
            elif char=='\\':escaped=True
            elif char==quote:quote=None
            continue
        if char=='#':comment=True
        elif char in ['"',"'",'`']:quote=char
        elif char=='{':depth+=1
        elif char=='}':
            depth-=1
            if depth==0:return (text[start:i+1]+'\n').encode('utf8')
    raise ValueError('Unbalanced original function '+name)
def main():
    assert not DEST.exists();DEST.mkdir();records=[]
    for version in ['1.14.0','1.17.1']:
        folder=DEST/version;folder.mkdir();functions=[]
        for file,names in FUNCTIONS.items():
            source=BASE/version/'source/Signac/R'/file;raw=source.read_bytes();text=raw.decode('utf8')
            for name in names:
                function=extract(text,name);resource=version+'/'+name+'.R';(DEST/resource).write_bytes(function);functions.append(dict(name=name,resource=resource,function=pin(function),original_source=source.relative_to(ROOT).as_posix(),original_source_pin=pin(raw)))
        records.append(dict(profile=version+('-package' if version=='1.17.1' else '-source'),edition=version,functions=functions,execution='full-current-package' if version=='1.17.1' else 'literal-historical-functions-current-dependencies-not-historical-package',sampling='R-base-sample.int-no-wrswoR',background_weighting='query-over-background-density' if version=='1.17.1' else 'query-density-only',trans_chromosome_rule='regex-prefix-with-hyphen' if version=='1.17.1' else 'regex-prefix-without-hyphen'))
    (DEST/'MIT-LICENSE.txt').write_bytes((ROOT/'src/main/resources/org/jlinalg/omics/signac-lsi/MIT-LICENSE.txt').read_bytes());(DEST/'SOURCE-PROFILES.json').write_text(json.dumps(dict(schema_version=1,records=records,historical_packages_installed=False),indent=2)+'\n',encoding='utf8',newline='\n');(DEST/'.gitattributes').write_text('*.R -text\n*.json -text\n*.txt -text\n',encoding='utf8',newline='\n');print(json.dumps(dict(profiles=2,functions_per_profile=12,status='retained-literal-LinkPeaks-and-dependency-sources')))
if __name__=='__main__':main()
