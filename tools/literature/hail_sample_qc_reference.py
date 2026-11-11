# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Original public Hail sample_qc and allele-type controls, with every returned metric/state retained. No namespace patching."""
import hashlib,json,math,sys,traceback
from pathlib import Path
import hail as hl
from hail.genetics.allele_type import AlleleType

def encode(value):
    if isinstance(value,hl.Struct):return {name:encode(value[name]) for name in value}
    if isinstance(value,dict):return {name:encode(v) for name,v in value.items()}
    if isinstance(value,(tuple,list)):return [encode(v) for v in value]
    if value is None:return dict(state='missing')
    if isinstance(value,float):return dict(state='NaN' if math.isnan(value) else '+Inf' if value==math.inf else '-Inf' if value==-math.inf else 'value',text='NaN' if math.isnan(value) else '+Inf' if value==math.inf else '-Inf' if value==-math.inf else repr(value))
    if isinstance(value,(str,int,bool)):return value
    raise TypeError('Unexpected original QC value type: '+str(type(value)))

def matrix(case):
    nrows=len(case['rows']);ncols=len(case['samples']);mt=hl.utils.range_matrix_table(nrows,ncols,n_partitions=1)
    allele_arrays=hl.literal([row['alleles'] for row in case['rows']],hl.tarray(hl.tarray(hl.tstr)))
    mt=mt.annotate_rows(locus=hl.locus('1',mt.row_idx+1,reference_genome='GRCh37'),alleles=allele_arrays[mt.row_idx]).key_rows_by('locus','alleles')
    mt=mt.annotate_cols(s=hl.literal(case['samples'],hl.tarray(hl.tstr))[mt.col_idx]).key_cols_by('s')
    entries=[]
    for row in case['rows']:
        current=[]
        for entry in row['entries']:
            call=entry['call'];gt=None if call is None else hl.Call(call['alleles'],phased=call['phased'])
            current.append(dict(gt=gt,dp=entry['depth'],gq=entry['genotype_quality'],filtered=entry['filtered']))
        entries.append(current)
    depth=hl.tfloat64 if case['depth_type']=='float64' else hl.tint32;gq=hl.tfloat64 if case['gq_type']=='float64' else hl.tint32
    raw=hl.literal(entries,hl.tarray(hl.tarray(hl.tstruct(gt=hl.tcall,dp=depth,gq=gq,filtered=hl.tbool))))[mt.row_idx][mt.col_idx]
    fields=dict(GT=raw.gt,source_filtered=raw.filtered)
    if case['depth_type']!='absent':fields['DP']=raw.dp
    if case['gq_type']!='absent':fields['GQ']=raw.gq
    mt=mt.annotate_entries(**fields)
    mt=mt.filter_entries(~mt.source_filtered)
    return mt

def main():
    assert len(sys.argv)==3;home=Path(sys.argv[1]);cases=json.loads(Path(sys.argv[2]).read_bytes());assert hl.__version__=='0.2.139-5e5eedb87ff8';state=dict(status='FAIL2_ORIGINAL_HAIL_QC_REFERENCE_INCOMPLETE',version=hl.__version__,cases=[],namespace_patched=False,genomic_or_cloud_paper_reproduced=False);errors=[]
    try:
        hl.init(backend='spark',master='local[2]',log=str(home/'original-hail.log'),tmp_dir=str(home/'hail-temp'),local_tmpdir=str(home/'hail-local-temp'),spark_conf={'spark.driver.memory':'2g','spark.executor.memory':'2g','spark.driver.extraJavaOptions':'-XX:ActiveProcessorCount=2','spark.executor.extraJavaOptions':'-XX:ActiveProcessorCount=2','spark.ui.enabled':'false','spark.ui.showConsoleProgress':'false'})
        for case in cases['cases']:
            output=home/case['id'];output.mkdir();mt=matrix(case);result=hl.sample_qc(mt);table=result.cols();values=table.order_by(table.col_idx).collect();records=[dict(sample=v.s,ordinal=v.col_idx,metrics=encode(v.sample_qc)) for v in values];assert len(records)==len(case['samples']);(output/'original-result.json').write_text(json.dumps(records,indent=2,ensure_ascii=True)+'\n',encoding='utf8');(output/'original-schema.txt').write_text(str(result.col.dtype)+'\n',encoding='utf8');state['cases'].append(dict(id=case['id'],samples=len(records),rows=len(case['rows']),status='PASS_COMPLETE_ORIGINAL_PUBLIC_SAMPLE_QC_METRICS'));print(case['id'],'complete source samples',len(records),flush=True)
        pairs=cases['allele_pairs'];expressions=hl.array([hl.struct(ref=ref,alt=alt,numeric=hl.numeric_allele_type(ref,alt),qc=hl.if_else(hl.numeric_allele_type(ref,alt)==AlleleType.SNP,hl.if_else(hl.is_transition(ref,alt),int(AlleleType.TRANSITION),int(AlleleType.TRANSVERSION)),hl.numeric_allele_type(ref,alt))) for ref,alt in pairs]);values=hl.eval(expressions);types=[dict(ref=v.ref,alt=v.alt,allele_type=AlleleType(v.numeric).name,qc_allele_type=AlleleType(v.qc).name) for v in values];(home/'original-allele-types.json').write_text(json.dumps(types,indent=2)+'\n',encoding='utf8');state.update(status='PASS_COMPLETE_ORIGINAL_HAIL_SAMPLE_QC_AND_ALLELE_TYPES_ONLY',allele_pairs=len(types),sample_outputs=sum(c['samples'] for c in state['cases']))
    except Exception as error:state['error']=repr(error);(home/'original-error.traceback').write_text(traceback.format_exc(),encoding='utf8');print('FAIL2 ORIGINAL REFERENCE:',repr(error),flush=True)
    finally:
        try:hl.stop()
        except Exception as error:state.update(status='FAIL2_ORIGINAL_HAIL_STOP_FAILED',stop_error=repr(error))
        (home/'COMPLETED.json').write_text(json.dumps(state,indent=2)+'\n',encoding='utf8')
    return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original QC reference checks')
    raise SystemExit(main())
