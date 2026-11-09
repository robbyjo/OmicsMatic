# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the public typed original counting workflow against all fifty separate original count-list controls."""
import argparse,json,os,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save,environment


def cases(inputs,fresh):
    def case(id,files='single.sam',annotation='genes.gtf',paired=False,pairs=False,options=None,genome=None,extra=None):
        names=files if isinstance(files,list) else [files];pe=paired if isinstance(paired,list) else [paired]*len(names);cp=pairs if isinstance(pairs,list) else [pairs]*len(names)
        return dict(id=id,files=[dict(path=str(inputs/n),paired_end=p,count_read_pairs=c,strand=0) for n,p,c in zip(names,pe,cp,strict=True)],
                    annotation=dict(path=str(inputs/annotation),format='GTF' if '.gtf' in annotation else 'SAF',extra_attributes=extra or []),options=options or {},genome=str(inputs/genome) if genome else None)
    result=[case('rna-paper-options',options=dict(countMultiMappingReads=False,juncCounts=True)),case('single-meta'),
            case('paired-fragments','paired.sam',paired=True,pairs=True),case('paired-reads','paired.sam',paired=True,pairs=False),
            case('mixed-libraries',['single.sam','paired.sam'],paired=[False,True],pairs=[False,True]),
            case('exon-features',options=dict(useMetaFeatures=False,juncCounts=True)),case('fractional-overlap',options=dict(allowMultiOverlap=True,countMultiMappingReads=True,fraction=True)),
            case('strand-forward'),case('strand-reverse')]
    result[-2]['files'][0]['strand']=1;result[-1]['files'][0]['strand']=2
    for maximum in [100,600]:result.append(case(f'atac-{maximum}-inactive','paired.sam','peaks.saf',True,True,dict(countMultiMappingReads=False,maxFragLength=maximum,checkFragLength=False)))
    result.extend([case('atac-100-active','paired.sam','peaks.saf',True,True,dict(countMultiMappingReads=False,minFragLength=0,maxFragLength=100,checkFragLength=True,requireBothEndsMapped=True)),
        case('junction-genome',options=dict(juncCounts=True),genome='genome.fasta'),case('junction-saf',annotation='peaks.saf',options=dict(juncCounts=True)),
        case('gzip-annotation',annotation='genes.gtf.gz'),case('duplicate-basenames',['one/same.sam','two/same.sam'])])
    previous=inputs;inputs=fresh
    result.extend([case('fresh-union-strands',annotation='strand-union.saf'),case('fresh-numeric-ids',annotation='numeric.saf'),case('fresh-logical-ids',annotation='logical.saf'),
        case('fresh-empty-junctions','no-junction.sam',options=dict(juncCounts=True)),case('fresh-extra-attributes',extra=['transcript_id']),
        case('fresh-bam-single','single.bam'),case('fresh-bam-paired-fragments','paired.bam',paired=True,pairs=True),case('fresh-bam-paired-reads','paired.bam',paired=True),
        case('fresh-bam-junctions','single.bam',options=dict(juncCounts=True),genome='genome.fasta')]);assert len(result)==25;return result


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','bindings','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True)
    state=dict(status='FAIL2_RSUBREAD_PUBLIC_WORKFLOW_VALIDATION',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,runs={})
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());assert build['status']=='passed-additive-JDK-only-Rsubread-original-counting-workflow-build';jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        base=ROOT/'build/literature-review/rsubread-runtime';java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe';script=ROOT/'tools/literature/compare_rsubread_workflow_results.R'
        for edition,original in [('2260','original-controls-3'),('2232-R441','original2232-controls-1')]:
            request=dict(schema_version=1,mode='ANALYZE',out=str(out/(edition+'-analysis')),binding=str((a.bindings/(edition+'-bound/COMPLETED.json')).resolve()),cases=cases(base/original/'inputs',base/'result-edges-1/inputs'))
            path=out/(edition+'.request.json');save(path,request);command=[java,'-Xmx256m','-jar',str(jar),str(path)]
            with (out/(edition+'.stdout')).open('xb') as first,(out/(edition+'.stderr')).open('xb') as second:run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=600,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
            save(out/(edition+'.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==0,(edition,'Read complete original/native stderr; no batch admitted')
            home=out/(edition+'-analysis');receipt=json.loads((home/'COMPLETED.json').read_bytes());assert receipt['status']=='completed-original-counting-with-warnings' and receipt['analysis_admitted'] and len(receipt['summaries'])==25
            verify=out/(edition+'.verify.json');save(verify,dict(schema_version=1,mode='VERIFY',analysis=str(home)));command=[java,'-Xmx256m','-jar',str(jar),str(verify)]
            run=subprocess.run(command,capture_output=True,timeout=120);(out/(edition+'-verify.stdout')).write_bytes(run.stdout);(out/(edition+'-verify.stderr')).write_bytes(run.stderr);save(out/(edition+'-verify.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==0,edition
            rscript=Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe') if edition=='2260' else base/'r441-setup-1/R441/root/bin/x64/Rscript.exe';env=environment()
            if edition=='2232-R441':env['R_HOME']=rscript.parents[2].as_posix()
            comparison=out/(edition+'-comparison');comparison.mkdir();command=[str(rscript),'--vanilla',str(script),str(home),str(base/original/'original'),str(base/'result-edges-1'/edition),str(comparison)]
            run=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=120,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0);(out/(edition+'-comparison.stdout')).write_bytes(run.stdout);(out/(edition+'-comparison.stderr')).write_bytes(run.stderr);save(out/(edition+'-comparison.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==0,edition
            assert (comparison/'R-status.txt').read_text().strip()=='PASS25_COMPLETE_PUBLIC_ORIGINAL_COUNT_LIST_COMPARISONS_WITH_EXPLICIT_POSITIONAL_TARGET_LEDGER'
            state['runs'][edition]=dict(receipt=pin(home/'COMPLETED.json'),complete_original_comparisons=25,summaries=receipt['summaries'])
        assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());state.update(status='passed-fifty-public-original-Rsubread-complete-count-list-executions-and-separate-original-comparisons',artifact=build['artifact'],source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),script]},fresh_original_executions=50,source_input_identity_checked=True,original_complete_RDS_retained=True,independent_read_counting=False,whole_package_implemented=False,whole_paper_reproduced=False)
    except Exception as e:state['error']=repr(e);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual whole-batch/reference checks')
    raise SystemExit(main())
