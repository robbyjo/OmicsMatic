"""Own original SCENIC+/pycisTarget orchestration; vendor code stays private and unchanged."""
from pathlib import Path
import csv, json, math, numbers, sys, time, traceback, warnings
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
    if isinstance(value, bytes):
        return {'source_kind':'bytes','hex':value.hex()}
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


def region_sets(path, limits):
    import pandas as pd
    import pyranges as pr
    groups,seen={},set()
    with path.open(encoding='utf-8',newline='') as stream:
        reader=csv.reader(stream,delimiter='\t',quoting=csv.QUOTE_NONE,strict=True)
        common.require(next(reader)==['set','Chromosome','Start','End'],'Require exact set/Chromosome/Start/End TSV header')
        for row in reader:
            common.require(len(row)==4,'Ragged region set row')
            group,chrom,start,end=row;common.label(group);common.label(chrom)
            common.require(':' not in chrom,'Chromosome containing colon is ambiguous in original coordinate serialization; no identifier rewrite')
            left,right=int(start),int(end)
            common.require(str(left)==start and str(right)==end and 0<=left<right<2**63,'Canonical zero-based half-open int64 coordinates required')
            common.require(tuple(row) not in seen and len(seen)<limits['maximum_pairs'],'Duplicate/capacity-exceeding region membership')
            seen.add(tuple(row));groups.setdefault(group,[]).append((chrom,left,right))
    common.require(groups,'Require nonempty explicitly named region sets')
    return {key:pr.PyRanges(pd.DataFrame(rows,columns=['Chromosome','Start','End'])) for key,rows in groups.items()}


def reference_database(path, kind, limits):
    import pyarrow as pa
    import pyarrow.ipc as ipc
    import numpy as np
    with pa.memory_map(str(path),'r') as source:
        reader=ipc.open_file(source);names=reader.schema.names
        common.require(len(names)==len(set(names)) and names.count('motifs')==1,'Unique Feather-v2 columns and explicit motifs index required')
        regions=[name for name in names if name!='motifs']
        common.require(0<len(regions)<=limits['maximum_regions'],'Database region capacity exceeded')
        for region in regions:common.label(region)
        count=0;motifs=[]
        for i in range(reader.num_record_batches):
            batch=reader.get_batch(i);count+=batch.num_rows
            common.require(count<=limits['maximum_genes'] and count*len(regions)<=limits['maximum_matrix_values'],'Database motif/matrix capacity exceeded')
            motifs.extend(batch.column(names.index('motifs')).to_pylist())
            for j,name in enumerate(names):
                if name=='motifs':continue
                column=batch.column(j)
                common.require(column.null_count==0,'Null motif rank/score; no substitution')
                values=column.to_numpy(zero_copy_only=False)
                if kind=='rankings':common.require(pa.types.is_integer(column.type) and np.all((values>=0)&(values<len(regions))),'Original zero-based integer ranking range required')
                else:common.require(pa.types.is_floating(column.type) and np.isfinite(values).all(),'Finite original floating-point scores required')
        common.require(motifs and len(motifs)==len(set(motifs)),'Nonempty unique motif identifiers required')
        for motif in motifs:
            common.label(motif);common.require('/' not in motif,'Motif slash is ambiguous in original HDF5 groups; no rewrite')
    return dict(motifs=motifs,regions=regions,motif_count=count,region_count=len(regions),ranking_permutation_authentication=False)


def literal_annotations(path):
    import pandas as pd
    required=['#motif_id','gene_name','motif_similarity_qvalue','orthologous_identity','description']
    literal=pd.read_csv(path,sep='\t',dtype=str,keep_default_na=False)
    common.require(set(required).issubset(literal.columns),'Missing original motif annotation columns')
    # Audit original parser semantics, then let the original implementation read unchanged bytes.
    original=pd.read_csv(path,sep='\t',usecols=required)
    for name in ('#motif_id','gene_name','description'):
        common.require(list(original[name])==list(literal[name]),'Original annotation parser converts literal '+name+'; no ID coercion or repair')
        for value in literal[name]:common.label(value)
    common.require(not any(', ' in v for v in literal['gene_name']),'Original TF delimiter is ambiguous in a literal gene name')
    return literal


def container_views(capture, destination, options, limits):
    """Compare HDF5 logical arrays/attributes via h5py; never unpickle saved tables."""
    import h5py
    import hashlib
    import numpy as np
    destination.mkdir()
    actual=capture/'worker/out'
    def value(data):
        if isinstance(data,h5py.Empty):return {'source_kind':'h5py.Empty','dtype':str(data.dtype)}
        if isinstance(data,np.void):
            if data.dtype.names:return {'source_kind':'numpy.void','dtype':str(data.dtype),'fields':[(name,value(data[name])) for name in data.dtype.names]}
            common.require(not data.dtype.hasobject,'Unsupported opaque HDF5 object value')
            return {'source_kind':'numpy.void','dtype':str(data.dtype),'bytes_hex':data.tobytes().hex()}
        if isinstance(data,np.ndarray):
            return dict(dtype=str(data.dtype),shape=list(data.shape),values=value(data.tolist()))
        if isinstance(data,(list,tuple)):return [value(v) for v in data]
        return scalar(data)
    with (destination/'logical-hdf5.jsonl').open('x',encoding='utf-8',newline='\n') as stream:
        def emit(record):stream.write(json.dumps(record,ensure_ascii=False,allow_nan=False,sort_keys=True)+'\n')
        paths=[(a['name']+'/original-result.hdf5',a['name']+'/metadata/path_to_motif_annotations') for a in options['analyses']]
        paths += [(kind+'-cistromes.h5ad',None) for kind in ('direct','extended')]
        for name,annotation_dataset in paths:
            path=actual/name
            if not path.exists():
                emit(dict(file=name,original_result_present=False));continue
            closed_h5ad(path,limits)
            seen={};total=0
            with h5py.File(path,'r') as handle:
                def visit(node,logical):
                    nonlocal total
                    address=h5py.h5o.get_info(node.id).addr
                    if address in seen:
                        emit(dict(file=name,path=logical,hardlink_to=seen[address]));return
                    seen[address]=logical
                    attrs={key:value(node.attrs[key]) for key in sorted(node.attrs)}
                    record=dict(file=name,path=logical,attributes=attrs)
                    if isinstance(node,h5py.Group):
                        emit(dict(record,kind='GROUP'))
                        for child in sorted(node):visit(node[child],logical+'/'+child)
                        return
                    total+=node.size
                    common.require(total<=limits['maximum_matrix_values'],'Serialized HDF5 logical-value capacity exceeded')
                    emit(dict(record,kind='DATASET',dtype=str(node.dtype),shape=list(node.shape)))
                    if logical.lstrip('/')==annotation_dataset:
                        emit(dict(file=name,path=logical,captured_annotation_input=common.identity(capture/'inputs/motif-annotations.tbl'),absolute_path_excluded_from_scientific_comparison=True))
                    else:
                        digest=hashlib.sha256();size=0
                        def consume(block):
                            nonlocal size
                            if node.dtype.hasobject:raw=(json.dumps(value(block),ensure_ascii=False,allow_nan=False,sort_keys=True)+'\n').encode('utf-8')
                            else:raw=np.asarray(block,dtype=node.dtype).tobytes(order='C')
                            digest.update(raw);size+=len(raw)
                        if not node.shape:consume(node[()])
                        else:
                            row_bytes=max(1,math.prod(node.shape[1:])*node.dtype.itemsize)
                            rows=max(1,1048576//row_bytes)
                            for begin in range(0,node.shape[0],rows):consume(node[begin:min(begin+rows,node.shape[0])])
                        emit(dict(file=name,path=logical,logical_values_sha256=digest.hexdigest(),logical_encoding_bytes=size,encoding='typed-json-blocks' if node.dtype.hasobject else 'dtype-preserving-C-array-bytes',complete_logical_array_read=True))
                visit(handle,'')


def execute(root, request, report):
    import anndata as ad
    import mudata as md
    import pandas as pd
    import numpy as np
    from scenicplus.cli.commands import _run_cistarget_single_region_set,_run_dem_single_region_set
    from scenicplus.data_wrangling.cistarget_wrangling import get_and_merge_cistromes
    options,limits=request['options'],request['limits'];inputs=root/'inputs';out=root/'worker/out';out.mkdir()
    closed_h5ad(inputs/'prepared.h5mu',limits);prepared=md.read_h5mu(inputs/'prepared.h5mu')
    common.require('scRNA' in prepared.mod and 'scATAC' in prepared.mod,'Require original scRNA/scATAC prepared MuData')
    common.require(all(prepared[modality].n_obs<=limits['maximum_cells'] for modality in ('scRNA','scATAC')),'Prepared cell capacity exceeded')
    regions=list(prepared['scATAC'].var_names);genes=list(prepared['scRNA'].var_names)
    for values,cap in ((regions,limits['maximum_regions']),(genes,limits['maximum_genes'])):
        common.require(values and len(values)<=cap and len(values)==len(set(values)),'Empty/duplicate/capacity-exceeding prepared feature axis')
        for value in values:common.label(value)
    sets=region_sets(inputs/'region-sets.tsv',limits);annotations=literal_annotations(inputs/'motif-annotations.tbl')
    required={a['method'] for a in options['analyses']};databases={}
    if 'CISTARGET' in required:databases['CISTARGET']=reference_database(inputs/'database.regions_vs_motifs.rankings.feather','rankings',limits)
    if 'DEM' in required:databases['DEM']=reference_database(inputs/'database.regions_vs_motifs.scores.feather','scores',limits)
    genome=None
    if (inputs/'genome-annotation.tsv').is_file():
        genome=pd.read_csv(inputs/'genome-annotation.tsv',sep='\t',dtype={'Chromosome':str},keep_default_na=False)
        common.require({'Chromosome','Start'}.issubset(genome.columns) and len(genome)<=limits['maximum_genes'],'Original genome annotation Chromosome/Start and bounded rows required')
        for value in genome['Chromosome']:common.label(value)
        common.require(pd.api.types.is_integer_dtype(genome['Start']) and (genome['Start']>=0).all(),'Integer original TSS Start coordinates required')
    invocation=dict(api='Original SCENIC+ CLI single-region scientific helpers, then original get_and_merge_cistromes',options=options,n_cpu=1,raw_reference_bytes_preserved=True,ClusterBuster_scanning='SKIP3_NOT_INCLUDED_PERMISSION_UNESTABLISHED',author_parameters_inferred=False)
    common.write(out/'invocation.json',invocation);exports=Exports(out);results=[];hdf=[];comparisons=[];warnings_specific=[]
    try:
        for analysis in options['analyses']:
            name,method=analysis['name'],analysis['method'];foreground=analysis['foreground'];background=analysis.get('background',[])
            common.require(set(foreground+background).issubset(sets),'Unknown foreground/background region set; no dropping')
            dest=out/name;dest.mkdir();report['current_analysis']=name
            common_options=dict(name=name,species=options['species'],path_to_motif_annotations=str(inputs/'motif-annotations.tbl'),annotation_version=options['annotation_version'],annotations_to_use=options['annotations_to_use'],motif_similarity_fdr=options['motif_similarity_fdr'],orthologous_identity_threshold=options['orthologous_identity_threshold'])
            if method=='CISTARGET':
                result=_run_cistarget_single_region_set(cistarget_db_fname=str(inputs/'database.regions_vs_motifs.rankings.feather'),region_set=sets[foreground[0]],fraction_overlap_w_cistarget_database=options['fraction_overlap'],**analysis['ctx'],**common_options)
                record=dict(name=name,method=method,enriched_motifs=len(result.motif_enrichment),mapped_rows=len(result.regions_to_db),input_regions=len(sets[foreground[0]]))
            else:
                d=analysis['dem']
                result=_run_dem_single_region_set(foreground_region_sets=[sets[key] for key in foreground],background_region_sets=[sets[key] for key in background],dem_db_fname=str(inputs/'database.regions_vs_motifs.scores.feather'),genome_annotation=genome,fraction_overlap_w_dem_database=options['fraction_overlap'],**d,**common_options)
                actual_fg,actual_bg=len(result.foreground_regions),len(result.background_regions)
                ignored=d['max_bg_regions'] is not None and not d['balance_number_of_promoters'] and actual_bg>d['max_bg_regions']
                record=dict(name=name,method=method,enriched_motifs=len(result.motif_enrichment),mapped_rows=len(result.regions_to_db),actual_foreground_regions=actual_fg,actual_background_regions=actual_bg,requested_max_bg_regions=d['max_bg_regions'],source_ignored_background_cap=ignored,pvalue_adjustment='Original pinned source p_adjust_bh; no documentation-based substitution',automatic_hit_threshold_exposed_by_returned_table='Motif_hit_thr' in result.motif_enrichment)
                if ignored:warnings_specific.append(dict(analysis=name,code='ORIGINAL_DEM_BACKGROUND_CAP_IGNORED',message='Original source with promoter balancing off used '+str(actual_bg)+' background regions despite requested '+str(d['max_bg_regions'])+'; results use the actual original background. No source repair or silent subsampling.',required_action='Review the actual background; use original promoter balancing with explicit annotation if scientifically appropriate, or retain this limitation/skip the dependent analysis.'))
            result.motif_enrichment.to_csv(dest/'enrichment.tsv',sep='\t');result.regions_to_db.to_csv(dest/'mapping.tsv',sep='\t',index=False)
            exports.frame(name+'.enrichment',result.motif_enrichment,index=True);exports.frame(name+'.mapping',result.regions_to_db,index=False)
            if len(result.motif_enrichment):
                common.write(dest/'motif-hits.json',result.motif_hits);common.write(dest/'cistromes.json',result.cistromes)
                path=dest/'original-result.hdf5';result.write_hdf5(str(path));hdf.append(str(path))
                comparisons += [name+'/'+n for n in ('motif-hits.json','cistromes.json')]
                record['outcome']='ORIGINAL_ENRICHMENT_RETURNED'
            else:
                record['outcome']='ORIGINAL_NO_ENRICHED_MOTIFS';warnings_specific.append(dict(analysis=name,code='EMPTY_ORIGINAL_ENRICHMENT',message='Original call returned no enriched motifs. No motif hits or HDF5 result are invented.',required_action='Review the declared region sets, database and thresholds; retain the empty result or explicitly rerun changed scientific settings.'))
            comparisons += [name+'/'+n for n in ('enrichment.tsv','mapping.tsv')];results.append(record)
        report['current_analysis']='ORIGINAL_CISTROME_MERGE'
        try:
            direct,extended=get_and_merge_cistromes(paths_to_motif_enrichment_results=hdf,scplus_regions=set(regions),direct_annotation=options['direct_annotation'],extended_annotation=options['extended_annotation'])
        except TypeError as error:
            if 'set.union' in str(error):
                raise RuntimeError('Original SCENIC+ cistrome merge cannot represent an empty direct/extended category in this pinned version. No replacement cistrome is invented. Retain enrichment evidence and skip the dependent network, or choose justified inputs/settings in a NEW run. Original cause: '+str(error)) from error
            raise
        for kind,obj in [('direct',direct),('extended',extended)]:
            common.require(obj.X.dtype==np.dtype(bool),'Original merged cistromes are not Boolean; no cast')
            common.require(obj.n_obs<=limits['maximum_regions'] and obj.n_vars<=limits['maximum_genes'] and obj.n_obs*obj.n_vars<=limits['maximum_matrix_values'],'Merged cistrome capacity exceeded')
            obj.write_h5ad(out/(kind+'-cistromes.h5ad'));obj.to_df().to_csv(out/(kind+'-cistromes.tsv'),sep='\t');obj.var.to_csv(out/(kind+'-metadata.tsv'),sep='\t')
            exports.frame(kind+'.cistromes',obj.to_df(),index=True);exports.frame(kind+'.var',obj.var,index=True)
            comparisons += [kind+'-cistromes.tsv',kind+'-metadata.tsv']
        tfs=list(({*direct.var_names,*extended.var_names})&set(genes))
        with (out/'tfs.txt').open('x',encoding='utf-8',newline='\n') as stream:stream.write(''.join(tf+'\n' for tf in tfs))
        common.write(out/'tf-selection.json',dict(original_union_intersection=tfs,absent_from_expression=list(({*direct.var_names,*extended.var_names})-set(genes)),no_sort_or_identifier_repair=True));comparisons += ['tfs.txt','tf-selection.json']
        if options['export_network_inputs']:
            common.require(list(prepared['scRNA'].obs_names)==list(prepared['scATAC'].obs_names),'Network export requires identical ordered RNA/ATAC cells; no inferred pairing, intersection or sorting')
            bridge={}
            for modality,name in [('scRNA','network-expression.tsv'),('scATAC','network-accessibility.tsv')]:
                obj=prepared[modality]
                common.require(obj.n_obs*obj.n_vars<=limits['maximum_matrix_values'],'Explicit network export matrix capacity exceeded')
                frame=obj.to_df();array=frame.to_numpy();kind=array.dtype.kind
                common.require(kind in 'biuf' and array.dtype.itemsize<=8,'Network float64 export supports original Boolean/integer/IEEE float16/32/64 values; no string or extended-precision coercion')
                if kind in 'iu':common.require(np.all(array>=-2**53) and np.all(array<=2**53),'Integer outside guaranteed exact float64 promotion range; no lossy export')
                common.require(np.isfinite(array).all(),'Network export has nonfinite source values; no substitution')
                promoted=frame.astype('float64');promoted.to_csv(out/name,sep='\t',index_label='cell');exports.frame('network-export.'+modality,promoted,index=True);comparisons.append(name)
                bridge[modality]=dict(shape=list(frame.shape),original_dtype=str(array.dtype),export_dtype='float64',exact_numeric_promotion=True,cell_header='cell',ordered_cell_and_feature_labels_preserved=True,normalization_added=False)
            common.write(out/'network-export-bridge.json',bridge);comparisons.append('network-export-bridge.json')
    finally:exports.close()
    container_views(root,out/'container-views',options,limits)
    comparisons += ['container-views/'+name for name in ('logical-hdf5.jsonl',)]
    if request.get('parent_analysis') is not None:
        parent=Path(request['parent_analysis']);container_views(parent,out/'saved-container-views',options,limits)
        for name in ('logical-hdf5.jsonl',):
            common.require(common.identity(out/'container-views'/name)==common.identity(out/'saved-container-views'/name),'Fresh serialized-container scientific comparison differs: '+name)
    common.write(out/'results.json',results);common.write(out/'source-warnings.json',warnings_specific);comparisons.append('source-warnings.json');common.write(out/'comparison-files.json',dict(files=comparisons))
    summary=dict(status='ORIGINAL_MOTIFS_AND_CISTROMES_RETURNED',analyses=len(results),empty_analyses=sum(not row['enriched_motifs'] for row in results),nonfinite_numerical_fields=exports.nonfinite,background_cap_warnings=sum(v['code']=='ORIGINAL_DEM_BACKGROUND_CAP_IGNORED' for v in warnings_specific),direct_shape=list(direct.shape),extended_shape=list(extended.shape),TFs_present_in_expression=len(tfs),network_inputs_exported=options['export_network_inputs'],ClusterBuster_scanning='SKIP3_NOT_EXECUTED_PERMISSION_UNESTABLISHED',genome_scoring_executed=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False)
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
