"""Own original-API preparation caller. No vendor code is bundled or changed."""
from pathlib import Path
import json, math, numbers, pickle, sys, time, traceback, warnings
import r2g_runtime as common

def scalar(value):
    import numpy as np
    import pandas as pd
    if value is None:
        return None
    if value is pd.NA:
        return {'source_kind':'pandas.NA'}
    if isinstance(value, (bool, np.bool_)):
        return bool(value)
    if isinstance(value, str):
        return value
    if isinstance(value, numbers.Integral):
        return int(value)
    if isinstance(value, numbers.Real):
        return {'float_hex':float(value).hex()}
    if isinstance(value, np.ndarray):
        return {'source_kind':'ndarray', 'dtype':str(value.dtype), 'values':scalar(value.tolist())}
    if isinstance(value, (list, tuple)):
        return [scalar(v) for v in value]
    if isinstance(value, (set, frozenset)):
        common.require(all(isinstance(v, str) for v in value), 'Unexpected source set element type')
        return {'source_kind':type(value).__name__, 'values':sorted(value)}
    raise TypeError('Unsupported source scalar; no repr/coercion applied: ' + type(value).__name__)


def closed_h5ad(path, limits):
    import h5py
    seen = set()
    with h5py.File(path, 'r') as handle:
        def visit(group):
            address = h5py.h5o.get_info(group.id).addr
            if address in seen:
                return
            seen.add(address)
            common.require(len(seen) <= limits['maximum_files'], 'HDF5 object capacity exceeded')
            if isinstance(group, h5py.Dataset):
                common.require(not group.is_virtual and not group.external, 'Materialize virtual/external HDF5 data before capturing this input')
                return
            for name in group:
                link = group.get(name, getlink=True)
                common.require(isinstance(link, h5py.HardLink), 'Cistrome input must contain no external/soft HDF5 links')
                visit(group[name])
        visit(handle)


class Exports:
    """Stream complete typed values and field-finiteness flags; retain original TSVs."""
    def __init__(self, out):
        self.values = (out / 'scientific.jsonl').open('x', encoding='utf-8', newline='\n')
        self.flags = (out / 'field-ledger.tsv').open('x', encoding='utf-8', newline='\n')
        self.flags.write('table\trow\tfinite_mask\n')
        self.schemas = {}
        self.nonfinite = 0

    def frame(self, name, frame, index=False):
        import pandas as pd
        columns = list(frame.columns)
        numerical = [i for i, dtype in enumerate(frame.dtypes)
                     if pd.api.types.is_numeric_dtype(dtype) and not pd.api.types.is_bool_dtype(dtype)]
        schema = dict(table=name, columns=columns, dtypes=[str(v) for v in frame.dtypes],
                      shape=list(frame.shape), index_included=index,
                      numerical_columns=[columns[i] for i in numerical])
        if index:
            schema.update(index_name=frame.index.name, index_dtype=str(frame.index.dtype))
        self.schemas[name] = schema
        self.values.write(json.dumps(schema, ensure_ascii=False, allow_nan=False, sort_keys=True) + '\n')
        for ordinal, row in enumerate(frame.itertuples(index=False, name=None)):
            data = dict(table=name, row=ordinal, values=[scalar(v) for v in row])
            if index:
                data['index'] = scalar(frame.index[ordinal])
            self.values.write(json.dumps(data, ensure_ascii=False, allow_nan=False, sort_keys=True) + '\n')
            finite = [math.isfinite(float(row[i])) for i in numerical]
            self.nonfinite += sum(not v for v in finite)
            self.flags.write(name + '\t' + str(ordinal) + '\t' + ''.join('1' if v else '0' for v in finite) + '\n')

    def close(self):
        self.values.close()
        self.flags.close()


def execute(root, request, report):
    import anndata as ad
    import numpy as np
    import pandas as pd
    from scipy import sparse
    from pycisTopic.cistopic_class import CistopicObject
    from pycisTopic.lda_models import CistopicLDAModel
    from scenicplus.data_wrangling.adata_cistopic_wrangling import process_non_multiome_data
    limits, options = request['limits'], request['options']
    closed_h5ad(root / 'inputs/RNA.h5ad', limits)
    rna = ad.read_h5ad(root / 'inputs/RNA.h5ad')
    # Deliberately original trusted upstream format. Loading untrusted pickles is unsafe.
    with (root / 'inputs/TRUSTED-CISTOPIC.pkl').open('rb') as stream:
        obj = pickle.load(stream)
    common.require(type(obj) is CistopicObject, 'Require an original trusted CistopicObject')
    common.require(type(obj.selected_model) is CistopicLDAModel, 'Select a genuine original fitted topic model before preparation; no dummy metrics are made')
    def labels(values, name):
        data = list(values)
        common.require(data and len(data) == len(set(data)), 'Empty/duplicate ' + name)
        for value in data:common.label(value)
        return data
    rna_cells = labels(rna.obs_names, 'RNA cells')
    atac_cells = labels(obj.cell_names, 'ATAC cells')
    regions = labels(obj.region_names, 'ATAC regions')
    common.require(list(obj.cell_data.index) == atac_cells and list(obj.region_data.index) == regions, 'Original cisTopic names and metadata axes differ; no reordering')
    if options['use_raw']:
        common.require(rna.raw is not None, 'use_raw=true requires RNA.raw; supply original raw data or explicitly choose processed RNA in a new request')
        gene_names = labels(rna.raw.var_names, 'raw RNA genes')
        values = rna.raw.X
    else:
        gene_names = labels(rna.var_names, 'processed RNA genes')
        values = rna.X
    common.require(len(rna_cells) <= limits['maximum_cells'] and len(atac_cells) <= limits['maximum_cells'] and len(gene_names) <= limits['maximum_genes'] and len(regions) <= limits['maximum_regions'], 'Input axis capacity exceeded')
    common.require(len(rna_cells)*len(gene_names) <= limits['maximum_matrix_values'] and len(atac_cells)*len(regions) <= limits['maximum_matrix_values'], 'Input matrix value capacity exceeded')
    common.require(np.isfinite(values.data if sparse.issparse(values) else values).all(), 'Nonfinite supplied RNA expression; no substitution')
    model = obj.selected_model
    for name in ('metrics','coherence','marg_topic','topic_ass','cell_topic','topic_region','parameters'):
        common.require(isinstance(getattr(model,name),pd.DataFrame), 'Missing original model component: ' + name)
    common.require(list(model.cell_topic.columns) == atac_cells and list(model.topic_region.index) == regions and list(model.cell_topic.index) == list(model.topic_region.columns), 'Selected original model axes differ; no model repair')
    for frame in (model.cell_topic,model.topic_region):
        common.require(np.isfinite(frame.to_numpy()).all(), 'Nonfinite selected topic model')
    key, sep = options['group_key'],options['separator']
    common.require(key in rna.obs and key in obj.cell_data, 'Grouping key must exist in both assays')
    rna_groups,atac_groups = list(rna.obs[key]),list(obj.cell_data[key])
    for group in rna_groups+atac_groups:common.label(group)
    shared = sorted(set(rna_groups)&set(atac_groups))
    common.require(shared, 'No shared annotation groups; dependent unpaired preparation cannot run')
    common.require(all(sep not in group for group in shared), 'Shared group contains metacell separator; original v1.0a2 truncates returned group metadata. Choose an absent separator explicitly or skip; labels are never rewritten')
    counts=options['nr_metacells'];per=options['cells_per_metacell']
    common.require(not isinstance(counts,dict) or set(counts)==set(shared), 'nr_metacells mapping must name exactly the shared groups')
    common.require(all(min(rna_groups.count(g),atac_groups.count(g))>=per for g in shared), 'Insufficient group cells for sampling without replacement; reduce explicitly or skip')
    expected_count=sum((counts[g] if isinstance(counts,dict) else counts) if counts is not None else round(min(rna_groups.count(g),atac_groups.count(g))/per)*2 for g in shared)
    common.require(0 < expected_count <= limits['maximum_cells'] and expected_count*max(len(gene_names),len(regions)) <= limits['maximum_matrix_values'], 'Output metacell/matrix capacity exceeded or empty automatic metacells')
    out = root / 'worker/out';out.mkdir()
    invocation = dict(api='scenicplus.data_wrangling.adata_cistopic_wrangling.process_non_multiome_data', options=options,imputed_acc_kwargs={'scale_factor':options['scale_factor']},source_fixed_ordinal_seed=True,membership_exposed_by_api=False,model_fitted_by_adapter=False)
    common.write(out/'invocation.json',invocation)
    result = process_non_multiome_data(GEX_anndata=rna,cisTopic_obj=obj,key_to_group_by=key,use_raw_for_GEX_anndata=options['use_raw'],nr_metacells=counts,nr_cells_per_metacells=per,meta_cell_split=sep,imputed_acc_kwargs={'scale_factor':options['scale_factor']})
    common.require(list(result.mod)==['scRNA','scATAC'] and result.n_obs==expected_count, 'Unexpected original modalities/metacell count')
    common.require(list(result['scRNA'].obs_names)==list(result['scATAC'].obs_names) and list(result['scRNA'].var_names)==gene_names and list(result['scATAC'].var_names)==regions, 'Original output axes differ; no correction applied')
    result.write_h5mu(out/'prepared.h5mu')
    export=Exports(out)
    try:
        for modality,filename in [('scRNA','expression.tsv'),('scATAC','accessibility.tsv')]:
            frame=result[modality].to_df();frame.to_csv(out/filename,sep='\t');export.frame(modality,frame,index=True)
            export.frame(modality+'.obs',result[modality].obs,index=True)
            export.frame(modality+'.var',result[modality].var,index=True)
        export.frame('joint.obs',result.obs,index=True)
    finally:export.close()
    summary=dict(status='ORIGINAL_UNPAIRED_PREPARATION_RETURNED',RNA_shape=list(result['scRNA'].shape),ATAC_shape=list(result['scATAC'].shape),use_raw=options['use_raw'],shared_groups=shared,omitted_RNA_groups=sorted(set(rna_groups)-set(shared)),omitted_ATAC_groups=sorted(set(atac_groups)-set(shared)),RNA_input_cells=len(rna_cells),ATAC_input_cells=len(atac_cells),shared_input_barcodes=len(set(rna_cells)&set(atac_cells)),RNA_retained_cells=sum(g in shared for g in rna_groups),ATAC_retained_cells=sum(g in shared for g in atac_groups),nonfinite_numerical_fields=export.nonfinite,model_fitted_by_adapter=False,model_fit_authentication=False,membership_reconstructed=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False)
    common.require(export.nonfinite==0,'Original preparation produced nonfinite fields; evidence retained, no complete result')
    common.write(out/'SUMMARY.json',summary)
    return summary


def main():
    root = Path(sys.argv[1]).resolve()
    worker = root / 'worker'
    request = json.loads((worker / 'request.json').read_bytes())
    profile = json.loads(Path(__file__).with_name('SOURCE-PROFILE.json').read_bytes())
    report = dict(status='PREFLIGHT', original_call_returned=False, results_admitted=False, private_capture=True)
    observed = []
    try:
        sys.addaudithook(common.network_policy)
        with warnings.catch_warnings(record=True) as observed:
            warnings.simplefilter('always')
            common.write(worker / 'runtime.json', common.runtime(profile))
            (worker / 'READY.pending').write_bytes(b'SCENICPLUS_PREFLIGHT_V1\n')
            (worker / 'READY.pending').replace(worker / 'READY')
            deadline = time.monotonic() + request['limits']['timeout_seconds']
            while not (worker / 'ACK').is_file():
                common.require(time.monotonic() < deadline, 'Java acknowledgement deadline expired')
                time.sleep(.025)
            common.require((worker / 'ACK').read_bytes() == b'SCENICPLUS_ACK_V1\n', 'Invalid Java acknowledgement')
            report['summary'] = execute(root, request, report)
            report['original_call_returned'] = True
            common.write(worker / 'runtime-after-info.json', common.runtime(profile))
            report['status'] = 'ORIGINAL_RETURNED_PENDING_JAVA_AUDIT'
        code = 0
    except common.Unavailable as error:
        report.update(status='SKIP3_DEPENDENT_ANALYSIS_SKIPPED', cause=str(error), private_traceback=traceback.format_exc())
        code = 3
    except Exception as error:
        report.update(status='FAIL2_NO_COMPLETE_RESULT', cause=str(error), private_traceback=traceback.format_exc())
        code = 2
    report['warnings'] = [dict(category=w.category.__name__, message=str(w.message), source_file=Path(w.filename).name, line=w.lineno) for w in observed]
    common.write(worker / 'report.json', report)
    return code


if __name__ == '__main__':
    sys.exit(main())
