# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise real packaged ChIP-seq routes, refusal codes and saved portable results."""
from pathlib import Path
import argparse,hashlib,json,subprocess,sys

def require(v,message):
    if not v:raise ValueError(message)
def enc(v):return(json.dumps(v,indent=2,ensure_ascii=False)+'\n').encode()
def pin(p):
    with p.open('rb')as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def run(a):
    out=a.out.absolute();out.mkdir(exist_ok=False);base=[a.java,'-jar',str(a.jar),'nfcore-tools'];calls=[]
    def call(label,args,code):
        result=subprocess.run(base+[str(x)for x in args],stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        (out/(label+'.stdout')).write_bytes(result.stdout);(out/(label+'.stderr')).write_bytes(result.stderr)
        calls.append(dict(case=label,expected=code,exit_code=result.returncode))
        require(result.returncode==code,label+': '+result.stderr.decode(errors='replace'))
        if code:require(('SKIP3'if code==3 else'FAIL2').encode()in result.stderr,'Missing prominent diagnostic: '+label)
        return result
    root=call('root-help',['--help'],0)
    for action,label in(('CHIPSEQ-1.2.1','121'),('CHIPSEQ-2.0.0','200')):
        require(action.encode()in root.stdout,'Missing root help action')
        help=call('help-'+label,[action,'--help'],0)
        require(b'Required action:'in help.stdout and b'--resume'in help.stdout,'Missing user actions')
        call('missing-runtime-'+label,[action,'--request',out/'missing-request.json','--out',out/('missing-run-'+label)],3)
        require(not(out/('missing-run-'+label)).exists(),'Missing runtime created analysis output')
        call('malformed-'+label,[action,'--request'],2)
        call('duplicate-option-'+label,[action,'--request','a','--request','b'],2)
        call('missing-result-'+label,[action,'--verify',out/'missing-result'],2)
        export=out/('setup-'+label);call('export-'+label,[action,'--export-runtime-setup',export],0)
        before={p.name:pin(p)for p in export.iterdir()if p.is_file()};require(not(export/'COMPLETED.json').exists(),'Setup export claimed installation or analysis')
        call('preserve-existing-export-'+label,[action,'--export-runtime-setup',export],2)
        require(before=={p.name:pin(p)for p in export.iterdir()if p.is_file()},'Existing export changed')
    for action in('CHIPSEQ','CHIPSEQ-latest','CHIPSEQ-2.0.1'):call('unsupported-'+action,[action,'--help'],2)
    legacy=[]
    for label,path in(('121',a.legacy121),('200',a.legacy200)):
        if path is None:continue
        before={p.relative_to(path).as_posix():pin(p)for p in path.rglob('*')if p.is_file()}
        require(before and 'COMPLETED.json'in before,'Require a complete original portable result, not selected rows')
        call('legacy-verify-'+label,['CHIPSEQ-'+('1.2.1'if label=='121'else'2.0.0'),'--verify',path],0)
        require(before=={p.relative_to(path).as_posix():pin(p)for p in path.rglob('*')if p.is_file()},'Saved result changed during read-only verification')
        legacy.append(dict(edition='1.2.1'if label=='121'else'2.0.0',complete_files_checked=len(before),manifest=before['manifest.json'],original_capture_unchanged=True,scientific_models_refit=False))
    result=dict(status='PASS_PACKAGED_CHIPSEQ_COMMAND_ROUTES',platform=sys.platform,producer=pin(a.jar),calls=calls,legacy_results=legacy,analysis_run_by_this_validator=False,new_family_credit=0,whole_goal_complete=False)
    (out/'RESULT.json').write_bytes(enc(result));print(json.dumps({k:v for k,v in result.items()if k!='calls'},indent=2))
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for name in('jar','out','legacy121','legacy200'):p.add_argument('--'+name,type=Path,required=name in('jar','out'))
    p.add_argument('--java',required=True);a=p.parse_args();run(a)
