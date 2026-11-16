#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Windows/Linux packaged command qualification, preserving all numerical classes."""
from pathlib import Path
import argparse,hashlib,json,platform,shutil,subprocess,time,zipfile
ROOT=Path(__file__).resolve().parents[3]
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def save(p,obj):
    with p.open('x',encoding='utf-8') as f:json.dump(obj,f,indent=2);f.write('\n')
def classes(path):
    with zipfile.ZipFile(path) as z:return {n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
def files(root):return {p.relative_to(root).as_posix():pin(p) for p in root.rglob('*') if p.is_file()}
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',required=True,type=Path);p.add_argument('--previous-jar',required=True,type=Path);p.add_argument('--java',required=True,type=Path);p.add_argument('--original-bundle',required=True,type=Path);p.add_argument('--wsl-distro',required=True);p.add_argument('--out',required=True,type=Path);a=p.parse_args()
    if platform.system()!='Windows':raise FileNotFoundError('This combined Windows/WSL command profile needs its permitted Windows and WSL runtimes')
    out=a.out.absolute();out.mkdir(parents=True,exist_ok=False);jar=out/'candidate.jar';shutil.copyfile(a.jar,jar);assert pin(jar)==pin(a.jar)
    sources=['src/main/java/org/jlinalg/cli/JLinAlgCli.java','docs/literature/LDPRED2_COMMAND.md','tools/literature/ldpred2_command_validation/qualify.py'];sourcepins={n:pin(ROOT/n) for n in sources};save(out/'SOURCE-PINS.json',sourcepins)
    before,after=classes(a.previous_jar),classes(jar);changed=sorted(n for n in before if before[n]!=after.get(n));assert changed==['org/jlinalg/cli/JLinAlgCli.class'] and set(before)==set(after)
    save(out/'CLASS-CONTINUITY.json',dict(previous_classes=len(before),current_classes=len(after),changed=changed,all_numerical_and_worker_class_bytes_unchanged=True,previous_jar=pin(a.previous_jar),candidate=pin(jar)))
    work=out/'working-directory';work.mkdir();(work/'jlinalg.yaml').write_text('invalid: [\n')
    checks=[]
    def run(label,command,expected=0):
        command=list(map(str,command));start=time.monotonic()
        with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:r=subprocess.run(command,cwd=work,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=600)
        row=dict(label=label,command=command,exit_code=r.returncode,expected_exit_code=expected,seconds=round(time.monotonic()-start,3));save(out/(label+'.process.json'),row);checks.append(row);print(label,r.returncode,flush=True);assert r.returncode==expected,label
    base=[a.java,'-Xmx256m','-jar',jar];cli=base+['ldpred2-tools']
    run('malformed-generic-project-config',base+['--help'],2)
    run('root-help-without-generic-config',base+['--help','--no-config']);assert 'ldpred2-tools --help' in (out/'root-help-without-generic-config.stdout').read_text()
    run('normal-command-help',cli+['--help'])
    run('standalone-help',[a.java,'-Xmx256m','-cp',jar,'org.jlinalg.cli.LdPred2WorkflowCli','--help'])
    assert (out/'normal-command-help.stdout').read_bytes()==(out/'standalone-help.stdout').read_bytes()
    data=out/'data';data.mkdir()
    previous_data=ROOT/'build/git-publish/20261116-all-main-1/package-checks/data'
    for n in ['summary.tsv','variants.tsv','p.bin','i.bin','x.bin','models.tsv']:shutil.copyfile(previous_data/n,data/n)
    values=dict(engine='native',method='grid',summary=str(data/'summary.tsv'),ld_variants=str(data/'variants.tsv'),ld_pointers=str(data/'p.bin'),ld_rows=str(data/'i.bin'),ld_values=str(data/'x.bin'),models=str(data/'models.tsv'),genome_build='synthetic',ld_population='synthetic',seed='4096',burn_in='10',iterations='20',java_heap_mb='256')
    request=out/'native.properties';request.write_text(''.join(k+'='+v.replace('\\','/')+'\n' for k,v in values.items()))
    run('native-full-grid',cli+['--request',request,'--out',out/'native-grid'])
    prior=ROOT/'build/git-publish/20261116-all-main-1/package-checks/windows-grid/results';assert files(prior)==files(out/'native-grid/results')
    run('native-genuine-recomputation',cli+['--verify',out/'native-grid','--out',out/'native-replay'])
    retained=files(out/'native-grid');run('existing-output-refused',cli+['--request',request,'--out',out/'native-grid'],2);assert files(out/'native-grid')==retained
    assert not list(work.glob('*.config.yaml')) and not list(out.glob('*.config.yaml'))
    (data/'auto.tsv').write_text('model\tp_init\nchain1\t0.1\n')
    original={k:v for k,v in values.items() if k not in ['burn_in','iterations']};original.update(engine='original',method='auto',models=str(data/'auto.tsv'),heritability='0.3',runtime=str(out/'MISSING_RUNTIME'))
    original_request=out/'original.properties';original_request.write_text(''.join(k+'='+v.replace('\\','/')+'\n' for k,v in original.items()))
    run('windows-original-prerequisite-skip',cli+['--request',original_request,'--out',out/'windows-skipped'],3);assert not (out/'windows-skipped/COMPLETED.properties').exists()
    def linux(p):
        s=p.resolve().as_posix();return '/mnt/'+s[0].lower()+s[2:]
    command=['wsl.exe','-d',a.wsl_distro,'--','/usr/bin/timeout','--signal=TERM','--kill-after=10','500','/usr/bin/java','-Xmx256m','-jar',linux(jar),'ldpred2-tools','--verify',linux(a.original_bundle),'--out',linux(out/'linux-original-replay')]
    run('linux-original-genuine-recomputation',command)
    assert files(a.original_bundle/'results')==files(out/'linux-original-replay/results')
    assert sourcepins=={n:pin(ROOT/n) for n in sources}
    save(out/'COMPLETED.json',dict(status='PASS_PACKAGED_NORMAL_LDPRED2_COMMAND',checks=checks,native_full_grid_and_original_auto_genuine_replay=True,malformed_generic_configuration_bypassed_only_by_explicit_workflow_command=True,all_numerical_and_worker_class_bytes_unchanged=True,new_catalog_credit=0,whole_goal_complete=False));return 0
if __name__=='__main__':raise SystemExit(main())
