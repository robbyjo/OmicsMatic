# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Write the explicit six-gene local Enrichr example; this command runs no analysis or installation."""
import argparse,json
from pathlib import Path


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--runtime',required=True,help='Exact successful GSEApy setup COMPLETED.json, as seen by Linux Java')
    p.add_argument('--out',type=Path,required=True,help='NEW example directory under an existing parent')
    p.add_argument('--path-style',choices=['native','wsl'],default='native')
    a=p.parse_args();home=a.out.resolve()
    if home.exists() or not home.parent.is_dir():raise ValueError('Use a NEW example directory under an existing parent')
    def path(file):
        if a.path_style=='native':return str(file)
        if not file.drive or len(file.drive)!=2 or file.drive[1]!=':':raise ValueError('WSL conversion requires an explicit Windows drive path')
        return '/mnt/'+file.drive[0].lower()+'/'+file.as_posix()[3:]
    data=dict(schema_version=1,numeric_dtype='float64',query=['A','B'],background=['A','B','C','D','E','F'],gene_sets={'first':['A','B','C'],'zero-overlap':['C','D','E'],'third':['B','E']})
    request=dict(schema_version=1,mode='RUN',edition='1.1.8',runtime=a.runtime,out=path(home/'result'),analyses=[dict(id='local-enrichr',method='enrichr',interface='original-class',global_seed=1781,input=dict(path=path(home/'prepared.json'),format='prepared-json-float64',source='Declared synthetic six-gene local-library example; no paper input is inferred'),options=dict(organism='human',cutoff=0.05),policies=dict(source_input_changes='fail',nonfinite_diagnostics='fail',empty_results='fail'))],maximum_input_bytes=1048576,maximum_output_bytes=67108864,maximum_files=10000,timeout_seconds=180,maximum_audit_operations=1000000,maximum_matrix_cells=100000,python_hash_seed=0)
    # Validate path conversion before creating any directory.
    path(home);home.mkdir()
    for name,value in [('prepared.json',data),('request.json',request)]:
        with (home/name).open('x',encoding='utf8',newline='\n') as file:json.dump(value,file,ensure_ascii=False,indent=2);file.write('\n')
    print('Prepared '+str(home/'request.json')+'; no analysis or installation occurred')


if __name__=='__main__':main()
