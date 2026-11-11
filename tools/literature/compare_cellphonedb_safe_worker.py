# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Pair complete deterministic original outputs; retain entropy-seeded sampling/parallel nulls without falsely claiming cross-run byte identity. Data only."""
import base64,json,sys
from pathlib import Path
import numpy as np


def plain(value):
    if isinstance(value,list):return [plain(v) for v in value]
    if isinstance(value,float) and not np.isfinite(value):return 'NaN' if np.isnan(value) else '+Inf' if value>0 else '-Inf'
    return value


def decode(value):
    if isinstance(value,list):return [decode(v) for v in value]
    if not isinstance(value,dict):return value
    kind=value.get('kind')
    if kind=='numeric-array':
        array=np.frombuffer(base64.b64decode(value['base64'],validate=True),dtype=np.dtype(value['dtype'])).reshape(value['shape']);return {'$ndarray':dict(dtype=str(array.dtype),shape=list(array.shape),values=plain(array.tolist()))}
    if kind=='object-array':return {'$ndarray':dict(dtype=value['dtype'],shape=value['shape'],values=decode(value['values']))}
    if kind=='dataframe':
        columns=[decode(v)['$ndarray']['values'] for v in value['column_data']];rows=[list(v) for v in zip(*columns)] if columns else [[] for _ in value['index']];return {'$dataframe':dict(index=decode(value['index']),index_names=decode(value['index_names']),columns=decode(value['columns']),column_names=decode(value['column_names']),dtypes=value['dtypes'],values=rows)}
    if kind=='python-class':return {'$python_class':value['name']}
    if kind=='nonfinite-scalar':return value['value']
    return {key:decode(v) for key,v in value.items()}


def main():
    root=Path(sys.argv[1]);request=json.loads((root/'request.json').read_bytes());checks=[];pvalue_cells=0;sampling_cases=0
    for case in request['analyses']:
        name=case['id'];home=root/'candidate'/name;original=json.loads((root/'direct'/name/'returned.legacy.json').read_bytes());actual=decode(json.loads((home/'returned.json').read_bytes()));observer=json.loads((home/'return-observer.json').read_bytes());assert observer['original_namespace_unchanged'] and observer['original_code_objects_unchanged'];sampling=case['method']=='statistical' and case['statistical']['subsampling']['enabled'];deterministic=case['threads']==1 and not sampling
        if deterministic:
            assert original==actual,('Complete uninstrumented/observed returned family changed',name)
            for key in ['rng-before.json','rng-after.json']:assert (root/'direct'/name/key).read_bytes()==(home/key).read_bytes(),('Original RNG changed',name,key)
            first={p.name:p.read_bytes() for p in (root/'direct'/name/'original').glob('*.txt')};second={p.name:p.read_bytes() for p in (home/'original').glob('*.txt')};assert first==second,('Full original TSV bytes changed',name)
        elif not sampling:
            for key in ['means','deconvoluted','deconvoluted_percents']:assert original[key]==actual[key],('Parallel real-data family changed',name,key)
        if sampling:
            initialization=json.loads((home/'observed-subsample-initialization.json').read_bytes());assert initialization['source_debug_seed'] is None,'The full original method passes None and reseeds from entropy; do not substitute the global/permutation seed';selected=decode(json.loads((home/'observed-subsample.json').read_bytes()))['returned']['$dataframe'];mapped=decode(json.loads((home/'preparation.json').read_bytes()))['mapped']['$dataframe'];assert len(selected['columns'])==case['statistical']['subsampling']['cells'] and len(selected['columns'])==len(set(selected['columns'])) and selected['index']==mapped['index'];positions=[mapped['columns'].index(cell) for cell in selected['columns']];assert selected['values']==[[row[i] for i in positions] for row in mapped['values']];assert json.loads((home/'subsampling-state.json').read_bytes())['source_errors']==[];sampling_cases+=1
        if case['method']=='statistical':
            observed=lambda key:decode(json.loads((home/('observed-'+key+'.json')).read_bytes()))['returned'];means=observed('means')['$dataframe'];gates=observed('eligibility')['$dataframe'];pvalues=observed('pvalues')['$dataframe'];packed=observed('permutation-greater-packed');assert len(packed)==case['statistical']['iterations'];hits=[[0]*len(means['columns']) for _ in means['index']];size=len(means['index'])*len(means['columns'])
            for current in packed:
                bytes_=current['$ndarray']['values'];assert len(bytes_)==(size+7)//8
                for i in range(size):hits[i//len(means['columns'])][i%len(means['columns'])]+=(bytes_[i//8]>>(7-i%8))&1
                for i in range(size,len(bytes_)*8):assert ((bytes_[i//8]>>(7-i%8))&1)==0,'Original padded bits must remain zero'
            expected=[[1. if mean==0 or gate==0 else hit/len(packed) for mean,gate,hit in zip(row,eligible,total,strict=True)] for row,eligible,total in zip(means['values'],gates['values'],hits,strict=True)];assert expected==pvalues['values'],'Complete source packed-null/P-value equation changed';pvalue_cells+=size
        checks.append(dict(id=name,all_returned_fields_and_TSV_bytes_exact=deterministic,source_entropy_subsampling_cross_run_byte_parity_claimed=False if sampling else None,source_parallel_null_byte_parity_claimed=False if case['threads']>1 else None,pickle_deserialized=False))
    assert sum(row['all_returned_fields_and_TSV_bytes_exact'] for row in checks)==17 and sampling_cases==1;record=dict(status='PASS_COMPLETE_PAIRED_DATA_ONLY_WORKER_CONTROLS',checks=checks,complete_original_pvalue_equation_cells=pvalue_cells,entropy_seeded_sampling_input_selection_checks=1,pickle_deserialized=False);(root/'comparison.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8');print('PASS_COMPLETE_PAIRED_DATA_ONLY_WORKER_CONTROLS',len(checks),pvalue_cells,'P-value cells')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete data-only source comparisons')
    main()
