#!/usr/bin/env python3
"""Verify public generated recipes, native audits and exact-population originals.

Linux x86_64 only. Uses already admitted homes/operations; never installs,
downloads, mutates a home or patches original scientific code. Missing cases
are skipped independently. All failed comparisons remain at NEW output paths.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import admit_homer_generated as generated

ROOT=Path(__file__).resolve().parents[2]
CASES=('5.1:generated-old','4.11:generated-old','4.11.1:generated-old','5.1:generated-new')
RECOVERY=('Required action: inspect verification.json,progress.json,each analysis WARNINGS.md and '
    'session.json,and all original/replay diagnostics. Preserve failed outputs;correct the named '
    'input,dependency,capacity or comparison cause and use NEW verification paths. '
    'A failed/skipped case is not a pass. '+generated.RECOVERY)


def require(condition,message):
    if not condition:raise ValueError(str(message))


def run(command,home,work,label):
    env=generated.genome.environment(home,work);env.update(LANG='C.UTF-8',LC_ALL='C.UTF-8',OMP_NUM_THREADS='1')
    command=list(map(str,command))
    if label=='java':
        stdout,stderr=work/(label+'.stdout'),work/(label+'.stderr')
        with stdout.open('xb') as out,stderr.open('xb') as err:
            child=subprocess.Popen(command,cwd=work,env=env,stdout=out,stderr=err,start_new_session=True)
            try:code=child.wait(timeout=600)
            except BaseException:
                try:os.killpg(child.pid,signal.SIGKILL)
                except ProcessLookupError:pass
                child.wait(timeout=10);raise
        require(code==0,('Java generated analysis exited',code,str(stderr)))
        size=sum(p.stat().st_size for p in work.rglob('*') if p.is_file())
        require(size<=2147483648,'Post-command verification workspace exceeds2GiB')
        return dict(command=command,exit_code=code,stdout_sha256=generated.genome.core.sha(stdout),
            stderr_sha256=generated.genome.core.sha(stderr),workspace_bytes_after_command=size,
            monitoring='Java own process/workspace bounds;timeout120;outer600;workspace walk after publication')
    try:return generated.genome.execute(command,home,work,label,seconds=600,maximum=2147483648,build_env=env)
    except generated.genome.core.Unavailable as failure:raise ValueError('Original verification call failed: '+str(failure)) from failure


def protocol(args,out,jar,home,inputs,variant,version,mode):
    work=out/(version+'-'+mode+'-'+variant);work.mkdir();analysis=work/'analysis';options=json.loads((inputs/(variant+'.json')).read_text());phase=options.get('analysis','both');known=phase!='denovo';discovery=phase!='known'
    java=run([args.java,'-Xmx1536m','-jar',jar,'homer-tools','genome','--options-file',inputs/(variant+'.json'),
        '--home',home,'--profile','linux-x86_64-genome-'+version,'--out',analysis,'--timeout','120'],home,work,'java')
    session=json.loads((analysis/'session.json').read_text())
    require(session['requested_analysis']==phase and session['generated_coordinate_audit']=='passed-complete-native-selected-population','Missing complete generated/phase audit')
    require(session['known_analysis']==('completed' if known else 'not-requested') and session['discovery_analysis']==('completed' if discovery else 'not-requested'),'Wrong requested phase statuses')
    require((session['known_motifs']==2 if known else session['known_motifs'] is None) and (session['learned_motifs']>=1 if discovery else session['learned_motifs'] is None),'Incomplete synthetic requested families')
    require((analysis/'results.tsv').exists()==known and (analysis/'discovery-results.tsv').exists()==discovery,'Unrequested/missing result tables')
    require('Required action:' in (analysis/'WARNINGS.md').read_text(),'Required action omitted')
    # Fixed-population replays are independent original invocations. They keep
    # original order, names and weights, and do not redraw a different contrast.
    replay=work/'exact-population-original';replay.mkdir();calls=[];comparisons={}
    for name in ['seq.tsv','group.adj']:shutil.copy2(analysis/'original-out'/name,replay/name)
    if known:
        shutil.copy2(analysis/'stage-known.motif',replay/'known.motif')
        command=['/usr/bin/perl',home/'bin/findKnownMotifs.pl','-s','seq.tsv','-g','group.adj','-o','known-out','-pvalue','0.01','-m','known.motif','-homer2','-p','1','-stat',options['statistic'].replace('hypergeometric','hypergeo'),'-cache','8']
        if session['known_strands_effective']=='FORWARD':command.extend(['-strand','+'])
        calls.append(run(command,home,replay,'known-reference'));comparisons['knownResults.txt']=generated.genome.core.sha(replay/'known-out/knownResults.txt')==generated.genome.core.sha(analysis/'original-out/knownResults.txt')
        require(comparisons['knownResults.txt'],'Exact-population original known table differs')
    if discovery:
        command=[home/'bin/homer2','denovo','-s','seq.tsv','-g','group.adj','-S','1','-stat',options['statistic'].replace('hypergeometric','hypergeo'),'-mis','1','-p','1','-cache','8','-minlp','-10','-len','8','-o','motifs8']
        if options['strands']=='forward':command.extend(['-strand','+'])
        calls.append(run(command,home,replay,'denovo-reference'));comparisons['homerMotifs.motifs8']=generated.genome.core.sha(replay/'motifs8')==generated.genome.core.sha(analysis/'original-out/homerMotifs.motifs8')
        require(comparisons['homerMotifs.motifs8'],'Exact-population original de novo model differs')
        shutil.copy2(replay/'motifs8',replay/'homerMotifs.all.motifs');shutil.copy2(analysis/'stage-comparison.motif',replay/'comparison.motif')
        calls.append(run(['/usr/bin/perl',home/'bin/compareMotifs.pl','homerMotifs.all.motifs','./','-reduceThresh','0.6','-matchThresh','T10','-known','comparison.motif','-nofacts','-cpu','1'],home,replay,'comparison-reference'))
        comparisons['homerMotifs.all.motifs']=generated.genome.core.sha(replay/'homerMotifs.all.motifs')==generated.genome.core.sha(analysis/'original-out/homerMotifs.all.motifs')
        require(comparisons['homerMotifs.all.motifs'] and bool(list((replay/'homerResults').glob('*.svg'))),'Exact-population original comparison models/SVGs differ or missing')
    direct=work/'fresh-original-draw';direct.mkdir()
    for name in ['stage-genome.fa','stage-targets.pos',*(['stage-known.motif'] if known else []),*(['stage-comparison.motif'] if discovery else [])]:shutil.copy2(analysis/name,direct/name)
    original=session['process_original']['command'];require(original[:2]==['/usr/bin/perl',str(home/'bin/findMotifsGenome.pl')],'Original wrapper differs')
    fresh=run(original,home,direct,'original-reference');stderr=(direct/'original-reference.stderr').read_text()
    require('Job finished' in stderr and 'Finished preparing sequence/group files' in stderr and not any(x in stderr for x in ['Illegal division','not found','uninitialized','Could not open',"Can't open"]),'Fresh original draw did not finalize cleanly')
    if known:require(len((direct/'original-out/knownResults.txt').read_text().splitlines())==3,'Fresh original known family incomplete')
    if discovery:require((direct/'original-out/homerResults.html').is_file() and bool(list((direct/'original-out/homerResults').glob('*.svg'))),'Fresh original discovery/comparison report incomplete')
    return dict(status='passed',version=version,mode=mode,variant=variant,analysis=phase,java=java,
        known_motifs=session['known_motifs'],learned_motifs=session['learned_motifs'],
        requested_background=session['generated_requested_background'],actual_background=session['generated_selected_background'],
        exact_population_original_calls=calls,exact_population_original_file_matches=comparisons,fresh_original_draw_call=fresh,
        fresh_draw_scope='Independent unseeded draw;no equality of selected backgrounds,counts or models is asserted across different draws.')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--java',default='/usr/bin/java');parser.add_argument('--case',action='append',choices=CASES)
    for version in generated.genome.LOCK['profiles'].values():
        v=version['version'];parser.add_argument('--home-'+v,dest='home_'+v,type=Path);parser.add_argument('--old-admission-'+v,dest='old_'+v,type=Path)
    parser.add_argument('--new-admission-5.1',dest='new_5.1',type=Path);args=parser.parse_args();owns=False;rows=[];record={};out=None;code=0
    try:
        out=generated.genome.core.private(args.out);require(not out.exists(),'Verification directory already exists;choose NEW paths');out.mkdir(parents=True);owns=True
        jar=args.jar.resolve(strict=True);require(jar.is_file(),'Missing packaged JAR');jar_hash=generated.genome.core.sha(jar);host=generated.genome.core.host()
        generator=ROOT/'examples/homer-generated-workflow/prepare_inputs.py';sources={str(p.relative_to(ROOT)):generated.genome.core.sha(p) for p in [generator,generator.with_name('README.md'),Path(__file__)]}
        spec=importlib.util.spec_from_file_location('generated_recipes',generator);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        for case in args.case or CASES:
            version,mode=case.split(':');home_value=vars(args)['home_'+version];admission_value=vars(args)[('old_' if mode=='generated-old' else 'new_')+version]
            try:
                if home_value is None or admission_value is None:raise generated.genome.core.Unavailable('Missing explicit home/admission for '+case)
                home=generated.genome.core.private(home_value);admission=generated.genome.core.private(admission_value);generated.verify(admission,home,'linux-x86_64-genome-'+version,mode,host)
                pins={str(p):generated.genome.core.sha(p) for p in [home/generated.genome.MARKER,admission/generated.MARKER]}
            except (generated.genome.core.Unavailable,FileNotFoundError,PermissionError) as failure:
                rows.append(dict(case=case,status='skipped',exit_code=3,cause=str(failure)));(out/'progress.json').write_text(json.dumps(rows,indent=2)+'\n');continue
            inputs=module.prepare(out/('inputs-'+version+'-'+mode),version,mode,admission)
            for variant in module.OLD_VARIANTS if mode=='generated-old' else module.NEW_VARIANTS:
                try:
                    row=protocol(args,out,jar,home,inputs,variant,version,mode);generated.verify(admission,home,'linux-x86_64-genome-'+version,mode,host);require(all(generated.genome.core.sha(Path(p))==h for p,h in pins.items()),'Runtime/admission changed during verification')
                except Exception as failure:row=dict(status='failed-retained',exit_code=2,version=version,mode=mode,variant=variant,cause=repr(failure))
                rows.append(row);(out/'progress.json').write_text(json.dumps(rows,indent=2)+'\n');print(json.dumps({k:v for k,v in row.items() if k in ('status','version','mode','variant','cause','actual_background')}),flush=True)
        require(generated.genome.core.sha(jar)==jar_hash and all(generated.genome.core.sha(ROOT/p)==h for p,h in sources.items()),'Verification sources or packaged JAR changed')
        code=2 if any(r['status']=='failed-retained' for r in rows) else 3 if any(r['status']=='skipped' for r in rows) else 0
        record=dict(status='passed-public-generated-workflows' if code==0 else 'failed-retained' if code==2 else 'skipped-some-generated-workflows',candidate_sha256=jar_hash,source_sha256=sources,protocols=rows,
            scope='Actual public Java workflows with complete native population audits,exact-population original known/discovery/comparison replays,and separate fresh original unseeded draws. Technical synthetic validation only;no full-paper or biological equivalence.')
    except (generated.genome.core.Unavailable,FileNotFoundError,PermissionError) as failure:code=3;record=dict(status='skipped-unavailable-verification-dependency',cause=str(failure),protocols=rows)
    except Exception as failure:code=2;record=dict(status='failed-retained',cause=repr(failure),protocols=rows)
    record.update(exit_code=code,required_action=RECOVERY)
    if owns:
        (out/'verification.json').write_text(json.dumps(record,indent=2)+'\n');(out/'WARNINGS.md').write_text('# HOMER GENERATED '+('VERIFICATION PASSED WITH REVIEW WARNINGS' if code==0 else 'VERIFICATION SKIPPED' if code==3 else 'VERIFICATION FAILED')+'\n\n'+json.dumps([r for r in rows if r['status']!='passed'],indent=2)+'\n\n'+RECOVERY+'\n')
    print(json.dumps({k:v for k,v in record.items() if k not in ('protocols','source_sha256')}),file=sys.stderr if code else sys.stdout);return code


if __name__=='__main__':raise SystemExit(main())
