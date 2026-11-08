# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the entire unchanged original RegTools clustering script on every literal case. No Stan/model or full-tool credit."""
import argparse,gzip,json,os,platform,subprocess,sys,time,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--intake',type=Path,required=True);p.add_argument('--fixtures',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_ORIGINAL_LEAFCUTTER_CLUSTER_CONTROLS_INCOMPLETE',implementation_credit=False,whole_goal_complete=False,namespace_patched=False);sources={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))};(out/'collector-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    try:
        intake=json.loads((a.intake/'COMPLETED.json').read_bytes());original=intake['editions']['source-2c9907ef66ad'];source=a.intake/'source-2c9907ef66ad/source/clustering/leafcutter_cluster_regtools.py';source=source.resolve();wanted={k:original['members']['clustering/leafcutter_cluster_regtools.py'][k] for k in ['bytes','sha256']};assert pin(source)==wanted;fixture_path=a.fixtures.resolve()/'cases.json';fixture_raw=fixture_path.read_bytes();fixture=json.loads(fixture_raw);(out/'cases.json').write_bytes(fixture_raw);reports=[];processes=[]
        for case in fixture['cases']:
            home=out/case['id'];home.mkdir();inputs=home/'inputs';inputs.mkdir();destination=home/'original';destination.mkdir();tmp=home/'owned-temp';tmp.mkdir();paths=[]
            for sample in case['samples']:
                assert sample['id'] and '/' not in sample['id'] and '\\' not in sample['id'] and '.junc' not in sample['id'];file=inputs/(sample['id']+'.junc');file.write_text('\n'.join(sample['lines'])+'\n' if sample['lines'] else '',encoding='utf8',newline='\n');paths.append(file.relative_to(home).as_posix())
            (home/'junctions.txt').write_text('\n'.join(paths)+'\n',encoding='utf8',newline='\n');op=case['options'];command=[sys.executable,'-I','-B','-X','utf8',str(source),'-j','junctions.txt','-o','leaf','-r','original','-l',str(op['maximum_intron_length']),'-m',str(op['minimum_reads']),'-p',str(op['minimum_ratio'])]
            if not op['check_chromosomes']:command+=['-k','1']
            env=dict(os.environ,TMPDIR=str(tmp),TEMP=str(tmp),TMP=str(tmp));started=time.monotonic();child=None
            with (home/'original.stdout').open('xb') as stdout,(home/'original.stderr').open('xb') as stderr:
                try:
                    child=subprocess.Popen(command,cwd=home,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,**({'creationflags':subprocess.CREATE_NO_WINDOW} if os.name=='nt' else {}));exit_code=child.wait(timeout=30)
                finally:
                    if child is not None and child.poll() is None:child.kill();child.wait(timeout=10)
            process=dict(command=command,exit_code=exit_code,pid=child.pid,owned_live_after_cleanup=[],elapsed_seconds=time.monotonic()-started,source_has_no_child_process_launch=True);save(home/'original.process.json',process);assert exit_code==0,'Original script failed '+case['id'];required=['leaf_pooled','leaf_refined','leaf_sortedlibs','leaf_perind.counts.gz','leaf_perind_numers.counts.gz'];assert all((destination/n).is_file() for n in required),'Original zero exit did not produce complete required output family';decoded={}
            for name in required:
                raw=gzip.decompress((destination/name).read_bytes()) if name.endswith('.gz') else (destination/name).read_bytes();(home/(name+'.decoded')).write_bytes(raw);decoded[name]=dict(raw=pin(destination/name),decoded=pin(home/(name+'.decoded')))
            ratios=(home/'leaf_perind.counts.gz.decoded').read_text(encoding='utf8').splitlines();assert ratios[0].split()==['chrom',*[s['id'] for s in case['samples']]] and all(len(line.split())==len(case['samples'])+1 for line in ratios[1:]);reports.append(dict(id=case['id'],rows=len(ratios)-1,refined_clusters=len((home/'leaf_refined.decoded').read_text().splitlines()),output_families=decoded));processes.append(process);print(case['id'],'complete original rows',len(ratios)-1,flush=True)
        assert fixture_path.read_bytes()==fixture_raw and pin(source)==wanted and all(pin(ROOT/n)==v for n,v in sources.items());state.update(status='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_CLUSTER_CONTROLS_ONLY',source_pins=sources,original=dict(revision=original['revision'],path=source.relative_to(ROOT).as_posix(),**wanted),fixtures=pin(fixture_path),cases=reports,processes=processes,python=sys.version,python_executable=sys.executable,platform=platform.platform(),analysis_installed_software=False)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    action='Required action: do not admit a partial control matrix or count LeafCutter as implemented. Preserve original scripts, inputs, stdout/stderr, counts and failed receipts; correct the named input/source/process/capacity cause and retry NEW output paths. Missing or forbidden required runtime dependencies keep the dependent analysis skipped. This component performs no phenotype/PCA/Stan/differential-splicing analysis.';save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Complete original clustering/counts only; full LeafCutter workflow not admitted.')+'\n\n'+action+'\n',encoding='utf8',newline='\n');print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original/source checks')
    raise SystemExit(main())
