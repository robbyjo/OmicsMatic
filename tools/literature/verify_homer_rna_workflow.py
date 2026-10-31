#!/usr/bin/env python3
"""Run two packaged RNA recipes and compare complete fresh original results.

Java owns its staging/monitors. This verifier never walks live Java staging.
Fresh originals use the separate bounded RNA setup runner.
"""
import argparse
import json
import os
from pathlib import Path
import signal
import shutil
import subprocess
import sys
import time
import zipfile
import install_homer_rna as rna

ROOT=Path(__file__).resolve().parents[2]


def members(group):
    result=[]
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():continue
        try:
            text=(path/'stat').read_text();fields=text[text.rfind(')')+2:].split()
            if int(fields[2])==group:result.append(int(path.name))
        except (FileNotFoundError,ProcessLookupError,PermissionError):continue
    return result


def java_run(command,work,label):
    began=time.monotonic()
    with (work/(label+'.stdout')).open('xb') as out,(work/(label+'.stderr')).open('xb') as err:
        child=subprocess.Popen(command,cwd=work,stdout=out,stderr=err,start_new_session=True)
        try:code=child.wait(timeout=4200)
        except BaseException:
            try:os.killpg(child.pid,signal.SIGKILL)
            except ProcessLookupError:pass
            child.wait(timeout=30)
            raise
    if members(child.pid):
        try:os.killpg(child.pid,signal.SIGKILL)
        except ProcessLookupError:pass
        raise ValueError('Java workflow left an owned process; preserve diagnostics')
    return dict(exit_code=code,seconds=time.monotonic()-began,owned_group_empty_after_wait=True)


def tag_family(directory):
    if not directory.is_dir():raise ValueError('Missing explicit RNA tag role: '+str(directory))
    result={}
    for path in sorted(directory.iterdir()):
        if path.is_symlink() or not path.is_file():raise ValueError('Unexpected RNA tag-role member')
        if path.name=='tagInfo.txt':result[path.name]='\n'.join(s for s in path.read_text().splitlines() if not s.startswith('cmd='))+'\n'
        else:result[path.name]=rna.core.sha(path)
    return result


def canonical_matrix(path,old_root,samples):
    with path.open() as stream:
        header=stream.readline().rstrip('\n')
        if len(header.split('\t'))!=8+samples or not header.startswith('Transcript/RepeatID (cmd='):raise ValueError('Incomplete original RNA header')
        rows=[];ids=set()
        for text in stream:
            row=text.rstrip('\n');fields=row.split('\t')
            if len(fields)!=8+samples or fields[0] in ids:raise ValueError('Incomplete/duplicate original RNA population')
            ids.add(fields[0]);rows.append(row)
    rows.sort()
    return dict(header=header.replace(str(old_root),'WORK'),rows=rows)


def original_reference(home,output,reference,session,args):
    reference.mkdir();tag_labels=sorted(k.removeprefix('process_') for k in session if k.startswith('process_tags-'))
    if not tag_labels or len(tag_labels)>32:raise ValueError('Missing complete original RNA tag operations')
    old=Path(session['process_'+tag_labels[0]]['command'][1]).parent
    for label in tag_labels:
        original=session['process_'+label]['command'];source=Path(original[2]);shutil.copyfile(output/source.name,reference/source.name)
    labels=[*tag_labels,*[name for name in ['raw','rpkm'] if 'process_'+name in session]];calls=[]
    for label in labels:
        command=[]
        for argument in session['process_'+label]['command']:
            command.append(str(reference)+argument[len(str(old)):] if argument==str(old) or argument.startswith(str(old)+'/') else argument)
        calls.append(rna.execute(command,home,reference,label,args))
    for label in tag_labels:
        role='R'+label.removeprefix('tags-r')
        if tag_family(output/('tags-'+role))!=tag_family(reference/('tags-'+role)):raise ValueError('Every original tag/QC/metadata row must match fresh original role: '+role)
    matrices={}
    for mode in ['raw','rpkm']:
        if 'process_'+mode not in session:continue
        first=canonical_matrix(output/(mode+'.stdout'),old,len(tag_labels));second=canonical_matrix(reference/(mode+'.stdout'),reference,len(tag_labels))
        if first!=second:raise ValueError('Complete RNA '+mode+' header/metadata/length/copy/value population differs from fresh original')
        matrices[mode]=dict(rows=len(first['rows']),sample_values=len(first['rows'])*len(tag_labels),canonical_sha256=__import__('hashlib').sha256((first['header']+'\n'+'\n'.join(first['rows'])+'\n').encode()).hexdigest())
    return dict(calls=calls,matrices=matrices,all_tag_families_identical_apart_from_command_paths=True)


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['home','resource-root','jar','out']:parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--java',default='/usr/bin/java')
    parser.add_argument('--timeout',type=int,default=3600)
    parser.add_argument('--maximum-upstream-memory-bytes',type=int,default=8589934592)
    parser.add_argument('--workspace-bytes',type=int,default=137438953472)
    args=parser.parse_args(argv);out=rna.safe(args.out)
    if out.exists():raise ValueError('Verifier output exists; choose NEW directory')
    out.mkdir(parents=True);code=0;record={}
    try:
        home=rna.safe(args.home);root=rna.safe(args.resource_root);jar=rna.core.private(args.jar);rna.verify_home(home,root,rna.core.host());jar_hash=rna.core.sha(jar);marker_hash=rna.core.sha(home/rna.MARKER)
        if not rna.LOCK['minimum_upstream_memory_bytes']<=args.maximum_upstream_memory_bytes<=1099511627776 or not 1<=args.timeout<=86400 or not 1048576<=args.workspace_bytes<=1099511627776:raise ValueError('Invalid explicit verifier capacities')
        with zipfile.ZipFile(jar) as source:
            try:lock=source.read('org/jlinalg/omics/homer-rna-lock.json')
            except KeyError as failure:raise rna.core.Unavailable('Selected project JAR lacks RNA capability; build/use the RNA development candidate') from failure
            if lock!=rna.RAW:raise rna.core.Unavailable('Selected project JAR lacks current RNA lock; build/use the RNA development candidate')
        help_run=java_run([args.java,'-jar',str(jar),'homer-tools','rna','--help'],out,'help')
        if help_run['exit_code'] or rna.LOCK['profile'] not in (out/'help.stdout').read_text():raise rna.core.Unavailable('Project JAR lacks typed RNA command; runtime setup does not update older project JARs')
        generated=subprocess.run([sys.executable,str(ROOT/'examples/homer-rna-workflow/prepare_inputs.py'),'--out',str(out/'inputs'),'--runtime-home',str(home)],cwd=out,capture_output=True,timeout=30)
        (out/'generation.stdout').write_bytes(generated.stdout);(out/'generation.stderr').write_bytes(generated.stderr)
        if generated.returncode:raise ValueError('Synthetic RNA input preparation failed')
        proofs=[]
        for genome in ['hg38','mm10']:
            options_path=out/'inputs'/(genome+'-options.json');options=json.loads(options_path.read_text());options['timeout']=str(args.timeout);options['workspace-bytes']=str(args.workspace_bytes);options['maximum-upstream-memory-bytes']=str(args.maximum_upstream_memory_bytes);options_path.write_text(json.dumps(options,indent=2)+'\n')
            result=java_run([args.java,'-Xmx3g','-jar',str(jar),'homer-tools','rna','--options-file',str(options_path)],out,genome)
            if result['exit_code']:raise ValueError('Packaged RNA '+genome+' exited'+str(result['exit_code'])+'; inspect '+str(Path(options['out'])/'WARNINGS.md'))
            output=Path(options['out']);session=json.loads((output/'session.json').read_text());status=json.loads((output/'analysis-status.json').read_text())
            if session.get('complete_native_audit')!='passed' or status.get('exit_code')!=0 or status.get('status')!='completed-original-RNA-with-review-warnings':raise ValueError('Missing complete native RNA audit/status')
            fresh=original_reference(home,output,out/(genome+'-fresh-original'),session,args)
            proof=dict(genome=genome,java=result,reference_transcripts=session['reference_transcripts'],reference_exon_parts=session['reference_exon_parts'],part_sample_counts=session['audited_part_sample_counts'],final_rows=session['audited_final_rows'],final_sample_values=session['audited_final_sample_values'],fresh_original=fresh)
            proofs.append(proof);rna.core.write(out/'progress.json',proofs);print(genome+' packaged RNA and fresh complete original passed',flush=True)
        if rna.core.sha(jar)!=jar_hash or rna.core.sha(home/rna.MARKER)!=marker_hash:raise ValueError('Candidate/runtime changed during verification')
        rna.verify_home(home,root,rna.core.host());record=dict(status='passed-two-packaged-full-reference-RNA-workflows-and-fresh-originals',candidate_sha256=jar_hash,runtime_marker_sha256=marker_hash,proofs=proofs)
    except rna.core.Unavailable as failure:code=3;record=dict(status='skipped-unavailable-dependency-or-capability',cause=str(failure))
    except Exception as failure:code=2;record=dict(status='failed-retained',cause=repr(failure))
    record.update(exit_code=code,required_action=rna.RECOVERY,scope='Synthetic technical fixtures with whole original RefSeq populations and complete native audits. Fresh original tag/QC/metadata and all final columns compared,apart from command/work paths. No biological,paper-data,differential-expression,release or production-accuracy certificate.')
    rna.core.write(out/'verification.json',record);(out/'WARNINGS.md').write_text('# RNA VERIFICATION '+('SKIPPED' if code==3 else 'FAILED' if code else 'REVIEW WARNINGS')+'\n\n'+record.get('cause',record['scope'])+'\n\n'+rna.RECOVERY+'\n')
    print(json.dumps(dict(status=record['status'],exit_code=code,report=str(out/'verification.json'),cause=record.get('cause'),required_action=rna.RECOVERY)),file=sys.stderr if code else sys.stdout,flush=True)
    return code


def entry():
    try:return main()
    except rna.core.Unavailable as failure:code=3;status='VERIFICATION SKIPPED';cause=str(failure)
    except Exception as failure:code=2;status='VERIFICATION FAILED';cause=repr(failure)
    print(status+': '+cause+'\n'+rna.RECOVERY,file=sys.stderr,flush=True)
    return code


if __name__=='__main__':raise SystemExit(entry())
