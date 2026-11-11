# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently audit complete original Hail workflow controls: entry masks, AF, LD, PCA equations, liftover, MatrixTable reuse and Batch outputs."""
import argparse,hashlib,json,math,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def read(path):return json.loads(path.read_bytes())
def number(value):
    assert set(value)=={'state','text'} and value['state']=='value';result=float(value['text']);assert math.isfinite(result);return result
def close(left,right):assert math.isclose(left,right,rel_tol=1e-9,abs_tol=1e-9),(left,right)
def dot(left,right):return sum(a*b for a,b in zip(left,right))
def parse_vcf(path):
    samples=[];variants=[]
    for line in path.read_text().splitlines():
        if line.startswith('##'):continue
        columns=line.split('\t')
        if line.startswith('#CHROM'):samples=columns[9:];continue
        entries=[]
        for token in columns[9:]:
            raw=dict(zip(columns[8].split(':'),token.split(':')));gt=raw['GT'];call=None if '.' in gt else dict(type='call',alleles=[int(x) for x in gt.replace('|','/').split('/')],phased='|' in gt);entries.append(dict(GT=dict(state='missing') if call is None else call,DP=dict(state='missing') if raw['DP']=='.' else int(raw['DP']),GQ=dict(state='missing') if raw['GQ']=='.' else int(raw['GQ'])))
        variants.append(dict(contig=columns[0],position=int(columns[1]),rsid=columns[2],alleles=[columns[3],*columns[4].split(',')],entries=entries))
    return samples,variants

def audit(home,fixtures=None):
    fixtures=home if fixtures is None else fixtures;inputs=fixtures/'literal-inputs';report=read(home/'COMPLETED.json');assert report['status']=='PASS_COMPLETE_ORIGINAL_HAIL_WORKFLOW_CANDIDATE_ONLY' and report['namespace_patched'] is False and report['cloud_batch_submitted'] is False and report['implementation_credit'] is False and report['complete_native_readback_verified'] is True and len(report['cases'])==10;assert read(home/'original.process.json')['owned_live_after_cleanup']==[] and read(home/'original.process.json')['exit_code']==0
    for name,want in read(fixtures/'literal-inputs.json').items():assert pin(inputs/name)==want
    if 'source_pins' in report:
        for name,want in report['source_pins'].items():assert pin(ROOT/name)==want,name
    else:assert pin(home/'worker.py')==pin(ROOT/'src/main/resources/org/jlinalg/omics/hail-workflow/worker.py')
    samples,variants=parse_vcf(inputs/'mixed.vcf');raw=read(home/'raw-mixed/matrix-complete.json');assert [col['s'] for col in raw['columns']]==samples and raw['column_count']==8 and raw['row_count']==9;assert len(raw['rows'])==len(variants);checks=dict(complete_original_cases=10,raw_genotype_entries=0,independent_sample_qc_counts=0,independent_variant_af_values=0,independent_ld_values=0,pca_equations=0,liftover_loci=0,batch_outputs=0,axis_filtered_entries=0,bgzf_roundtrip_entries=0,original_dag_edges=0)
    for row,source in zip(raw['rows'],variants):
        assert row['locus']==dict(type='locus',contig=source['contig'],position=source['position'],reference_genome='GRCh37') and row['alleles']==source['alleles'] and row['rsid']==source['rsid'];assert row['__omicsmatic_entries']==source['entries'];checks['raw_genotype_entries']+=len(source['entries'])
    filtered=[]
    for row in variants:
        entries=[e if isinstance(e['DP'],int) and isinstance(e['GQ'],int) and e['DP']>=10 and e['GQ']>=20 else dict(state='missing') for e in row['entries']];filtered.append(dict(**row,filtered=entries))
    for phase,field,entries_for_row in [('sample-before','sample_before',lambda row:row['entries']),('sample-after','sample_after',lambda row:row['filtered'])]:
        rows=read(home/'filtered-qc'/phase/'metrics.json')['rows'];by_sample={row['s']:row[field] for row in rows};assert len(rows)==len(by_sample)==len(samples)
        for j,sample in enumerate(samples):
            entries=[entries_for_row(row)[j] for row in filtered];present=[e for e in entries if 'state' not in e];called=[e for e in present if 'state' not in e['GT']];homref=sum(all(a==0 for a in e['GT']['alleles']) for e in called);het=sum(len(e['GT']['alleles'])==2 and len(set(e['GT']['alleles']))==2 for e in called);homvar=len(called)-homref-het;expected=dict(n_called=len(called),n_not_called=len(present)-len(called),n_filtered=len(entries)-len(present),n_hom_ref=homref,n_het=het,n_hom_var=homvar,n_non_ref=len(called)-homref);actual=by_sample[sample]
            for key,value in expected.items():assert actual[key]==value,(phase,sample,key,actual[key],value);checks['independent_sample_qc_counts']+=1
            close(number(actual['call_rate']),len(called)/len(entries));checks['independent_sample_qc_counts']+=1
    variant_metrics=read(home/'filtered-qc/variant-after/metrics.json')['rows'];by_position={row['locus']['position']:row['variant_after'] for row in variant_metrics};assert len(variant_metrics)==len(by_position)==len(filtered)
    for row in filtered:
        actual=by_position[row['position']];calls=[e['GT']['alleles'] for e in row['filtered'] if 'state' not in e and 'state' not in e['GT']];ac=[sum(call.count(allele) for call in calls) for allele in range(len(row['alleles']))];assert actual['AC']==ac and actual['AN']==sum(ac)
        for source,expected in zip(actual['AF'],ac):close(number(source),expected/sum(ac));checks['independent_variant_af_values']+=1
    result=read(home/'filtered-qc/matrix-complete.json');assert result==read(home/'matrix-roundtrip/matrix-complete.json');expected=[row for row in filtered if sum('state' not in e and 'state' not in e['GT'] for e in row['filtered'])/len(samples)>=.5];assert [row['locus']['position'] for row in result['rows']]==[row['position'] for row in expected]
    for row,original in zip(result['rows'],expected):assert row['__omicsmatic_entries']==original['filtered'];close(number(row['minor_allele_frequency']),min(number(v) for v in by_position[original['position']]['AF']))
    summary=read(home/'make-synthetic-summary/matrix-complete.json');assert summary==read(home/'summary-resource-read/matrix-complete.json') and summary['row_key_schema']=='struct{gene_id: str}' and 'GT' not in summary['entry_schema']
    for row in summary['rows']:
        for entry in row['__omicsmatic_entries']:assert entry['source_missing']==dict(state='missing') and entry['source_nan']==dict(state='NaN',text='NaN');close(number(entry['p_value']),.1)
    ld_samples,ld_variants=parse_vcf(inputs/'biallelic.vcf');dosages=[[sum(a!=0 for a in e['GT']['alleles']) for e in row['entries']] for row in ld_variants];centered=[[value-sum(row)/len(row) for value in row] for row in dosages];correlation=[[dot(a,b)/math.sqrt(dot(a,a)*dot(b,b)) for b in centered] for a in centered];ld=read(home/'ld-pca/ld/ld-complete.json');assert ld['rows']==ld['columns']==8 and len(ld['values'])==8
    for expected,actual in zip(correlation,ld['values']):
        assert len(actual)==8
        for e,a in zip(expected,actual):close(number(a),e);checks['independent_ld_values']+=1
    pruned=read(home/'ld-pca/prune/pruned-variants.json')['rows'];selected=[next(i for i,row in enumerate(ld_variants) if row['position']==record['locus']['position'] and row['alleles']==record['alleles']) for record in pruned];assert len(selected)==len(set(selected))>0
    for i,a in enumerate(selected):
        for b in selected[i+1:]:assert correlation[a][b]**2<.05+1e-12
    assert [r['locus']['position'] for r in read(home/'ld-pca/matrix-complete.json')['rows']]==[row['locus']['position'] for row in pruned]
    eigenvalues=[number(v) for v in read(home/'ld-pca/pca/eigenvalues.json')];score_rows=read(home/'ld-pca/pca/scores.json')['rows'];scores_by_sample={row['s']:[number(v) for v in row['scores']] for row in score_rows};assert len(eigenvalues)==2 and len(scores_by_sample)==len(score_rows)==len(ld_samples);scores=[scores_by_sample[sample] for sample in ld_samples];loading_rows=read(home/'ld-pca/pca/loadings.json')['rows'];loadings_by_position={row['locus']['position']:[number(v) for v in row['loadings']] for row in loading_rows};assert len(loadings_by_position)==len(loading_rows)==len(ld_variants);loadings=[loadings_by_position[row['position']] for row in ld_variants];x=[]
    for row in dosages:
        mean=sum(row)/len(row);scale=math.sqrt(mean*(2-mean)*len(dosages)/2);x.append([(v-mean)/scale for v in row])
    for component,eigenvalue in enumerate(eigenvalues):
        close(sum(row[component]**2 for row in scores),eigenvalue);close(sum(row[component]**2 for row in loadings),1);checks['pca_equations']+=2
        for i,row in enumerate(x):close(dot(row,[s[component] for s in scores]),eigenvalue*loadings[i][component]);checks['pca_equations']+=1
        for j in range(len(ld_samples)):close(sum(x[i][j]*loadings[i][component] for i in range(len(x))),scores[j][component]);checks['pca_equations']+=1
    close(dot([s[0] for s in scores],[s[1] for s in scores]),0);close(dot([s[0] for s in loadings],[s[1] for s in loadings]),0);checks['pca_equations']+=2
    lifted=read(home/'liftover-strand-missing/liftover.json')['rows'];positions=[None,101,150,200,None,248956122,248956073,248956023,None];assert len(lifted)==len(positions)==9
    for i,(record,position) in enumerate(zip(lifted,positions)):
        assert record['ordinal']==i and record['id']=='site-'+str(i) and record['original_alleles']==['A','G']
        if position is None:assert record['lifted']==dict(state='missing')
        else:assert record['lifted']==dict(result=dict(type='locus',contig='chr1',position=position,reference_genome='GRCh38'),is_negative_strand=i>=5)
        checks['liftover_loci']+=1
    expected_outputs={'left-table':b'gene-A\t4\ngene-B\t2\n','right-table':b'gene-B\t5\ngene-C\t7\n','reduce-table':b'gene-A\t4\ngene-B\t7\ngene-C\t7\n','report-table':b'gene-A\t4\ngene-B\t7\ngene-C\t7\n'};pins=read(home/'batch-resource-dag/complete-output-pins.json');assert len(pins)==len(expected_outputs)
    for name,expected in expected_outputs.items():
        payload=(home/'batch-resource-dag/outputs'/name).read_bytes();assert payload==expected;row=next(r for r in pins if r['job']+'-'+r['output']==name);assert row['bytes']==len(payload) and row['sha256']==hashlib.sha256(payload).hexdigest();checks['batch_outputs']+=1
    graph=read(home/'batch-resource-dag/original-dependency-graph.json');assert graph['jobs']==[dict(id='left',parents=[]),dict(id='right',parents=[]),dict(id='reduce',parents=['left','right']),dict(id='report',parents=['reduce'])];checks['original_dag_edges']=3
    axis=read(home/'axis-filtering/matrix-complete.json');assert axis['row_count']==6 and axis['column_count']==7 and axis['columns']==raw['columns'][:7]
    for actual,source in zip(axis['rows'],raw['rows'][:6]):expected=dict(source);expected['__omicsmatic_entries']=source['__omicsmatic_entries'][:7];assert actual==expected;checks['axis_filtered_entries']+=7
    assert raw==read(home/'bgzf-roundtrip/matrix-complete.json');checks['bgzf_roundtrip_entries']=72
    return checks

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--controls',type=Path,required=True);parser.add_argument('--fixtures',type=Path);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_ORIGINAL_HAIL_WORKFLOW_AUDIT_INCOMPLETE',implementation_credit=False,whole_goal_complete=False,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))});(out/'auditor-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    try:
        controls=a.controls.resolve()
        if a.fixtures:
            parent=controls.parent;marker=read(parent/'analysis-status.json');assert marker['result_admitted'] is True and pin(parent/'manifest.json')['sha256']==marker['manifest_sha256'];manifest=read(parent/'manifest.json')
            for name,want in manifest['files'].items():assert pin(parent/name)==want,name
            state['producer_manifest']=dict(path=(parent/'manifest.json').relative_to(ROOT).as_posix(),**pin(parent/'manifest.json'))
        state.update(status='PASS_COMPLETE_ORIGINAL_HAIL_WORKFLOW_INDEPENDENT_AUDIT_ONLY',checks=audit(controls,a.fixtures.resolve() if a.fixtures else None));print(state['status'],state['checks'])
    except Exception as failure:state.update(status='FAIL2_ORIGINAL_HAIL_WORKFLOW_AUDIT_INCOMPLETE',error=repr(failure),required_action='No complete workflow or tool integration is admitted. Preserve original outputs and this full audit failure; inspect error.traceback, correct the demonstrated source/input/output/auditor discrepancy and repeat in NEW paths. Never drop a failing cell, alter an original result or re-pin a scientific hash to pass.');(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8');(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state['required_action']+'\n\n'+repr(failure)+'\n',encoding='utf8');print(state['status'],repr(failure))
    save(out/'COMPLETED.json',state);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable independent original-output audits')
    raise SystemExit(main())
