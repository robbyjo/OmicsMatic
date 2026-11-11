# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Unchanged-original Hail/Hail Batch operations from a declared request. No installs, fallback release, inferred masks or cloud submission."""
import hashlib,json,math,operator,re,shlex,sys,traceback
from pathlib import Path
import hail as hl

def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=True,allow_nan=False)+'\n',encoding='utf8')

def encode(value):
    if value is None:return dict(state='missing')
    if isinstance(value,hl.Struct):return {key:encode(value[key]) for key in value}
    if isinstance(value,hl.Call):return dict(type='call',alleles=list(value.alleles),phased=value.phased)
    if isinstance(value,hl.Locus):return dict(type='locus',contig=value.contig,position=value.position,reference_genome=value.reference_genome.name)
    if isinstance(value,hl.Interval):return dict(type='interval',start=encode(value.start),end=encode(value.end),includes_start=value.includes_start,includes_end=value.includes_end)
    if isinstance(value,dict):return dict(type='dict',entries=[[encode(key),encode(item)] for key,item in value.items()])
    if isinstance(value,(set,frozenset)):return dict(type='set',values=sorted([encode(v) for v in value],key=lambda v:json.dumps(v,sort_keys=True)))
    if isinstance(value,(tuple,list)):return [encode(v) for v in value]
    if isinstance(value,float):
        state='NaN' if math.isnan(value) else '+Inf' if value==math.inf else '-Inf' if value==-math.inf else 'value';return dict(state=state,text=repr(value) if state=='value' else state)
    if isinstance(value,(str,int,bool)):return value
    raise TypeError('Unsupported original value type; retain native Hail files, do not omit fields: '+str(type(value)))

def keys(value,expected):
    if not isinstance(value,dict) or set(value)!=set(expected):raise ValueError('Unexpected/missing request fields: '+str(set(value) if isinstance(value,dict) else type(value))+'; expected '+str(expected))

def identifier(value):
    if not isinstance(value,str) or not re.fullmatch('[A-Za-z][A-Za-z0-9_-]{0,63}',value):raise ValueError('Require a unique portable operation/resource identifier')
    return value

def integer(value,low,high):
    if type(value)!=int or not low<=value<=high:raise ValueError('Expected complete integer within declared capacity')
    return value

def boolean(value):
    if type(value)!=bool:raise ValueError('Expected explicit Boolean')
    return value

def bounded(value,low,high):
    if type(value) not in (int,float) or not math.isfinite(value) or not low<=value<=high:raise ValueError('Expected finite explicit number within declared bounds')
    return value

def local(value,kind):
    path=Path(value)
    if not path.is_absolute() or path.resolve()!=path or path.is_symlink() or (kind=='file' and not path.is_file()) or (kind=='directory' and not path.is_dir()):raise ValueError('Require captured canonical local '+kind+': '+str(path))
    return path

def expression(node,mt):
    """Small typed expression grammar; calls original Hail operators without eval or Python source injection."""
    if not isinstance(node,dict):raise ValueError('Expression nodes must be typed objects')
    if set(node)=={'literal'}:
        value=node['literal']
        if value is None:raise ValueError('Missing literals require an explicit Hail dtype')
        return hl.literal(value)
    if set(node)=={'missing'}:return hl.missing(hl.dtype(node['missing']))
    if set(node)=={'field'}:
        field=node['field'];keys(field,['axis','path']);axes=dict(entry=mt.entry,row=mt.row,col=mt.col,global_=mt.index_globals());axis=field['axis'];axis='global_' if axis=='global' else axis
        if axis not in axes or not isinstance(field['path'],list) or not field['path']:raise ValueError('Field requires an explicit entry/row/col/global axis and nonempty path')
        value=axes[axis]
        for part in field['path']:
            if not isinstance(part,str):raise ValueError('Struct field path elements must be strings')
            if isinstance(value,hl.expr.LocusExpression) and part in ['position','contig']:value=getattr(value,part)
            elif isinstance(value,hl.expr.IntervalExpression) and part in ['start','end','includes_start','includes_end']:value=getattr(value,part)
            else:value=value[part]
        return value
    if set(node)=={'index','at'}:return expression(node['index'],mt)[integer(node['at'],0,1000000)]
    binary={'add':operator.add,'subtract':operator.sub,'multiply':operator.mul,'divide':operator.truediv,'lt':operator.lt,'le':operator.le,'gt':operator.gt,'ge':operator.ge,'eq':operator.eq,'ne':operator.ne,'and':operator.and_,'or':operator.or_}
    if set(node)=={'operator','left','right'} and node['operator'] in binary:return binary[node['operator']](expression(node['left'],mt),expression(node['right'],mt))
    if set(node)=={'operator','arg'} and node['operator']=='not':return ~expression(node['arg'],mt)
    functions={'is_defined':hl.is_defined,'is_missing':hl.is_missing,'abs':hl.abs,'sqrt':hl.sqrt,'len':hl.len,'min':hl.min,'max':hl.max,'float64':hl.float64,'int32':hl.int32,'str':hl.str}
    if set(node)=={'function','args'} and node['function'] in functions:return functions[node['function']](*[expression(a,mt) for a in node['args']])
    if set(node)=={'call_method','arg'} and node['call_method'] in ['is_het','is_hom_ref','is_hom_var','is_non_ref','n_alt_alleles']:return getattr(expression(node['arg'],mt),node['call_method'])()
    raise ValueError('Unsupported typed original expression; no generic substitute: '+repr(node))

def limits(request):
    value=request['limits'];keys(value,['maximum_rows','maximum_columns','maximum_entries','maximum_dense_ld_cells']);return {k:integer(v,1,1000000000) for k,v in value.items()}

def check_matrix(mt,capacity):
    rows,cols=mt.count()
    if rows>capacity['maximum_rows'] or cols>capacity['maximum_columns'] or rows*cols>capacity['maximum_entries']:raise ValueError('Complete matrix capacity exceeded; no truncation/subsampling or partial result is admitted')
    return rows,cols

def table_snapshot(table,out,stem,maximum):
    size=table.count()
    if size>maximum:raise ValueError('Complete table capacity exceeded: '+stem)
    table.write(str(out/(stem+'.ht')))
    values=table.collect();assert len(values)==size;save(out/(stem+'.json'),dict(schema=str(table.row.dtype),key=str(table.key.dtype),globals_schema=str(table.globals.dtype),globals=encode(hl.eval(table.index_globals())),rows=[encode(v) for v in values]));return size

def matrix_value(mt,capacity):
    nrows,ncols=check_matrix(mt,capacity);rows_name='__omicsmatic_entries';cols_name='__omicsmatic_columns'
    if rows_name in mt.row or cols_name in mt.globals:raise ValueError('Input uses a reserved snapshot field; choose a separate capture rather than overwrite it')
    localized=mt.localize_entries(rows_name,cols_name);columns=hl.eval(localized.index_globals()[cols_name]);rows=localized.collect();assert len(rows)==nrows and len(columns)==ncols
    for row in rows:assert len(row[rows_name])==ncols
    return dict(row_schema=str(mt.row.dtype),row_key_schema=str(mt.row_key.dtype),col_schema=str(mt.col.dtype),col_key_schema=str(mt.col_key.dtype),entry_schema=str(mt.entry.dtype),globals_schema=str(mt.globals.dtype),globals=encode(hl.eval(mt.index_globals())),rows=[encode(row) for row in rows],columns=[encode(col) for col in columns],row_count=nrows,column_count=ncols)

def matrix_snapshot(mt,out,capacity):
    value=matrix_value(mt,capacity);save(out/'matrix-complete.json',value);mt.write(str(out/'result.mt'));return dict(rows=value['row_count'],columns=value['column_count'],entries=value['row_count']*value['column_count'])

def pipeline(case,out,capacity):
    keys(case,['id','kind','input','steps']);source=case['input'];steps=case['steps']
    if source['kind']=='vcf':
        keys(source,['kind','path','reference_genome','force_bgz','contig_recoding']);local(source['path'],'file');mt=hl.import_vcf(source['path'],reference_genome=source['reference_genome'],force_bgz=boolean(source['force_bgz']),contig_recoding=source['contig_recoding'])
    elif source['kind']=='matrix-table':keys(source,['kind','path']);local(source['path'],'directory');mt=hl.read_matrix_table(source['path'])
    else:raise ValueError('Input kind must be vcf or matrix-table; unsupported formats are not converted implicitly')
    check_matrix(mt,capacity);seen=set();reports=[]
    for step in steps:
        id=identifier(step['id'])
        if id in seen:raise ValueError('Repeated operation identifier')
        seen.add(id);operation=step['operation'];folder=out/id;folder.mkdir()
        if operation in ['sample_qc','variant_qc']:
            keys(step,['id','operation','name']);name=identifier(step['name']);mt=getattr(hl,operation)(mt,name=name);table=mt.cols() if operation=='sample_qc' else mt.rows();table_snapshot(table,folder,'metrics',capacity['maximum_columns'] if operation=='sample_qc' else capacity['maximum_rows'])
        elif operation in ['filter_rows','filter_cols','filter_entries']:
            keys(step,['id','operation','predicate','keep']);mt=getattr(mt,operation)(expression(step['predicate'],mt),keep=boolean(step['keep']))
        elif operation=='annotate':
            keys(step,['id','operation','axis','fields']);axis=step['axis']
            if axis not in ['rows','cols','entries','globals'] or not isinstance(step['fields'],dict):raise ValueError('Declare the original annotation axis and field family')
            fields={identifier(name):expression(value,mt) for name,value in step['fields'].items()};mt=getattr(mt,'annotate_'+axis)(**fields)
        elif operation in ['select_rows','select_cols','select_entries','select_globals','key_rows_by','key_cols_by']:
            keys(step,['id','operation','fields']);assert isinstance(step['fields'],list) and all(isinstance(name,str) for name in step['fields']);mt=getattr(mt,operation)(*step['fields'])
        elif operation=='ld_prune':
            keys(step,['id','operation','r2','bp_window_size','memory_per_core','keep_higher_maf','block_size']);pruned=hl.ld_prune(mt.GT,r2=bounded(step['r2'],0,1),bp_window_size=integer(step['bp_window_size'],1,1000000000),memory_per_core=integer(step['memory_per_core'],1,16384),keep_higher_maf=boolean(step['keep_higher_maf']),block_size=integer(step['block_size'],1,4096));table_snapshot(pruned,folder,'pruned-variants',capacity['maximum_rows']);mt=mt.filter_rows(hl.is_defined(pruned[mt.row_key]))
        elif operation=='ld_matrix':
            keys(step,['id','operation','radius','block_size']);count=mt.count_rows()
            if count*count>capacity['maximum_dense_ld_cells']:raise ValueError('Complete dense LD output exceeds declared capacity; do not return a truncated matrix')
            bm=hl.ld_matrix(mt.GT.n_alt_alleles(),mt.locus,radius=integer(step['radius'],0,1000000000),block_size=integer(step['block_size'],1,4096));bm.write(str(folder/'ld.bm'));values=bm.to_numpy().tolist();assert len(values)==count and all(len(row)==count for row in values);save(folder/'ld-complete.json',dict(rows=count,columns=count,values=encode(values)));table_snapshot(mt.add_row_index('__omicsmatic_row_index').rows(),folder,'variant-order',capacity['maximum_rows'])
        elif operation=='hwe_normalized_pca':
            keys(step,['id','operation','k','compute_loadings']);eigenvalues,scores,loadings=hl.hwe_normalized_pca(mt.GT,k=integer(step['k'],1,100),compute_loadings=boolean(step['compute_loadings']));save(folder/'eigenvalues.json',encode(eigenvalues));table_snapshot(scores,folder,'scores',capacity['maximum_columns'])
            if loadings is not None:table_snapshot(loadings,folder,'loadings',capacity['maximum_rows'])
        else:raise ValueError('Unsupported original Hail operation: '+str(operation))
        nrows,ncols=check_matrix(mt,capacity);reports.append(dict(id=id,operation=operation,rows=nrows,columns=ncols));save(folder/'operation.json',reports[-1])
    return dict(kind='matrix-pipeline',operations=reports,**matrix_snapshot(mt,out,capacity))

def liftover(case,out,capacity,chains):
    keys(case,['id','kind','input','chain','source_reference','destination_reference','min_match','unmapped_policy','allele_policy']);local(case['input'],'file');chain=local(case['chain'],'file');records=json.loads(Path(case['input']).read_bytes());assert isinstance(records,list) and len(records)<=capacity['maximum_rows'];source=case['source_reference'];destination=case['destination_reference'];assert source=='GRCh37' and destination=='GRCh38';assert case['allele_policy']=='KEEP_SOURCE_REPORT_STRAND';assert case['unmapped_policy'] in ['KEEP_MISSING','FAIL_ATOMIC'];signature=hashlib.sha256(chain.read_bytes()).hexdigest();reference=hl.get_reference(source)
    if (source,destination) not in chains:reference.add_liftover(str(chain),destination);chains[(source,destination)]=signature
    elif chains[(source,destination)]!=signature:raise ValueError('One atomic worker cannot silently replace an already bound source/destination chain')
    data=[]
    for i,row in enumerate(records):
        keys(row,['id','contig','position','alleles']);assert isinstance(row['id'],str) and isinstance(row['alleles'],list) and all(isinstance(a,str) for a in row['alleles']);data.append(dict(ordinal=i,id=row['id'],original_locus=hl.Locus(row['contig'],integer(row['position'],1,2147483647),source),original_alleles=row['alleles']))
    table=hl.Table.parallelize(data,hl.tstruct(ordinal=hl.tint32,id=hl.tstr,original_locus=hl.tlocus(source),original_alleles=hl.tarray(hl.tstr)),key=['ordinal']);table=table.annotate(lifted=hl.liftover(table.original_locus,destination,min_match=bounded(case['min_match'],0,1),include_strand=True));unmapped=table.aggregate(hl.agg.count_where(hl.is_missing(table.lifted)));count=table_snapshot(table,out,'liftover',capacity['maximum_rows'])
    if unmapped and case['unmapped_policy']=='FAIL_ATOMIC':raise ValueError('Original liftover returned '+str(unmapped)+' unmapped loci; caller required atomic failure. Every provisional source locus/result is retained.')
    return dict(kind='liftover',rows=count,unmapped=unmapped,allele_policy=case['allele_policy'],chain_sha256=signature,source_reference=source,destination_reference=destination)

def batch_local(case,out):
    import hailtop.batch as hb
    keys(case,['id','kind','backend','inputs','jobs']);assert case['backend']=='LOCAL','Cloud backend is unconfigured: skip the dependent cloud analysis; local execution does not establish Service/backend/scale equivalence';assert 1<=len(case['jobs'])<=128;inputs={};jobs={};resources={};ledger=[];scratch=out/'original-batch-scratch';scratch.mkdir();published=out/'outputs';published.mkdir();backend=hb.LocalBackend(tmp_dir=str(scratch));batch=hb.Batch(name=case['id'],backend=backend)
    try:
        for item in case['inputs']:
            keys(item,['id','path']);id=identifier(item['id']);assert id not in inputs;local(item['path'],'file');inputs[id]=batch.read_input(item['path'])
        for item in case['jobs']:
            keys(item,['id','argv','outputs','depends_on']);id=identifier(item['id']);assert id not in jobs and isinstance(item['argv'],list) and item['argv'];job=batch.new_job(name=id);jobs[id]=job;outputs={}
            for name in item['outputs']:name=identifier(name);assert name not in outputs;outputs[name]=job[name]
            resources[id]=outputs
            for dependency in item['depends_on']:
                if dependency==id or dependency not in jobs:raise ValueError('Jobs must be topologically declared; unresolved/cyclic explicit dependency: '+dependency)
                job.depends_on(jobs[dependency])
            argv=[]
            for token in item['argv']:
                if isinstance(token,str):assert '\0' not in token;argv.append(shlex.quote(token))
                elif isinstance(token,dict) and set(token)=={'input'}:argv.append('"'+str(inputs[token['input']])+'"')
                elif isinstance(token,dict) and set(token)=={'output'}:argv.append('"'+str(outputs[token['output']])+'"')
                elif isinstance(token,dict) and set(token)=={'job','output'}:
                    if token['job']==id or token['job'] not in resources:raise ValueError('Resource dependency must refer to an earlier job')
                    argv.append('"'+str(resources[token['job']][token['output']])+'"')
                else:raise ValueError('Arguments must be literals or typed input/output/resource bindings')
            command=' '.join(argv);job.command(command)
            for name,resource in outputs.items():batch.write_output(resource,str(published/(id+'-'+name)))
            ledger.append(dict(id=id,argv=item['argv'],original_resource_command=command,outputs=item['outputs'],explicit_dependencies=item['depends_on']))
        identities={job:id for id,job in jobs.items()};graph=[dict(id=id,parents=sorted(identities[parent] for parent in job._dependencies)) for id,job in jobs.items()];save(out/'original-dependency-graph.json',dict(scope='Read-only Job._dependencies from the exact original pinned Hail Batch release; no namespace modification',jobs=graph));save(out/'declared-jobs.json',ledger);batch.run(verbose=True,delete_scratch_on_exit=False)
        files=[]
        for id,outputs in resources.items():
            for name in outputs:
                path=published/(id+'-'+name);raw=path.read_bytes();files.append(dict(job=id,output=name,bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()))
        save(out/'complete-output-pins.json',files);return dict(kind='batch-local',backend='LOCAL',jobs=len(jobs),outputs=len(files),batch_module_path=str(Path(hb.__file__).resolve()),cloud_batch_submitted=False)
    finally:backend.close()

def main():
    assert len(sys.argv)==3;home=Path(sys.argv[1]);request=json.loads(Path(sys.argv[2]).read_bytes(),parse_constant=lambda value:(_ for _ in ()).throw(ValueError('Nonstandard JSON numeric constant '+value)));keys(request,['schema_version','expected_hail','limits','cases']);assert request['schema_version']==1 and hl.version()==request['expected_hail'];capacity=limits(request);state=dict(status='FAIL2_ORIGINAL_HAIL_WORKFLOW_INCOMPLETE',hail=hl.version(),hail_module_path=str(Path(hl.__file__).resolve()),python_prefix=sys.prefix,cases=[],namespace_patched=False,cloud_batch_submitted=False,paper_reproduced=False,implementation_credit=False,whole_goal_complete=False);started=False;chains={};seen=set()
    try:
        assert 1<=len(request['cases'])<=128
        for case in request['cases']:
            id=identifier(case['id']);assert id not in seen;seen.add(id);out=home/id;out.mkdir()
            if case['kind']=='batch-local':result=batch_local(case,out)
            else:
                if not started:
                    hl.init(backend='spark',master='local[2]',log=str(home/'original-hail.log'),tmp_dir=str(home/'hail-temp'),local_tmpdir=str(home/'hail-local-temp'),spark_conf={'spark.driver.memory':'2g','spark.executor.memory':'2g','spark.driver.extraJavaOptions':'-XX:ActiveProcessorCount=2','spark.executor.extraJavaOptions':'-XX:ActiveProcessorCount=2','spark.ui.enabled':'false','spark.ui.showConsoleProgress':'false'});started=True
                if case['kind']=='matrix-pipeline':result=pipeline(case,out,capacity)
                elif case['kind']=='liftover':result=liftover(case,out,capacity,chains)
                else:raise ValueError('Unsupported original Hail case kind')
            result['id']=id;state['cases'].append(result);save(out/'case.json',result);print('Complete original case:',id,flush=True)
        for case in request['cases']:
            out=home/case['id']
            if case['kind']=='matrix-pipeline':assert matrix_value(hl.read_matrix_table(str(out/'result.mt')),capacity)==json.loads((out/'matrix-complete.json').read_bytes()),'Complete native MatrixTable readback differs after the batch: '+case['id']
            elif case['kind']=='liftover':
                table=hl.read_table(str(out/'liftover.ht'));value=json.loads((out/'liftover.json').read_bytes());assert str(table.row.dtype)==value['schema'] and str(table.key.dtype)==value['key'] and str(table.globals.dtype)==value['globals_schema'] and encode(hl.eval(table.index_globals()))==value['globals'] and [encode(row) for row in table.collect()]==value['rows'],'Complete native liftover table readback differs'
            elif case['kind']=='batch-local':
                for value in json.loads((out/'complete-output-pins.json').read_bytes()):
                    raw=(out/'outputs'/(value['job']+'-'+value['output'])).read_bytes();assert len(raw)==value['bytes'] and hashlib.sha256(raw).hexdigest()==value['sha256'],'An earlier Batch output changed after its case completed'
        state.update(status='PASS_COMPLETE_ORIGINAL_HAIL_WORKFLOW_CANDIDATE_ONLY',complete_native_readback_verified=True)
    except Exception as failure:state['error']=repr(failure);(home/'original-error.traceback').write_text(traceback.format_exc(),encoding='utf8');print('FAIL2 ORIGINAL HAIL WORKFLOW:',repr(failure),flush=True)
    finally:
        if started:
            try:hl.stop()
            except Exception as failure:state.update(status='FAIL2_ORIGINAL_HAIL_STOP_FAILED',stop_error=repr(failure))
        save(home/'COMPLETED.json',state)
    return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original workflow admission checks')
    raise SystemExit(main())
