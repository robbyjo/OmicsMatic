"""Install the exact original Monocle3 1.0.0 into a NEW private library."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,time,traceback
ROOT=Path(__file__).resolve().parents[3]
ACTION=('SKIP3: dependent Monocle1.0.0 analysis skipped. Supply the exact permitted R4.3.3 dependency base, '
 'original GenomeInfoDbData library and pinned original source, or retain the skip if installation/execution '
 'is disallowed. Installation needs --allow-install and writes only NEW private paths. FAIL2: no complete '
 'runtime admitted; retain source, transcripts and receipts, correct the named integrity/build cause and retry '
 'NEW output. Never patch original scientific source or substitute another Monocle edition. Readiness alone '
 'does not qualify graph construction, the P021 modified test, historical dependencies or any paper analysis.')

def require(ok,message):
    if not ok:raise ValueError(message)
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def linux(path):
    path=path.resolve();require(path.drive.lower()=='d:','This WSL host installer requires a D: workspace')
    return '/mnt/d/'+path.relative_to(Path('D:/')).as_posix()
def census(root):return {path.relative_to(root).as_posix():pin(path) for path in sorted(root.rglob('*')) if path.is_file()}

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    for name in ['base','source-candidate','genome-library','out']:parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--allow-install',action='store_true');parser.add_argument('--distro',default='JLinAlg-Omics-SAIGE-20261026');args=parser.parse_args()
    out=args.out.resolve();require(out.is_relative_to(ROOT/'build') and out!=ROOT/'build','Use NEW output under this checkout build directory');out.mkdir(parents=True,exist_ok=False)
    record=dict(status='SKIP3_MONOCLE100_RUNTIME_UNAVAILABLE',runtime_admitted=False,scientific_analysis_admitted=False,required_action=ACTION);before=None;protected={}
    try:
        if not args.allow_install:raise PermissionError('Installation was not selected with --allow-install')
        base=args.base.resolve();candidate=args.source_candidate.resolve();genome=args.genome_library.resolve();prefix=base/'.pixi/envs/default';library=prefix/'lib/R/library'
        base_record=json.loads((base/'RESULT.json').read_bytes());require(base_record['status']=='INSTALLED_PRIVATE_R43_MONOCLE131_DEPENDENCY_BASE_NOT_133_ANALYSIS','Different exact dependency base')
        locked=json.loads(Path(__file__).with_name('source-profile.json').read_bytes());source=json.loads((candidate/'SOURCE.json').read_bytes())
        require(source['version']==locked['version']=='1.0.0' and source['commit']==locked['commit']=='004c096f3a3bdaf1e6709a65fb36f40ba775f1ab','Different selected source version/commit')
        require(source['archive']==locked['archive'] and source['source_members']==locked['source_members'],'Different pinned complete original source')
        require(pin(candidate/'monocle3-source.tar.gz')==locked['archive'] and census(candidate/'source')==locked['source_members'],'Changed original source/archive')
        require((genome/'GenomeInfoDbData/DESCRIPTION').is_file(),'Missing original GenomeInfoDbData library')
        protected={'dependency-library':library,'genome-package':genome/'GenomeInfoDbData','original-source':candidate/'source'}
        before={name:census(root) for name,root in protected.items()};save(out/'PROTECTED-BEFORE.json',dict(roots={name:str(root) for name,root in protected.items()},files=before))
        base_pins={str(path):pin(path) for path in [base/'pixi.lock',prefix/'bin/Rscript',prefix/'bin/R']}
        lib=out/'R-lib';lib.mkdir();shutil.copytree(genome/'GenomeInfoDbData',lib/'GenomeInfoDbData')
        stage=out/'source-stage/monocle3';stage.mkdir(parents=True)
        for name,wanted in locked['source_members'].items():
            target=(stage/name).resolve();require(target.is_relative_to(stage),'Unsafe source path');target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(candidate/'source'/name,target);require(pin(target)==wanted,'Changed staged source')
        for name in ['home','tmp','cache']:(out/name).mkdir()
        environment=['/usr/bin/env','PATH='+linux(prefix/'bin')+':/usr/bin:/bin','R_HOME='+linux(prefix/'lib/R'),'LD_LIBRARY_PATH='+linux(prefix/'lib'),'HOME='+linux(out/'home'),'TMPDIR='+linux(out/'tmp'),'R_USER_CACHE_DIR='+linux(out/'cache'),'R_LIBS='+linux(lib),'R_LIBS_USER='+linux(lib),'R_LIBS_SITE=','R_ENVIRON_USER=/dev/null','R_PROFILE_USER=/dev/null','R_MAKEVARS_USER=/dev/null','OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1','MKL_NUM_THREADS=1','LANG=C','LC_ALL=C']
        def run(name,arguments,timeout):
            command=['wsl.exe','-d',args.distro,'--user','root','--exec','/usr/bin/timeout','--signal=TERM','--kill-after=15',str(timeout),*environment,*arguments];started=time.time()
            save(out/(name+'.STARTED.json'),dict(command=command,started_unix=started))
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=timeout+60)
            save(out/(name+'.PROCESS.json'),dict(command=command,exit_code=process.returncode,elapsed_seconds=time.time()-started));print(name,process.returncode,flush=True)
            if process.returncode:raise RuntimeError('Original runtime step failed: '+name+'; read retained stdout/stderr')
        save(out/'STARTED.json',dict(source_profile=pin(Path(__file__).with_name('source-profile.json')),setup_source=pin(Path(__file__)),base_pins=base_pins,source_commit=locked['commit'],private_library=linux(lib),source_unmodified=True,scientific_analysis_admitted=False))
        run('install-original-monocle100',[linux(prefix/'bin/R'),'CMD','INSTALL','--library='+linux(lib),linux(stage)],1200)
        run('check-original-namespace',[linux(prefix/'bin/Rscript'),'--vanilla',linux(Path(__file__).with_name('check_original.R')),linux(out),linux(lib),linux(library),linux(stage)],180)
        ready=json.loads((out/'READINESS.json').read_bytes());require(ready['status']=='PASS_EXACT_ORIGINAL_MONOCLE100_NAMESPACE_NOT_ANALYSIS','Readiness gate failed')
        require(all(pin(Path(name))==wanted for name,wanted in base_pins.items()),'Changed dependency executable/lock')
        record.update(status='READY_EXACT_ORIGINAL_MONOCLE100_NAMESPACE_NOT_ANALYSIS',runtime_admitted=True,readiness=pin(out/'READINESS.json'),rscript=linux(prefix/'bin/Rscript'),libraries=[linux(lib),linux(library)],source_root=linux(stage),source_members=locked['source_members'],installed_members=census(lib),base_pins=base_pins)
    except Exception as error:
        record.update(cause=repr(error),traceback=traceback.format_exc())
        if not isinstance(error,(FileNotFoundError,PermissionError)):record['status']='FAIL2_MONOCLE100_RUNTIME_NOT_ADMITTED'
    finally:
        if before is not None:
            after={name:census(root) for name,root in protected.items()};save(out/'PROTECTED-AFTER.json',dict(roots={name:str(root) for name,root in protected.items()},files=after));record['preexisting_protected_files_unchanged']=before==after
            if before!=after:record.update(status='FAIL2_PROTECTED_PREDECESSOR_CHANGED',runtime_admitted=False)
    save(out/'RESULT.json',record);(out/'WARNINGS.md').write_text('# '+record['status']+'\n\n'+record.get('cause','')+'\n\n'+ACTION+'\n',encoding='utf-8',newline='\n');print(record['status'],record.get('cause',''),ACTION,flush=True)
    return 0 if record['runtime_admitted'] else 3 if record['status'].startswith('SKIP3') else 2

if __name__=='__main__':
    require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');raise SystemExit(main())
