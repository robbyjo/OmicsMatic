# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create a separate pinned scFates kit; never change the frozen arithmetic or Palantir sources."""
import argparse,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);base=ROOT/'build/literature-review/scfates-runtime';parent=ROOT/'src/main/resources/org/jlinalg/omics/palantir-workflow';target=ROOT/'src/main/resources/org/jlinalg/omics/scfates-workflow';target.mkdir(parents=True,exist_ok=True);source=json.loads((base/'source-intake-1/SOURCE.json').read_bytes());assert source['status']=='PASS_OFFICIAL_SCFATES_SIMPLEPPT_SOURCE_ONLY';setup=json.loads((base/'reference-setup-1/COMPLETED.json').read_bytes());assert setup['status']=='PASS_COMPLETE_PRIVATE_ORIGINAL_SCFATES_SIMPLEPPT_IMPORTS_ONLY';locks=json.loads((base/'reference-setup-1/dependency-locks.json').read_bytes());assert len(locks['archives'])==50;captured={}
    def write(name,raw):
        path=target/name;path.parent.mkdir(parents=True,exist_ok=True)
        if path.exists():assert path.read_bytes()==raw,'Existing new kit member protected: '+name
        else:path.write_bytes(raw)
        captured[name]=pin(path)
    write('.gitattributes',b'* -text\n')
    write('setup/inventory.py',(ROOT/'tools/literature/inventory_hail_runtime_linux.py').read_bytes())
    supervisor=(parent/'supervisor.py').read_text(encoding='utf8').replace('Palantir','scFates').replace('PALANTIR','SCFATES');old="NUMEXPR_NUM_THREADS='1')";assert supervisor.count(old)==1;supervisor=supervisor.replace(old,"NUMEXPR_NUM_THREADS='1',NUMBA_NUM_THREADS='1',MPLBACKEND='Agg',JAX_PLATFORMS='cpu')");write('supervisor.py',supervisor.encode())
    prepare=(parent/'setup/prepare.py').read_text(encoding='utf8').replace('Palantir','scFates').replace('PALANTIR','SCFATES')
    start=prepare.index("        dependencies=prefix/'dependencies';")
    end=prepare.index('        initial=inventory(prefix,',start)
    prepare=prepare[:start]+"        dependencies=prefix/'dependencies';execute('install-exact-dependencies',[python,'-I','-B','-m','pip','install','--no-index','--no-deps','--no-compile','--target',dependencies,*locked])\n"+prepare[end:]
    prepare=prepare.replace('for cache in a.archive_cache:',"for cache in [kit/'original',*a.archive_cache]:")
    prepare=prepare.replace('optional GAM/FCS extras are not installed.','optional R/GAM, GPU and unrelated scFates analyses are not admitted.')
    write('setup/prepare.py',prepare.encode())
    for package,value in source['packages'].items():
        wheel=base/'source-intake-1'/package/Path(value['wheel']['path']).name if 'path' in value['wheel'] else None
        candidates=list((base/'source-intake-1'/package).glob('*.whl'));assert len(candidates)==1;wheel=candidates[0];assert pin(wheel)=={k:value['wheel'][k] for k in ['bytes','sha256']};write('original/'+wheel.name,wheel.read_bytes())
        for name in value['license_members']:write('original/'+package+'/'+name,(base/'source-intake-1'/package/'wheel-source'/name).read_bytes())
    original=json.loads((parent/'profiles.json').read_bytes())
    profile=dict(schema_version=1,edition='scfates-1.2.5_simpleppt-1.1.7',scfates_version='1.2.5',simpleppt_version='1.1.7',packages={name:dict(version=v['version'],revision=v['revision'],wheel=v['wheel'],members=v['wheel_members']) for name,v in source['packages'].items()},dependency_archives=[{k:v for k,v in row.items() if k!='reused_verified_cache'} for row in locks['archives']],base_profile=original['base_profile'])
    # New authored worker/health/docs must exist before sealing their profile.
    required=['worker.py','codec.py','setup/health.py','SETUP.md','NOTICE.txt']
    for name in required:assert (target/name).is_file(),'Write the new member first: '+name
    worker_names=['worker.py','codec.py','supervisor.py']
    profile['files']={p.relative_to(target).as_posix():pin(p) for p in sorted(target.rglob('*')) if p.is_file() and p.relative_to(target).as_posix() not in ['profiles.json','worker-profile.json',*worker_names]}
    save(target/'profiles.json',profile);save(target/'worker-profile.json',dict(schema_version=1,files={n:pin(target/n) for n in worker_names}))
    for path in [Path(__file__).resolve(),*sorted(p for p in target.rglob('*') if p.is_file())]:snapshot=out/'source-snapshot'/path.relative_to(ROOT);snapshot.parent.mkdir(parents=True,exist_ok=True);snapshot.write_bytes(path.read_bytes())
    save(out/'COMPLETED.json',dict(status='PASS_PINNED_ORIGINAL_SCFATES_WORKFLOW_KIT_SOURCES_ONLY',profile=pin(target/'profiles.json'),source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),*sorted(p for p in target.rglob('*') if p.is_file())]},implementation_credit=False,whole_goal_complete=False));print('Prepared',len(profile['files']),'runtime and',len(worker_names),'worker members; no runtime or analysis executed')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable pinned source checks')
    main()
