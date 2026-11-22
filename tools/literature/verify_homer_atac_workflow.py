#!/usr/bin/env python3
"""Run three packaged synthetic ATAC workflows and fresh complete original references.

Java owns its own monitors and atomic publication. This verifier never walks a
live Java staging directory. Independent originals use the bounded setup runner.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time
import install_homer_atac as atac

ROOT=Path(__file__).resolve().parents[2]


def options(home,out,inputs,prepared,case):
    return dict(home=str(home),profile=atac.LOCK['profile'],out=str(out),**{
        'sam-list':str(inputs/case['manifest']),'reference-lengths':str(inputs/'reference.tsv'),
        'reference-build':prepared['reference_build'],'reference-provenance':prepared['reference_provenance'],
        'duplicate-alignments':'error','supplementary-alignments':'error','unpaired-alignments':'error',
        'improper-pairs':'error','zero-template-length':'error','identical-replicates':'error',
        'effective-genome-size':str(prepared['effective_genome_size']),'genome-size-policy':'error',
        'peak-style':case['peak_style'],'peak-local-fold':case['local_fold'],'peak-clonal-fold':case['clonal_fold'],
        'peak-tag-cap':case['tag_cap'],'coverage-length-normalization':case['length_normalization'],
        'coverage-endpoints':case['endpoints'],'maximum-records':'10000','maximum-ledger-keys':'100',
        'maximum-chromosome-tags':'10000','maximum-regions':'1000','maximum-coverage-rows':'100000',
        'workspace-bytes':'268435456','maximum-upstream-memory-bytes':'2147483648','timeout':'600'})


def canonical_peaks(path):
    lines=path.read_text().splitlines();body=[s for s in lines if s and not s.startswith('#')]
    if not lines or lines[0]!='# HOMER Peaks' or not any(s.startswith('#PeakID\t') for s in lines):raise ValueError('Incomplete original region output')
    return [s for s in lines if not s.startswith('# tag directory = ') and not s.startswith('# cmd = ')]


def tag_family(directory):
    if not directory.is_dir():raise ValueError('Expected original tag-result directory missing: '+str(directory))
    result={}
    for path in sorted(directory.iterdir()):
        if not path.is_file() or path.is_symlink():raise ValueError('Unexpected original tag family shape')
        if path.name=='tagInfo.txt':result[path.name]='\n'.join(s for s in path.read_text().splitlines() if not s.startswith('cmd='))+'\n'
        else:result[path.name]=atac.core.sha(path)
    return result


def compare_tag_families(output,reference,session):
    roles=session.get('original_tag_populations',{})
    if not isinstance(roles,dict) or 'MERGED' not in roles:raise ValueError('Explicit original tag roles are missing')
    for role in roles:
        if role!='MERGED' and not __import__('re').fullmatch(r'R[0-9]{4}',role):raise ValueError('Unexpected original tag role identity')
        if tag_family(output/('tags-'+role))!=tag_family(reference/('tags-'+role)):
            raise ValueError('Every original tag/QC/metadata record must match the fresh complete reference: '+role)


def original_reference(home,output,reference,session):
    reference.mkdir();old=Path(session['process_peaks']['command'][1]).parent
    for path in output.glob('stage-*.sam'):shutil.copyfile(path,reference/path.name)
    shutil.copyfile(output/'stage-chrom.sizes',reference/'stage-chrom.sizes');calls=[]
    labels=[*sorted(k.removeprefix('process_') for k in session if k.startswith('process_tags-')),'merge','peaks','coverage','conversion']
    for label in labels:
        command=[]
        for argument in session['process_'+label]['command']:
            prefix=str(old)+'/'
            command.append(str(reference)+argument[len(str(old)):] if argument==str(old) or argument.startswith(prefix) else argument)
        calls.append(atac.execute(command,home,reference,label,seconds=180,rss=2147483648,workspace=268435456))
    for name in ['peaks','coverage','conversion']:
        if calls[labels.index(name)]['exit_code']!=0:raise ValueError('Fresh original operation failed')
    compare_tag_families(output,reference,session)
    if canonical_peaks(output/'peaks.stdout')!=canonical_peaks(reference/'peaks.stdout'):raise ValueError('Full original region header/rows differ from fresh reference')
    if (output/'coverage.stdout').read_bytes()!=(reference/'coverage.stdout').read_bytes():raise ValueError('Every original one-base coverage bin must match fresh reference')
    if (output/'coverage.bw').read_bytes()!=(reference/'coverage.bw').read_bytes():raise ValueError('Original converter bytes differ on this deterministic fixed-runtime fixture; preserve both artifacts for review')
    return calls


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['home','jar','out']:parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--java',default='/usr/bin/java');args=parser.parse_args(argv);out=atac.safe(args.out)
    if out.exists():raise ValueError('Verifier output exists; choose NEW path')
    out.mkdir(parents=True);code=0;record={}
    try:
        host=atac.core.host();home=atac.safe(args.home);jar=atac.core.private(args.jar);atac.verify_home(home,host);jar_hash=atac.core.sha(jar);marker_hash=atac.core.sha(home/atac.MARKER)
        with (out/'help.stdout').open('xb') as stdout,(out/'help.stderr').open('xb') as stderr:help_run=subprocess.run([args.java,'-jar',str(jar),'homer-tools','atac','--help'],cwd=out,stdout=stdout,stderr=stderr,timeout=60)
        if help_run.returncode!=0 or atac.LOCK['profile'] not in (out/'help.stdout').read_text():raise atac.core.Unavailable('Selected project JAR lacks the typed ATAC command; build/use the ATAC development candidate and confirm homer-tools atac --help. Runtime setup alone does not add commands to an older JAR')
        path=ROOT/'examples/homer-atac-workflow/prepare_inputs.py';spec=importlib.util.spec_from_file_location('homer_atac_examples',path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);prepared=module.prepare(out/'inputs');proofs=[]
        for case in prepared['cases']:
            work=out/case['name'];work.mkdir();output=work/'result';o=options(home,output,out/'inputs',prepared,case);atac.core.write(work/'options.json',o);command=[args.java,'-Xmx1g','-jar',str(jar),'homer-tools','atac','--options-file',str(work/'options.json')];began=time.monotonic()
            with (work/'java.stdout').open('xb') as stdout,(work/'java.stderr').open('xb') as stderr:run=subprocess.run(command,cwd=work,stdout=stdout,stderr=stderr,timeout=900)
            status=json.loads((output/'analysis-status.json').read_text());session=json.loads((output/'session.json').read_text())
            if run.returncode==3 or status['exit_code']==3:raise atac.core.Unavailable('Packaged ATAC workflow skipped: '+session.get('error','inspect WARNINGS.md'))
            if run.returncode!=0 or status['exit_code']!=0 or status['status']!='completed-original-ATAC-with-review-warnings':raise ValueError('Packaged ATAC workflow failed; inspect '+str(output/'WARNINGS.md'))
            if session['input_alignments']!=case['input_records'] or session['eligible_alignment_weight']!=case['eligible_weight'] or len(session['original_tag_populations'])!=case['replicates']+1:raise ValueError('Packaged complete input/replicate population differs')
            if (output/'native-expected-tags.tsv').read_bytes()!=(output/'native-actual-tags.tsv').read_bytes() or (output/'native-coverage.bedgraph').read_bytes()!=(output/'coverage.stdout').read_bytes() or session['audited_coverage_rows']!=session['audited_raw_BigWig_rows']:raise ValueError('Complete native/source tag or signal proof differs')
            if case['local_fold']=='0' and session['original_regions']<3:raise ValueError('Known synthetic positive regions omitted')
            calls=original_reference(home,output,work/'fresh-original',session)
            if atac.core.sha(jar)!=jar_hash or atac.core.sha(home/atac.MARKER)!=marker_hash:raise ValueError('Verifier executable/admission changed')
            atac.verify_home(home,host);proofs.append(dict(case=case['name'],status='passed-complete-packaged-native-and-fresh-original-workflow',records=case['input_records'],weighted_tags=case['eligible_weight'],replicates=case['replicates'],regions=session['original_regions'],coverage_rows=session['audited_coverage_rows'],source_tag_full_keys=session['complete_original_actual_full_keys'],command=command,reference_calls=calls,seconds=time.monotonic()-began,original_regions_sha256=atac.core.sha(output/'peaks.stdout'),original_coverage_sha256=atac.core.sha(output/'coverage.stdout'),bigwig_sha256=atac.core.sha(output/'coverage.bw')));atac.core.write(out/'progress.json',proofs);print(case['name']+' all original populations/regions/coverage/BigWig matched fresh references',flush=True)
        record=dict(status='passed-three-packaged-ATAC-workflows-and-three-fresh-complete-original-references',jar_sha256=jar_hash,marker_sha256=marker_hash,protocols=proofs,scope='Independent synthetic paired2chr fixtures,Unicode identities,softmatchingblocks,Tlen100/356comparatoraliases,excludedMAPQ/secondarypairs,threeinitialstyles,twoendpoint/lengthchoices,one-replicate merge. Everytag/QC/metadata family(exceptcmdpaths),completepeakheaders/rows(exceptcmdpaths),allprintedcoverage andoriginalconverterbytes match fresh original executions. Packaged native all-full-key halfunits/finalregioncounts/float32printedcoverage/rawBigWig audits passed. No paper-edition,biological,statisticalcalibration,production or broadHOMER completion certificate.')
    except (atac.core.Unavailable,FileNotFoundError,PermissionError) as failure:code=3;record=dict(status='skipped-unavailable-dependency-or-capability',cause=str(failure))
    except Exception as failure:code=2;record=dict(status='failed-retained',cause=repr(failure))
    record.update(exit_code=code,required_action=atac.RECOVERY);atac.core.write(out/'verification.json',record);(out/'WARNINGS.md').write_text('# ATAC VERIFICATION '+('SKIPPED' if code==3 else 'FAILED' if code else 'REVIEW WARNINGS')+'\n\n'+record.get('cause',record.get('scope',''))+'\n\n'+atac.RECOVERY+'\n');print(json.dumps({k:v for k,v in record.items() if k!='protocols'}),file=sys.stderr if code else sys.stdout,flush=True);return code


if __name__=='__main__':raise SystemExit(main())
