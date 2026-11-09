# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Test retained-result integrity on isolated copies; preserve every mutation and leave original evidence unchanged."""
import argparse,copy,json,shutil,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','result','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_HIFIASM_RETAINED_RESULT_CONTROLS',baselines=[],corruptions={},analysis_admitted=False,implementation_credit=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};sources=[ROOT/'src/main/java/org/jlinalg/cli/HifiasmResultConsistency.java',ROOT/'tools/literature/java/org/jlinalg/cli/HifiasmVerifyConsistencyAudit.java'];classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),*map(str,sources)];compiled=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stderr').write_bytes(compiled.stderr);save(out/'compile.json',dict(command=command,exit_code=compiled.returncode));assert compiled.returncode==0,compiled.stderr.decode();original=a.result.resolve();report=json.loads((original/'COMPLETED.json').read_bytes());assert report['status']=='PASS_COMPLETE_ORIGINAL_HIFIASM_AND_NATIVE_GRAPH_WORKFLOW' and report['analysis_admitted'];before={f.relative_to(original).as_posix():pin(f) for f in original.rglob('*') if f.is_file()};shadow=out/'isolated-copy';shutil.copytree(original,shadow);assert all(pin(shadow/n)==v for n,v in before.items())
        def run(label,java,is_linux):
            path=lambda p:linux(p) if is_linux else str(p);command=(['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',java] if is_linux else [java])+['-Xmx1g','-cp',path(classes)+(':' if is_linux else ';')+path(jar),'org.jlinalg.cli.HifiasmVerifyConsistencyAudit','--out',path(shadow)];process=subprocess.run(command,capture_output=True,timeout=300);(out/(label+'.stdout')).write_bytes(process.stdout);(out/(label+'.stderr')).write_bytes(process.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=process.returncode));return process.returncode
        for label,java,is_linux in [('windows21',str(JAVA21/'java.exe'),False),('windows25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',False),('linux21','/usr/bin/java',True)]:assert run('baseline-'+label,java,is_linux)==0;state['baselines'].append(label)
        first=report['analyses'][0]['id'];mutations=[]
        for name,member in [('original-graph',next((shadow/'analyses'/first/'original').glob('*.gfa')).relative_to(shadow).as_posix()),('native-fasta',next((shadow/'analyses'/first/'native').glob('*.fasta')).relative_to(shadow).as_posix()),('graph-audit','analyses/'+first+'/graph-audit.json'),('input-copy',next((shadow/'inputs').glob('*.reads')).relative_to(shadow).as_posix()),('original-process','analyses/'+first+'/process/process.json'),('warnings','WARNINGS.md'),('inventory','result-files.json')]:mutations.append((name,member,lambda raw:raw+b'\n# controlled retained-copy corruption\n'))
        def root_change(function):
            def mutate(raw):d=json.loads(raw);function(d);return (json.dumps(d,indent=2)+'\n').encode()
            return mutate
        mutations.extend([('root-status','COMPLETED.json',root_change(lambda d:d.update(status='FAIL2_CONTROLLED_CORRUPTION'))),('root-edition','COMPLETED.json',root_change(lambda d:d.update(edition='not-the-original-edition'))),('root-commit','COMPLETED.json',root_change(lambda d:d.update(commit='0'*40))),('root-case-identity','COMPLETED.json',root_change(lambda d:d['analyses'][0].update(id='not-the-original-analysis'))),('root-graph-count','COMPLETED.json',root_change(lambda d:d['analyses'][0].update(graph_views=d['analyses'][0]['graph_views']+1))),('root-biological-claim','COMPLETED.json',root_change(lambda d:d.update(biological_or_paper_reproduction_certified=True))),('root-earlier-population','COMPLETED.json',root_change(lambda d:d.update(earlier_cases_are_provisional_unless_entire_receipt_passes=[])))])
        accepted=[];records=out/'mutations';records.mkdir()
        for name,member,change in mutations:
            target=shadow/member;assert target.resolve().is_relative_to(shadow);raw=target.read_bytes();changed=change(raw);home=records/name;home.mkdir();(home/'before').write_bytes(raw);(home/'after').write_bytes(changed);target.write_bytes(changed)
            try:code=run('corrupt-'+name,str(JAVA21/'java.exe'),False)
            finally:target.write_bytes(raw)
            save(home/'mutation.json',dict(member=member,before=pin(home/'before'),after=pin(home/'after'),exit_code=code));state['corruptions'][name]=dict(member=member,exit_code=code)
            if code!=2:accepted.append(name)
        extra=shadow/'unexpected-retained-file';assert extra.parent.resolve()==shadow;extra.write_bytes(b'Controlled unindexed file\n')
        try:code=run('corrupt-extra-file',str(JAVA21/'java.exe'),False)
        finally:extra.unlink()
        state['corruptions']['extra-file']=dict(exit_code=code)
        if code!=2:accepted.append('extra-file')
        member=next((shadow/'analyses'/first/'original').glob('*.bin'));raw=member.read_bytes();assert member.parent.resolve().is_relative_to(shadow);member.unlink()
        try:code=run('corrupt-missing-file',str(JAVA21/'java.exe'),False)
        finally:member.write_bytes(raw)
        state['corruptions']['missing-file']=dict(exit_code=code)
        if code!=2:accepted.append('missing-file')
        assert all(pin(original/n)==v and pin(shadow/n)==v for n,v in before.items()) and all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state['unexpectedly_accepted_corruptions']=accepted;assert not accepted,'Retained-result verifier accepted incoherent metadata: '+repr(accepted);state.update(status='passed-three-JVM-complete-integrity-plus-summary-consistency-and-sixteen-corruptions',artifact=built['artifact'],original_result=dict(path=original.relative_to(ROOT).as_posix(),receipt=before['COMPLETED.json']),corruption_cases=16,source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),*sources]})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable retained-result and corruption checks')
    raise SystemExit(main())
