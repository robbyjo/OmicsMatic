# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture unchanged MTAG input parsing/merging/QC as binary64 hex; no covariance estimation or new fit."""
import argparse
import json
import os
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_mtag_reference import BASE, COMMIT, linux

PROGRAM = r'''from __future__ import print_function
import sys,json
sys.path.insert(0,sys.argv[1])
import mtag
import numpy as np
tokens=json.load(open(sys.argv[2]))
args=mtag.parser.parse_args(tokens)
assert not args.use_beta_se and not args.meta_format
union,data,args=mtag.load_and_merge_data(args)
z,n,f,template,data,nraw=mtag.extract_gwas_sumstats(data,args,list(np.arange(args.P)))
def hexmatrix(a): return [[float(v).hex() for v in row] for row in a]
ids=[str(v) for v in template['SNP'].values]
record=dict(status='ORIGINAL_PARSING_QC_ONLY_NO_COVARIANCE_OR_FIT',variants=ids,z=hexmatrix(z),n=hexmatrix(n),freq=hexmatrix(f),nraw=hexmatrix(nraw),
            template_columns=list(template.columns),template_rows=[[str(v) for v in row] for row in template.values])
with open(sys.argv[3],'w') as stream:json.dump(record,stream,sort_keys=True,indent=2)
print('Captured original parsing/QC for %d variants and %d traits; no fit' % z.shape)
'''


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out',type=Path,required=True)
    p.add_argument('--probe',type=Path,required=True)
    p.add_argument('--setup',type=Path,required=True)
    a=p.parse_args();out=a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    state=dict(status='FAIL2_MTAG_PARSER_CONTROL',new_fit=False,new_catalog_credit=False)
    (out/'capture-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    try:
        setup=json.loads((a.setup/'COMPLETED.json').read_bytes())
        assert setup['status']=='PASS_ORIGINAL_MTAG108_REFERENCE_PREPARED_ONLY' and setup['source_commit']==COMMIT
        probe=a.probe.resolve();record=json.loads((probe/'COMPLETED.json').read_bytes())
        assert record['status']=='PASS_ORIGINAL_MTAG_WORKFLOW_SYNTHETIC_PROBE_ONLY'
        source=BASE/'source-intake-1/source'
        for name,wanted in setup['source_inventory'].items():assert pin(source/name)==wanted,name
        cmd=json.loads((probe/'original.command.json').read_bytes())['command']
        tokens=cmd[cmd.index('--sumstats'):]
        save(out/'original-options.json',tokens)
        worker=out/'parser-control.py';worker.write_text(PROGRAM,encoding='utf8',newline='\n')
        command=['wsl.exe','-d',setup['distribution'],'--','/usr/bin/env','-i','PATH=/usr/bin:/bin','LC_ALL=C','OPENBLAS_NUM_THREADS=1','OMP_NUM_THREADS=1',
                 'MKL_NUM_THREADS=1','PYTHONHASHSEED=0','PYTHONDONTWRITEBYTECODE=1',setup['python'],'-B',linux(worker),linux(source),linux(out/'original-options.json'),linux(out/'original-parsed-hex.json')]
        result=subprocess.run(command,cwd=ROOT,env=dict(os.environ),stdin=subprocess.DEVNULL,capture_output=True,timeout=300)
        (out/'original.stdout').write_bytes(result.stdout);(out/'original.stderr').write_bytes(result.stderr)
        save(out/'original.process.json',dict(command=command,exit_code=result.returncode))
        assert result.returncode==0,result.stderr[-3000:]
        values=json.loads((out/'original-parsed-hex.json').read_bytes())
        assert values['status']=='ORIGINAL_PARSING_QC_ONLY_NO_COVARIANCE_OR_FIT' and len(values['variants'])==20000
        for name,wanted in setup['source_inventory'].items():assert pin(source/name)==wanted,name
        state.update(status='PASS_ORIGINAL_MTAG_PARSER_CONTROL_ONLY',variants=20000,traits=3,source_commit=COMMIT,parsed_hex=pin(out/'original-parsed-hex.json'),
                     source_inventory=setup['source_inventory'],capture_helper_pin=pin(Path(__file__)))
        print(state['status'])
    except Exception as error:state['error']=repr(error);print(state['status']+': '+repr(error))
    save(out/'COMPLETED.json',state)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original parser/source checks')
    raise SystemExit(main())
