#!/usr/bin/env python3
"""Run the public supplied-background genomic recipes and fresh original references.

Linux x86_64 only. Requires three already admitted HOMER genomic homes and the
development JAR. Never installs, downloads, edits a home or substitutes a version.
All outputs, commands and failed comparisons are retained in a NEW directory.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import signal
import shutil
import subprocess
import sys
import install_homer_genome as genome

ROOT = Path(__file__).resolve().parents[2]
VERSIONS = ('5.1','4.11','4.11.1')
RECOVERY = ('Required action: inspect verification.json, progress.json, each analysis WARNINGS.md, '
    'session.json and java/reference diagnostics. Correct the named input, runtime or comparison '
    'cause and rerun to a NEW verification directory. Preserve partial results; a failed/skipped '
    'verification is not a pass. '+genome.RECOVERY)


def require(condition, context):
    if not condition:
        raise ValueError('Genomic workflow verification differs: '+repr(context))


def verify(args, out, homes, jar):
    generator = ROOT/'examples/homer-genome-workflow/prepare_inputs.py'
    recipe = generator.with_name('README.md')
    sources = {str(p.relative_to(ROOT)):genome.core.sha(p) for p in [generator,recipe,Path(__file__)]}
    spec = importlib.util.spec_from_file_location('homer_genome_examples',generator)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    inputs = module.prepare(out/'inputs')
    hashes = {p.name:genome.core.sha(p) for p in inputs.iterdir()}
    jar_hash = genome.core.sha(jar)
    calls, rows = [], []

    def run(command, home, work, label):
        env = genome.environment(home,work)
        env.update(LANG='C.UTF-8',LC_ALL='C.UTF-8',OMP_NUM_THREADS='1')
        try:
            if label=='java':
                # A live rglob/scandir monitor can hold DrvFs directory handles
                # while Java atomically publishes staging. Java already enforces
                # its own workspace/RSS/process bounds; inspect disk after exit.
                stdout,stderr=work/(label+'.stdout'),work/(label+'.stderr')
                with stdout.open('xb') as out_stream,stderr.open('xb') as err_stream:
                    process=subprocess.Popen(list(map(str,command)),cwd=work,env=env,
                        stdout=out_stream,stderr=err_stream,start_new_session=True)
                    try:code=process.wait(timeout=600)
                    except BaseException:
                        try:os.killpg(process.pid,signal.SIGKILL)
                        except ProcessLookupError:pass
                        process.wait(timeout=10)
                        raise
                if code:raise ValueError('Genomic Java command exited '+str(code)+'; inspect '+str(stderr))
                size=sum(path.stat().st_size for path in work.rglob('*') if path.is_file())
                require(size<=2147483648,'post-command verification workspace capacity')
                call=dict(command=list(map(str,command)),exit_code=code,
                    stdout_sha256=genome.core.sha(stdout),stderr_sha256=genome.core.sha(stderr),
                    workspace_bytes_after_command=size,
                    monitoring='Java own resource monitors and explicit timeout120;outer timeout600;workspace walk only after atomic publication')
            else:
                call = genome.execute(list(map(str,command)),home,work,label,seconds=600,
                                      maximum=2147483648,build_env=env)
        except genome.core.Unavailable as failure:
            # Dependencies were admitted before execution. A failed verification
            # command must not turn an observed scientific failure into a pass/skip.
            raise ValueError('Verification command failed or timed out: '+str(failure)) from failure
        calls.append(call)
        return call

    def groups(path):
        values = {}
        for line in path.read_text().splitlines():
            f = line.split('\t'); require(f[0] not in values,('duplicate group',f[0]))
            values[f[0]] = (f[1],float(f[2]) if len(f)>2 else 1.0)
        return values

    for version in VERSIONS:
        home = homes[version]
        for variant in module.VARIANTS:
            work = out/(version+'-'+variant); work.mkdir()
            analysis = work/'analysis'
            command = [args.java,'-Xmx1536m','-jar',jar,'homer-tools','genome',
                '--options-file',inputs/(variant+'.json'),'--home',home,
                '--profile','linux-x86_64-genome-'+version,'--out',analysis,'--timeout','120']
            call = run(command,home,work,'java')
            session = json.loads((analysis/'session.json').read_text())
            options=json.loads((inputs/(variant+'.json')).read_text())
            phase=options.get('analysis','both');known=phase!='denovo';discovery=phase!='known'
            expected='completed-original-genomic-known-and-denovo-with-review-warnings' if phase=='both' else 'completed-original-genomic-'+phase+'-with-review-warnings'
            require(session['status']==expected,(version,variant,session['status']))
            require(session['requested_analysis']==phase and session['known_analysis']==('completed' if known else 'not-requested') and session['discovery_analysis']==('completed' if discovery else 'not-requested'),'explicit phase status')
            require((session['known_motifs']==2 if known else session['known_motifs'] is None) and (session['learned_motifs']>=1 if discovery else session['learned_motifs'] is None),(version,variant,'complete requested motif families'))
            require((analysis/'results.tsv').exists()==known and (analysis/'discovery-results.tsv').exists()==discovery,'no unrequested result tables')
            direct = work/'direct-original'; direct.mkdir()
            for name in ['stage-genome.fa','stage-targets.pos','stage-background.pos',*(['stage-known.motif'] if known else []),*(['stage-comparison.motif'] if discovery else [])]:
                shutil.copy2(analysis/name,direct/name)
            original = session['process_original']['command']
            require(original[:2]==['/usr/bin/perl',str(home/'bin/findMotifsGenome.pl')],'exact original wrapper')
            reference_call = run(original,home,direct,'reference')
            actual, reference = analysis/'original-out',direct/'original-out'
            compared = {name:genome.core.sha(actual/name)==genome.core.sha(reference/name) for name in
                ['seq.tsv',*(['knownResults.txt'] if known else []),*(['homerMotifs.motifs8','homerMotifs.all.motifs'] if discovery else [])]}
            require(all(compared.values()),(version,variant,compared))
            require(groups(actual/'group.adj')==groups(reference/'group.adj'),(version,variant,'normalized groups'))
            for folder in [actual,reference]:
                require(all((folder/n).is_file() and (folder/n).stat().st_size>0 for n in [*(['knownResults.html'] if known else []),*(['homerResults.html'] if discovery else [])]),'complete requested HTML reports')
                if discovery:require(bool(list((folder/'homerResults').glob('*.svg'))),'original comparison SVGs')
                require((folder/'knownResults.txt').exists()==known and (folder/'homerResults.html').exists()==discovery,'original omitted only unrequested phases')
            dispositions = {}
            for line in (analysis/'population.tsv').read_text().splitlines()[1:]:
                key=line.split('\t')[3]; dispositions[key]=dispositions.get(key,0)+1
            rows.append(dict(version=version,variant=variant,analysis=phase,exit_code=0,known_motifs=session['known_motifs'],
                learned_motifs=session['learned_motifs'],analytic_records=session['analytic_records'],
                dispositions=dispositions,exact_original_files=compared,normalized_groups_equal=True,
                java_call=call,reference_call=reference_call))
            genome.core.write(out/'progress.json',rows)
            print(json.dumps(dict(version=version,variant=variant,status='passed')),flush=True)
    require(hashes=={p.name:genome.core.sha(p) for p in inputs.iterdir()},'unchanged generated inputs/options')
    require(jar_hash==genome.core.sha(jar),'unchanged JAR')
    require(sources=={n:genome.core.sha(ROOT/n) for n in sources},'unchanged generator/recipe/verifier')
    return dict(status='passed-fifteen-public-genomic-recipes-and-fifteen-fresh-original-references',
        candidate_sha256=jar_hash,source_sha256=sources,input_sha256=hashes,protocols=rows,
        runtime_bindings=3,calls=calls,
        scope='Synthetic supplied-background old algorithm only. Exact staged-input original requested known tables, sequence/motif bytes and normalized group maps; native production population/statistic audits. Three versions times five recipes: three both-phase protocols, one known-only and one de novo-only with comparison/SVG. Unrequested phases have explicit status, null counts and no result tables. No generated-background, optimizer reconstruction, historical paper dataset or biological calibration claim.')


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    for version in VERSIONS:
        parser.add_argument('--home-'+version,type=Path,required=True,dest='home_'+version)
    parser.add_argument('--java',default=shutil.which('java'))
    args=parser.parse_args(argv);owns=False;code=0;record={};out=None;phase='dependencies'
    try:
        out=genome.core.private(args.out)
        if out.exists():raise ValueError('Verification output exists; choose a NEW directory')
        homes={v:genome.core.private(getattr(args,'home_'+v)) for v in VERSIONS}
        if any(out==p or out in p.parents or p in out.parents for p in homes.values()):
            raise ValueError('Verification output and runtime homes must be nonnested')
        jar=genome.core.private(args.jar)
        if out==jar or out in jar.parents:raise ValueError('Verification output must not contain the candidate JAR')
        out.mkdir(parents=True);owns=True;host=genome.core.host()
        for version in VERSIONS:
            genome.verify_home(homes[version],'linux-x86_64-genome-'+version,host)
        markers={v:genome.core.sha(homes[v]/genome.MARKER) for v in VERSIONS}
        if not args.java or not Path(args.java).is_file():raise genome.core.Unavailable('Java17+ unavailable; supply --java')
        if not jar.is_file():raise genome.core.Unavailable('Development JAR unavailable: '+str(jar))
        phase='verification'
        record=verify(args,out,homes,jar)
        phase='final-runtime-integrity'
        for version in VERSIONS:
            genome.verify_home(homes[version],'linux-x86_64-genome-'+version,host)
        require(markers=={v:genome.core.sha(homes[v]/genome.MARKER) for v in VERSIONS},'immutable runtime markers')
        record['runtime_marker_sha256']=markers
    except genome.core.Unavailable as failure:
        code=3;record=dict(status='skipped-dependency-unavailable',cause=str(failure))
    except (FileNotFoundError,PermissionError) as failure:
        code=3 if phase=='dependencies' else 2
        record=dict(status='skipped-dependency-unavailable' if code==3 else 'failed-retained',cause=str(failure),phase=phase)
    except Exception as failure:
        code=2;record=dict(status='failed-retained',cause=repr(failure))
    record.update(exit_code=code,required_action=RECOVERY)
    if owns:
        genome.core.write(out/'verification.json',record)
        (out/'WARNINGS.md').write_text('# HOMER GENOMIC '+('VERIFICATION SKIPPED' if code==3 else 'VERIFICATION FAILED' if code else 'VERIFICATION SCOPE')+'\n\n'+record.get('cause',record.get('scope',''))+'\n\n'+RECOVERY+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in record.items() if k not in {'protocols','calls','input_sha256','source_sha256'}}),file=sys.stderr if code else sys.stdout,flush=True)
    return code


if __name__=='__main__':raise SystemExit(main())
