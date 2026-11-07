# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Package exact admitted current marker sources; historical summary fixtures remain separate."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    target=ROOT/'src/main/resources/org/jlinalg/omics/seurat-differential-reference';assert not target.exists();target.mkdir()
    for name in ['differential_expression.R','DESCRIPTION','LICENSE']:(target/name).write_bytes((BASE/'differential-source-intake-1'/name).read_bytes())
    (target/'MIT-LICENSE.txt').write_bytes((ROOT/'src/test/resources/org/jlinalg/sequence/seurat-marker-summary/SEURAT_LICENSE.txt').read_bytes())
    functions=json.loads((BASE/'differential-runtime-probe-2/probe/RESULT.json').read_bytes())['source_functions'];assert len(functions)==42 and all(all(v.values()) for v in functions.values())
    profile=dict(profile='Seurat-5.6.0-R4.6.1-base',files={n:sha(target/n) for n in ['differential_expression.R','DESCRIPTION','LICENSE','MIT-LICENSE.txt']},functions=sorted(functions),source_url='https://raw.githubusercontent.com/satijalab/seurat/v5.6.0/R/differential_expression.R',required_packages={'Seurat':'5.6.0','SeuratObject':'5.4.0','Matrix':'1.7.5','jsonlite':'2.0.0','future':'1.80.0','future.apply':'1.21.0','lmtest':'0.9.40'},absent_optional_packages=['presto','limma'],historical_whole_package=False,installed_DLL_source_authentication=False)
    (target/'SOURCE-PROFILE.json').write_text(json.dumps(profile,indent=2)+'\n',encoding='utf8',newline='\n');(target/'.gitattributes').write_text('*.R -text\n*.json -text\n*.txt -text\nDESCRIPTION -text\nLICENSE -text\n',encoding='ascii',newline='\n');print('Packaged42 current marker function source guards; optional backend presence remains explicit')
if __name__=='__main__':main()
