# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Additional exact-runtime, pre-write preservation, capacity, unsupported-platform and Java25 original retrieval checks."""
import argparse,copy,json
from pathlib import Path
from setup_deseq2_reference import ROOT,environment,pin,save
from validate_mashr_native import JAVA21,linux
from prepare_biomart_workflow_example import example
from prepare_biomart_runtime import tree
from validate_biomart_workflow import run

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--runtime',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert not out.exists();out.mkdir(parents=True);build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};runtime=a.runtime.resolve();m=json.loads(runtime.read_bytes());sources=[Path(__file__).resolve(),ROOT/'tools/literature/validate_biomart_workflow.py',ROOT/'tools/literature/compare_biomart_workflow.R',ROOT/'tools/literature/prepare_biomart_workflow_example.py',ROOT/'tools/literature/prepare_biomart_runtime.py',ROOT/'tools/literature/export_biomart_runtime.py'];pins={f.relative_to(ROOT).as_posix():pin(f) for f in sources};pins.update(build['source_pins']);before={n:tree(Path(d)) for n,d in m['roots'].items()};state=dict(status='FAIL2_ADDITIONAL_BIOMART_CHECKS_INCOMPLETE',artifact=build['artifact'],source_pins=pins,checks=[],whole_goal_complete=False)
    for name in pins:
        f=out/'source-snapshot'/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes((ROOT/name).read_bytes())
    def execute(name,r,expected,java=None,external_out=None,on_linux=False):
        home=out/name;home.mkdir();r=copy.deepcopy(r);destination=external_out or home/'analysis';r['out']=linux(destination) if on_linux else str(destination);save(home/'request.json',r);command=([str(java or JAVA21/'java.exe'),'-Xmx2g','-cp',str(jar),'org.jlinalg.cli.BiomartWorkflowCli','--request',str(home/'request.json')] if not on_linux else ['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx2g','-cp',linux(jar),'org.jlinalg.cli.BiomartWorkflowCli','--request',linux(home/'request.json')]);code=run(command,home);assert code==expected,(name,code,(home/'stderr').read_text(errors='replace')[-4000:]);status=destination/'analysis-status.json'
        if expected==0:assert json.loads(status.read_bytes())['result_admitted']
        elif status.exists():assert not json.loads(status.read_bytes())['result_admitted'] and not (destination/'results').exists() and 'REQUIRED ACTION:' in (destination/'WARNINGS.md').read_text()
        else:assert not destination.exists() and 'FAIL2 NO RESULT ADMITTED:' in (home/'stderr').read_text(errors='replace')
        if destination.exists():
            for f in destination.rglob('process.json'):
                row=json.loads(f.read_bytes())
                if 'owned_live_after_cleanup' in row:assert not row['owned_live_after_cleanup']
        state['checks'].append(dict(name=name,exit_code=code,result_admitted=expected==0));print(name,'exit',code,flush=True);return home
    try:
        base=example(runtime,out/'unused','GRCh37')
        for name,field,value in [('changed-profile','profile_sha256','0'*64),('wrong-Rscript','rscript',str(Path(m['roots']['r-home'])/'bin/R.exe'))]:
            modified=copy.deepcopy(m);modified[field]=value;file=out/(name+'-runtime.json');save(file,modified);r=copy.deepcopy(base);r['runtime_manifest']=str(file);execute(name,r,3)
        modified=copy.deepcopy(m);empty=out/'empty-package';empty.mkdir();key=next(n for n in modified['roots'] if n.startswith('pkg-'));modified['roots'][key]=str(empty);file=out/'missing-package-runtime.json';save(file,modified);r=copy.deepcopy(base);r['runtime_manifest']=str(file);execute('missing-package-files',r,3)
        forbidden=Path(m['roots']['r-home'])/'biomart-forbidden-output-test';assert not forbidden.exists();execute('runtime-overlap-refused-before-write',base,2,external_out=forbidden);assert not forbidden.exists()
        r=copy.deepcopy(base);r['queries']=r['queries'][:1];r['queries'][0]['filters'][0]=dict(name='start',type='NUMERIC',values=['1f']);r['queries'][0]['check_filters']=False;execute('Java-numeric-suffix-refused',r,2)
        r=copy.deepcopy(base);r['maximum_output_bytes']=1024;execute('complete-output-capacity',r,2)
        r=copy.deepcopy(base);r['maximum_http_bytes']=1024;execute('complete-HTTP-capacity',r,2)
        r=copy.deepcopy(base);r['runtime_manifest']=linux(runtime);execute('Linux-original-runtime-skipped',r,3,on_linux=True)
        home=execute('Windows-Java25-original-GRCh37',base,0,java=Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'));reference=home/'independent-reference';reference.mkdir();temp=reference/'private-temp';temp.mkdir();env=environment();env.update(TMPDIR=str(temp),TMP=str(temp),TEMP=str(temp),BIOMART_CACHE=str(reference/'private-cache'));code=run([m['rscript'],'--vanilla',str(ROOT/'tools/literature/compare_biomart_workflow.R'),str(home/'request.json'),str(reference),*m['libraries']],reference,env);assert code==0,('Java25 complete original comparison',(reference/'stderr').read_text(errors='replace'));assert json.loads((reference/'COMPLETED.json').read_bytes())['status']=='PASS_COMPLETE_INDEPENDENT_ORIGINAL_DATAFRAMES_AND_TYPED_STATES';state['status']='passed-additional-biomaRt-runtime-preservation-capacity-and-Java25-original-comparisons'
    except Exception as error:state['error']=repr(error);print('FAIL2',repr(error),flush=True)
    after={n:tree(Path(d)) for n,d in m['roots'].items()};state['original_runtime_files_unchanged']=before==after
    if before!=after:state.update(status='FAIL2_ORIGINAL_RUNTIME_CHANGED',error='Compare complete before/after inventories; no result admitted')
    assert all(pin(ROOT/n)==v for n,v in pins.items());save(out/'runtime-before.json',before);save(out/'runtime-after.json',after);save(out/'COMPLETED.json',state);print(state['status']);return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original runtime and failure checks')
    raise SystemExit(main())
