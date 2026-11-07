# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run actual typed user-workflow, retry, failure, cache and portable-bundle controls with three original engines."""
import argparse,concurrent.futures,copy,csv,hashlib,json,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from collect_nextflow_runtime_controls import WORKFLOW,EXPECTED
from validate_mashr_native import JAVA21,linux,pin
DISTRO='JLinAlg-Omics-SAIGE-20261026'
VERSIONS=[('21.04.1','21-v2'),('22.10.6','22'),('24.04.4','24')]

def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')
def wsl(*argv):
    result=subprocess.run(['wsl.exe','-d',DISTRO,'--exec',*map(str,argv)],cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=60)
    if result.returncode:raise RuntimeError('Controller helper failed: '+result.stderr.decode(errors='replace'))
    return result.stdout

def main():
    if not __debug__:raise RuntimeError('Never disable workflow checks')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    jar=args.artifact.resolve();out=args.out.resolve();base=ROOT/'build/literature-review/nextflow-runtime';assert jar.is_relative_to(base.resolve()) and out.is_relative_to(base.resolve()) and not out.exists();out.mkdir()
    artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');sources={p.relative_to(ROOT).as_posix():pin(p) for p in [*sorted((ROOT/'src/main/java/org/jlinalg/cli').glob('Nextflow*.java')),ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java',Path(__file__),ROOT/'tools/literature/collect_nextflow_runtime_controls.py']}
    remote=wsl('/usr/bin/python3','-c','import tempfile;print(tempfile.mkdtemp(prefix="omicsmatic-nextflow-workflows-"))').decode().strip();assert remote.startswith('/tmp/omicsmatic-nextflow-workflows-') and '\n' not in remote
    write(out/'OWNER.json',dict(owned_linux_root=remote,artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),cleanup='Retained for inspection; no test cleanup/deletion is performed.'))
    setup=base/'managed-setup-development-1'
    java=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/timeout','--signal=TERM','--kill-after=10s','300s','/usr/bin/java','-Xmx256m','-XX:ActiveProcessorCount=2','-jar',linux(jar),'nextflow-tools']
    def run(folder,label,arguments,expected):
        with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:result=subprocess.run(java+arguments,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=330)
        record=dict(arguments=arguments,expected_exit=expected,actual_exit=result.returncode);write(folder/(label+'.json'),record)
        if result.returncode!=expected:raise RuntimeError(str(folder.relative_to(out))+'/'+label+': expected '+str(expected)+', got '+str(result.returncode)+'; inspect complete logs')
        return record
    def retain(session,folder):
        script='''import shutil,sys
from pathlib import Path
s=Path(sys.argv[1]);d=Path(sys.argv[2]);d.mkdir()
for p in s.iterdir():
 if p.is_file():shutil.copyfile(p,d/p.name)
for name in ('attempts','objects'):
 if (s/name).exists():shutil.copytree(s/name,d/name)
'''
        wsl('/usr/bin/python3','-c',script,session,linux(folder/'retained'))
    def family(spec):
        version,suffix=spec;family=out/version;family.mkdir();remote_family=remote+'/'+version;wsl('/usr/bin/mkdir',remote_family)
        modes=['module-success','task-failure','ignored-failure','empty-requires-tasks','empty-missing-output','pure-dataflow','retry-success','retry-exhausted','fifo-leftover','missing-runtime','missing-tool','missing-output']
        records=[]
        for mode in modes:
            folder=family/mode;folder.mkdir();project=folder/"workflow-α's data";project.mkdir();inputs=project/'inputs';inputs.mkdir();(project/'modules').mkdir();(project/'settings').mkdir();(project/'empty-asset-dir').mkdir()
            for name,sequence in [('a','ACGTacgt'),('b','AAATTT'),('c','GCGCgcgcNN')]: (inputs/(name+'.fa')).write_text('>'+name+'\n'+sequence+'\n',encoding='utf-8',newline='\n')
            module=WORKFLOW[WORKFLOW.index('process COUNTS {'):WORKFLOW.index('process GATHER {')]
            main=WORKFLOW.replace(module,"include { COUNTS } from './modules/counts'\n\n")
            if mode.startswith('retry-'):
                main=main.replace("errorStrategy { params.ignore ? 'ignore' : 'terminate' }","errorStrategy 'retry'\n    maxRetries 1").replace("if(params.fail) 'exit 7'","if(task.attempt == 1) 'exit 7'" if mode=='retry-success' else "if(true) 'exit 7'")
            if mode=='fifo-leftover':main=main.replace("else 'cat *.tsv | LC_ALL=C sort > counts.tsv'","else 'cat *.tsv | LC_ALL=C sort > counts.tsv; mkfifo scratch.pipe'")
            if mode=='pure-dataflow':main="nextflow.enable.dsl=2\nparams.outdir=null\nworkflow { new File(params.outdir,'pure.txt').text='pure\\n' }\n"
            (project/'main.nf').write_text(main,encoding='utf-8',newline='\n');(project/'modules/counts.nf').write_text(module,encoding='utf-8',newline='\n')
            (project/'settings/base.config').write_text("includeConfig '../executor.config'\n",encoding='utf-8',newline='\n')
            (project/'executor.config').write_text("process.executor='local'\nprocess.cpus=1\nprocess.memory='64 MB'\nprocess.maxForks=2\nexecutor.cpus=2\nexecutor.memory='1 GB'\n",encoding='utf-8',newline='\n')
            (project/'asset.txt').write_text('asset\n',encoding='utf-8',newline='\n')
            wsl('/usr/bin/python3','-c','import os,sys;from pathlib import Path;p=Path(sys.argv[1]);os.link(p/"asset.txt",p/"asset-hardlink.txt");os.symlink("./asset.txt",p/"asset-link.txt");os.symlink("asset-link.txt",p/"asset-chain.txt")',linux(project))
            parameters=dict(inputs={'$input':'reads'},outdir={'$output':''},fail=mode in ('task-failure','ignored-failure'),ignore=mode=='ignored-failure',empty=mode in ('empty-requires-tasks','empty-missing-output'),large_integer=9007199254740993)
            output='pure.txt' if mode=='pure-dataflow' else 'not-produced.tsv' if mode=='missing-output' else 'counts.tsv'
            recipe=dict(schema_version=1,engine_profile='linux-x86_64-nextflow-'+version,workflow=dict(path=linux(project),entrypoint='main.nf',configs=['settings/base.config'],profiles=[]),resources=dict(reads=dict(path=linux(inputs))),parameters=parameters,required_tools=[dict(name='awk',path='/usr/bin/awk')],outputs=[dict(path=output,kind='file',allow_empty=False)],require_tasks=mode not in ('pure-dataflow','empty-missing-output'),limits=dict(wall_seconds=120,cpu_seconds=120))
            if mode=='missing-tool':recipe['required_tools'].append(dict(name='missing_fixture_tool',path='/opt/omicsmatic-deliberately-missing-tool'))
            recipe_path=folder/'recipe.json';write(recipe_path,recipe);session=remote_family+'/'+mode
            expected=3 if mode in ('empty-requires-tasks','missing-runtime','missing-tool') else 2 if mode in ('task-failure','ignored-failure','empty-missing-output','retry-exhausted','missing-output') else 0
            home=remote+'/absent-runtime' if mode=='missing-runtime' else linux(setup/('home-'+suffix));report=linux(setup/('report-'+suffix))
            calls=[run(folder,'run',['--recipe',linux(recipe_path),'--home',home,'--setup-report',report,'--out',session],expected)]
            if expected==0:
                calls.append(run(folder,'verify',['--verify',session],0))
                if mode=='module-success':calls.append(run(folder,'resume',['--resume',session,'--home',home,'--setup-report',report],0));calls.append(run(folder,'verify-resume',['--verify',session],0))
            retain(session,folder);retained=folder/'retained';attempts=sorted((retained/'attempts').glob('[0-9]*')) if (retained/'attempts').exists() else []
            if expected:
                assert not list(retained.rglob('COMPLETED.json')),'Failed or skipped workflow admitted a completion marker'
                failure=json.loads(((attempts[-1] if attempts else retained)/'FAILURE.json').read_bytes());assert failure['exit_code']==expected
            else:
                attempt=attempts[-1];outputs=json.loads((attempt/'outputs.json').read_bytes());pin_output=outputs['members'][output]['pin'];raw=(attempt/'objects'/pin_output['sha256']).read_bytes();assert raw==(b'pure\n' if mode=='pure-dataflow' else EXPECTED),(version,mode,raw)
                complete=json.loads((attempt/'completion.json').read_bytes())
                if mode=='module-success':assert complete['stats']['cached']==4 and complete['stats']['succeeded']==0 and len(attempts)==2
                elif mode=='retry-success':assert complete['outcome']=='COMPLETE_AFTER_RETRIES' and complete['stats']['failed']==complete['stats']['retries']==1 and complete['stats']['succeeded']==4
                elif mode=='pure-dataflow':assert complete['outcome']=='EMPTY' and complete['trace']['taskRows']==0
                else:assert complete['stats']['succeeded']==4
                for platform,executable in [('windows-java21',JAVA21/'java.exe'),('windows-java25',Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'))]:
                    with (folder/(platform+'.stdout')).open('xb') as stdout,(folder/(platform+'.stderr')).open('xb') as stderr:checked=subprocess.run([str(executable),'-Xmx128m','-jar',str(jar),'nextflow-tools','--verify',str(attempt)],cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=120)
                    assert checked.returncode==0,(version,mode,platform)
            record=dict(version=version,mode=mode,expected_exit=expected,calls=calls,retained_attempts=len(attempts),passed=True);write(folder/'COMPLETED-CONTROL.json',record);records.append(record);print(json.dumps(dict(version=version,mode=mode,exit=expected,passed=True)),flush=True)
        return records
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=sum(list(pool.map(family,VERSIONS)),[])
    assert pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical and all(pin(ROOT/p)==value for p,value in sources.items())
    write(out/'COMPLETED.json',dict(status='passed-36-original-engine-typed-user-workflow-controls',cases=len(runs),runs=runs,artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=sources,owned_linux_root=remote,canonical_executable_unchanged=canonical,whole_paper_reproduced=False,biological_accuracy_certified=False,catalog_promoted=False,scope='Twelve declared workflow controls per exact engine. Reused tiny synthetic populations; no independent biological accuracy or whole-paper claim. Successful standalone attempts also verified from Windows Java 21/25. Failed/skipped dependent analyses retain no completed result.'))
if __name__=='__main__':
    try:main()
    except Exception as failure:print('FAIL2_NEXTFLOW_USER_WORKFLOW_MATRIX: '+str(failure)+'\nRequired action: preserve the owned Linux root and copied evidence, inspect the named command/output/trace discrepancy, and rerun to NEW paths only after correction.',file=sys.stderr);sys.exit(2)
