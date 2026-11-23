"""Independent saved Squidpy capture checks; no upstream calls or model refits.

Requires NumPy for non-pickle primitive-array inspection. Java VERIFY remains
independent of Python installation. Recorded runtime hashes are evidence, not
authentication or a fresh check of the installed runtime on this machine.
"""
from pathlib import Path
import argparse,hashlib,itertools,json,math,posixpath,re,sys,zipfile


def require(value,message):
    if not value:raise ValueError(message)


def pairs(items):
    result={}
    for key,value in items:
        require(key not in result,'Duplicate JSON key');result[key]=value
    return result


def load(path):
    def constant(value):raise ValueError('Non-JSON number '+value)
    return json.loads(path.read_bytes(),object_pairs_hook=pairs,parse_constant=constant)


def fingerprint(path):
    digest=hashlib.sha256();size=0
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(65536),b''):digest.update(block);size+=len(block)
    return dict(bytes=size,sha256=digest.hexdigest())


def child(root,name):
    require(isinstance(name,str) and name and not any(c in name for c in ('\\',':')),'Invalid portable member')
    require(all(part not in ('','.','..') for part in name.split('/')) and not name.startswith('/'),'Member traversal')
    return root/name


def number(token):
    require(token['kind'] in ('float','integer','None','pandas.NA','pandas.NaT'),'Typed number required')
    if token['kind'] in ('None','pandas.NA','pandas.NaT'):return math.nan
    value=token['value']
    return {'NaN':math.nan,'Inf':math.inf,'-Inf':-math.inf}[value] if value in ('NaN','Inf','-Inf') else float(value)


def scalar(token):
    kind=token['kind']
    if kind in ('None','pandas.NA','pandas.NaT'):require(set(token)=={'kind'},'Malformed missing token');return
    require(set(token)=={'kind','value'},'Malformed typed scalar')
    value=token['value']
    if kind=='tuple':
        for item in value:scalar(item)
    elif kind=='string':require(isinstance(value,str),'Literal text required')
    elif kind=='bool':require(type(value) is bool,'Boolean required')
    elif kind=='integer':require(isinstance(value,str) and re.fullmatch(r'-?(0|[1-9][0-9]*)',value) is not None,'Exact integer token required')
    elif kind=='float':
        require(isinstance(value,str),'Typed floating text required')
        if value not in ('NaN','Inf','-Inf'):require(re.fullmatch(r'-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?',value) is not None and math.isfinite(float(value)),'Invalid or overflowing float token')
    else:raise ValueError('Unknown typed scalar '+kind)


def rows(directory):
    schema=load(directory/'schema.json');columns=schema['columns'];count=0
    require(len(schema['column_labels'])==columns==len(schema['dtypes']),'Typed column population')
    for value in schema['column_labels']:scalar(value)
    with (directory/'rows.jsonl').open(encoding='utf-8') as stream:
        for line in stream:
            row=json.loads(line,object_pairs_hook=pairs);require(row['position']==count and len(row['cells'])==columns,'Typed row position/width')
            scalar(row['index'])
            for value in row['cells']:scalar(value)
            yield row;count+=1
    require(count==schema['rows'],'Incomplete typed frame')


def matrix(directory,item):
    path=child(directory,item['file']);count=nonfinite=0
    with path.open(encoding='ascii',newline='') as stream:
        header=stream.readline().lower().split();require(len(header)==5 and header[:2]==['%%matrixmarket','matrix'] and header[2] in ('array','coordinate') and header[3] in ('real','integer') and header[4]=='general','Unsupported original MatrixMarket transport')
        line=stream.readline()
        while line.startswith('%'):line=stream.readline()
        dimensions=[int(v) for v in line.split()];require(dimensions[:2]==item['shape'],'Matrix shape metadata')
        expected=dimensions[2] if header[2]=='coordinate' else dimensions[0]*dimensions[1]
        for line in stream:
            require(all(ord(c)>=32 or c in ('\t','\n') for c in line),'Control byte in MatrixMarket export; no trimming/repair is admitted')
            parts=line.split()
            if not parts:continue
            if header[2]=='coordinate':
                require(len(parts)==3 and 1<=int(parts[0])<=dimensions[0] and 1<=int(parts[1])<=dimensions[1],'Matrix coordinate/width')
                values=parts[2:]
            else:values=parts
            for value in values:
                parsed=int(value) if header[3]=='integer' else float(value)
                if isinstance(parsed,float) and not math.isfinite(parsed):nonfinite+=1
                count+=1
        require(count==expected==item['stored_entries'],'Incomplete matrix payload')
    return nonfinite


def state(directory,request):
    import numpy as np
    obj=load(directory/'state.json');cells,features=obj['n_obs'],obj['n_vars'];arrays=0
    for axis,size in (('obs',cells),('var',features)):
        require(load(directory/axis/'schema.json')['rows']==size,'State identity population')
        for _ in rows(directory/axis):pass
    require(obj['X']['shape']==[cells,features],'State expression axes');matrix(directory,obj['X'])
    for slot,items in obj['slots'].items():
        for item in items:
            require(item['shape'][0]==cells,'State matrix observation population')
            if slot=='obsp':require(item['shape']==[cells,cells],'State graph axes')
            if slot=='layers':require(item['shape']==[cells,features],'State layer axes')
            matrix(directory,item)
    if 'raw' in obj:
        require(obj['raw']['shape'][0]==cells and load(directory/'raw-var/schema.json')['rows']==obj['raw']['shape'][1],'Raw identity population')
        for _ in rows(directory/'raw-var'):pass
        matrix(directory,obj['raw'])
    def walk(node,depth=0):
        nonlocal arrays
        require(depth<=64,'Metadata nesting')
        if node['kind']=='mapping':
            for entry in node['entries']:scalar(entry['key']);walk(entry['value'],depth+1)
        elif node['kind'] in ('list','tuple','object_array'):
            for value in node['values']:walk(value,depth+1)
        elif node['kind']=='array':
            path=child(directory,node['file'])
            if re.fullmatch(r'(float(32|64)|u?int(8|16|32|64)|bool)',node['dtype']):
                a=np.load(path,allow_pickle=False,mmap_mode='r')
                require(list(a.shape)==node['shape'] and str(a.dtype)==node['dtype'],'NPY shape/type metadata differs')
                require(a.ndim<=16 and a.size<=request['limits']['maximum_entries'],'NPY entry/rank capacity')
                require(a.offset+a.nbytes==path.stat().st_size,'NPY truncated/trailing bytes');arrays+=1
                del a
            else:require(path.read_bytes()[:6]==b'\x93NUMPY','Opaque context NPY signature')
        else:scalar(node)
    walk(obj['uns'])
    with (directory/'original-state.h5ad').open('rb') as stream:require(stream.read(8)==b'\x89HDF\r\n\x1a\n','Original H5AD signature')
    return dict(cells=cells,features=features,primitive_context_arrays_checked=arrays)


def autocorrelation(root,request):
    original=root/'worker/out';schema=load(original/'statistics/schema.json');labels=[v['value'] for v in schema['column_labels']]
    context=load(original/'normality-context.json');n=context['n'];options=request['options'];mode=options.get('mode','moran')
    s0,s1,s2=(number(context[k]) for k in ('s0','s1','s2'));epsilon=number(context['moment_precision_epsilon']);require(epsilon in (2.**-52,2.**-23,2.**-10),'Moment precision')
    require(n>1,'Observation count');s02=s0*s0
    if mode=='geary' and request['expected_squidpy']=='1.8.4':
        denominator=abs(2*(n+1)*s02);scale=(abs((2*s1+s2)*(n-1))+abs(4*s02))/denominator if denominator else math.nan
        analytic=((2*s1+s2)*(n-1)-4*s02)/(2*(n+1)*s02) if denominator else math.nan
    else:
        denominator=abs((n-1)*(n+1)*s02);scale=(abs(n*n*s1)+abs(n*s2)+abs(3*s02))/denominator+1/(n-1)**2 if denominator else math.nan
        analytic=(n*n*s1-n*s2+3*s02)/((n-1)*(n+1)*s02)-1/(n-1)**2 if denominator else math.nan
    guard=64*epsilon*scale;score=labels.index('I' if mode=='moran' else 'C');variance=labels.index('var_norm');tainted=set();finite=absent=withheld=0
    def valid(cells,label):
        p=number(cells[labels.index(label)]);v=number(cells[variance]);ok=math.isfinite(number(cells[score])) and math.isfinite(p) and 0<=p<=1
        if label.startswith('pval_norm'):ok=ok and math.isfinite(v) and math.isfinite(guard) and v>guard and v>0
        if label.startswith('pval_z_sim'):ok=ok and 'var_sim' in labels and math.isfinite(number(cells[labels.index('var_sim')])) and number(cells[labels.index('var_sim')])>0
        return ok
    for row in rows(original/'statistics'):
        for label in ('pval_norm','pval_sim','pval_z_sim'):
            if label in labels and not valid(row['cells'],label):tainted.add(label)
    with (original/'normality-z.jsonl').open(encoding='utf-8') as zs,(root/'admitted/estimates.jsonl').open(encoding='utf-8') as admitted:
        for row in rows(original/'statistics'):
            z=json.loads(next(zs));a=json.loads(next(admitted));cells=row['cells'];point=number(cells[score]);v=number(cells[variance]);ok=math.isfinite(point)
            require(z['position']==row['position']==a['source_position'] and z['index']==row['index']==a['feature'],'Autocorrelation identity/order')
            require(a['variance']==cells[variance] and a['point']==(dict(status='ADMITTED',estimate=cells[score]) if ok else dict(status='UNDEFINED',original=cells[score])),'Point admission contradicts raw export')
            finite+=ok;absent+=not ok
            for i,label in enumerate(labels):
                if not label.startswith('pval_'):continue
                base=next((b for b in ('pval_norm','pval_z_sim','pval_sim') if label.startswith(b)),None);require(base is not None,'Unknown probability field')
                good=valid(cells,label) and (label==base or base not in tainted)
                expected=dict(status='ADMITTED',estimate=cells[i]) if good else dict(status='UNDEFINED_ADJUSTMENT_FAMILY' if label!=base and base in tainted else 'UNDEFINED_OR_UNRESOLVED',original=cells[i])
                require(a['probabilities'][label]==expected,'Probability admission contradicts raw export/taint');withheld+=not good
                if good and label=='pval_norm':
                    error=number(context['source_derived_standard_error']);standardized=number(z['standardized']);expectation=-1/(n-1) if mode=='moran' else 1.
                    eps=max({'float16':2.**-10,'float32':2.**-23,'float64':2.**-52}[context[k]] for k in ('score_dtype','standardized_dtype','source_standard_error_dtype'))
                    require(math.isfinite(analytic) and abs(v-analytic)<=2*guard,'Variance contradicts moments')
                    require(abs(error-math.sqrt(v))<=8*eps*math.sqrt(v)+8*math.ulp(math.sqrt(v)),'Standard error contradicts variance')
                    reconstructed=(point-expectation)/error;tolerance=16*eps*(abs(point/error)+abs(expectation/error))+16*math.ulp(reconstructed)
                    require(standardized==reconstructed or abs(standardized-reconstructed)<=tolerance,'Standardized score contradicts point/standard error')
                    cdf=.5*math.erfc(-standardized/math.sqrt(2));p=1-cdf if standardized>0 else cdf
                    if options.get('two_tailed',False):p*=2
                    require(abs(number(cells[i])-p)<=3e-12,'Normality probability contradicts standardized score')
        require(next(zs,None) is None and next(admitted,None) is None,'Excess admitted/diagnostic rows')
    return dict(finite_result_rows=finite,no_result_rows=absent,withheld_probability_cells=withheld)


def ligrec(root):
    original=root/'worker/out';ms=load(original/'means/schema.json');ps=load(original/'pvalues/schema.json');metadata=load(original/'metadata/schema.json')
    require(ms['rows']==ps['rows']==metadata['rows'] and ms['column_labels']==ps['column_labels'],'Interaction result axes')
    finite=absent=0
    with (root/'admitted/estimates.jsonl').open(encoding='utf-8') as stream:
        for mean,pvalue,annotation in itertools.zip_longest(rows(original/'means'),rows(original/'pvalues'),rows(original/'metadata')):
            require(mean is not None and pvalue is not None and annotation is not None and mean['index']==pvalue['index']==annotation['index'],'Interaction annotation identity/order')
            admitted=json.loads(next(stream));require(admitted['interaction']==mean['index'] and admitted['source_position']==mean['position'],'Admitted interaction identity/order')
            for i,(m,p) in enumerate(zip(mean['cells'],pvalue['cells'])):
                value=number(p);ok=math.isfinite(number(m)) and math.isfinite(value) and 0<=value<=1
                expected=dict(status='ADMITTED',estimate=p) if ok else dict(status='NOT_TESTED_OR_UNDEFINED',original=p)
                require(admitted['cells'][i]==dict(column=i,mean=m,probability=expected),'Interaction estimate admission');finite+=ok;absent+=not ok
        require(next(stream,None) is None,'Excess admitted interactions')
    return dict(finite_result_rows=finite,no_result_rows=absent,withheld_probability_cells=absent)


def recorded_runtime(root,request):
    normalize=lambda path:posixpath.normpath(path.replace('\\','/'))
    key=lambda name:re.sub(r'[-_.]+','-',name.lower())
    closure=load(root/'runtime-before.json');files={normalize(k):v for k,v in closure['files'].items()};aliases={normalize(k):normalize(v) for k,v in closure['addresses'].items()}
    edition=load(root/'source/SOURCE-PROFILE.json')['editions'][request['expected_squidpy']]
    for name in ('runtime.json','runtime-after-info.json'):
        info=load(root/'worker'/name)
        require(info['python']==request['expected_python'] and info['squidpy']==request['expected_squidpy'],'Recorded runtime editions')
        require(info['python_hash_seed']=='0' and info['python_flags']['no_user_site']==1 and info['python_flags']['dont_write_bytecode'] is True,'Recorded source-isolation flags')
        require(normalize(info['python_executable']) in files,'Interpreter outside recorded runtime closure')
        packages={}
        for item in info['packages']:
            k=key(item['name']);require(k not in packages or packages[k]==item['version'],'Conflicting dependency aliases');packages[k]=item['version']
        for package,version in request['expected_packages'].items():require(packages.get(key(package))==version,'Recorded dependency edition: '+package)
        require(info['original_source_files']==edition['members'],'Recorded original wheel members')
        for member,identity in edition['members'].items():
            address=normalize(info['squidpy_source_root']+'/'+member);require(files.get(aliases.get(address,address))==identity,'Original wheel source lacks recorded bytes: '+member)
        worker_sources={normalize(request['out']+'/source/'+filename) for filename in ('worker.py','operations.py')}
        for address in info['loaded_module_files']:require(normalize(address) in files or normalize(address) in worker_sources,'Loaded module outside recorded closure')


def verify(root,jar):
    require(not (root/'FAILURE.json').exists(),'Failed capture cannot be admitted');manifest=load(root/'manifest.json');request=load(root/'canonical-request.json')
    require(load(root/'COMPLETED.json')['manifest_sha256']==fingerprint(root/'manifest.json')['sha256'],'Completion seal')
    require(manifest['workflow']=='squidpy-original-workflow-v1' and manifest['results_admitted'] is True,'Workflow admission')
    actual={};directories=[]
    for path in root.rglob('*'):
        require(not path.is_symlink() and path.resolve()==path.absolute(),'Aliased capture member')
        name=path.relative_to(root).as_posix()
        if path.is_dir():directories.append(name)
        else:
            require(path.is_file(),'Nonregular capture member')
            if name not in ('manifest.json','COMPLETED.json'):actual[name]=fingerprint(path)
    require(manifest['inventory']==dict(files=actual,directories=sorted(directories)),'Closed capture inventory')
    with zipfile.ZipFile(jar) as archive:
        for name,digest in load(root/'producer.json')['selected_class_sha256'].items():require(hashlib.sha256(archive.read(name.replace('.','/')+'.class')).hexdigest()==digest,'Producer class differs: '+name)
        for name in ('worker.py','operations.py','SOURCE-PROFILE.json'):require(archive.read('org/jlinalg/omics/squidpy-workflow/'+name)==(root/'source'/name).read_bytes(),'Producer worker/profile source differs')
    require(load(root/'runtime-before.json')==load(root/'runtime-after.json'),'Recorded runtime changed')
    recorded_runtime(root,request)
    require(load(root/'worker/invocation.json')['provided_options']==request['options'],'Executed options differ from declaration')
    for name,identity in load(root/'input-inventory.json').items():require(fingerprint(child(root,name))==identity,'Captured input differs')
    originals=load(root/'original-inventory.json');require(set(originals)=={p.relative_to(root/'worker/out').as_posix() for p in (root/'worker/out').rglob('*') if p.is_file()},'Original export closure')
    for name,identity in originals.items():require(fingerprint(child(root/'worker/out',name))==identity,'Original export bytes differ')
    report=load(root/'worker/report.json');status=load(root/'analysis-status.json');require(report['original_call_returned'] is True and report['results_admitted'] is False and status['results_admitted'] is True and status['exit_code']==0,'Original phase/final admission')
    require((root/'worker/READY').read_bytes()==b'SQUIDPY_PREFLIGHT_V1\n' and (root/'worker/ACK').read_bytes()==b'SQUIDPY_ACK_V1\n','Pre-call handshake')
    require(manifest['process_exit_code']==0 and all(p['same_identity_alive'] is False for p in manifest['owned_processes']),'Worker completion/ownership')
    summary=load(root/'summary.json');operation=request['operation']
    if operation=='SPATIAL_AUTOCORR':facts=autocorrelation(root,request)
    elif operation=='LIGREC':facts=ligrec(root)
    else:facts=state(root/'worker/out/state',request)
    for key,value in facts.items():
        if key!='primitive_context_arrays_checked':require(summary[key]==value,'Summary contradicts original objects: '+key)
    if manifest['mode']=='REPLAY':
        parent=load(root/'replay-parent-manifest.json');require(fingerprint(root/'replay-parent-manifest.json')==manifest['replay_parent_manifest'],'Retained replay parent seal')
        for name in ('original-inventory.json','input-inventory.json'):require(parent['inventory']['files'][name]==fingerprint(root/('replay-parent-'+name)),'Replay parent inventories')
        require(load(root/'replay-parent-input-inventory.json')==load(root/'input-inventory.json'),'Replay changed captured inputs')
        semantic=lambda values:{k:v for k,v in values.items() if not k.endswith('/original-state.h5ad')}
        before=semantic(load(root/'replay-parent-original-inventory.json'));after=semantic(originals);equal=before==after
        require(load(root/'replay-comparison.json')==dict(typed_semantic_exports_byte_identical=equal,parent=before,replay=after,h5ad_transport_bytes_retained_but_not_compared=True,independent_refit=False),'Replay claims contradict inventories')
        require(equal or request['admission']['allow_replay_differences'],'Replay differences lack declared admission')
        binding=load(root/'request-binding.json');expected=dict(binding['replay_source_canonical'],out=request['out']);require(expected==request,'Replay changed scientific declarations')
    return dict(status='PASS_INDEPENDENT_SAVED_SQUIDPY_CAPTURE_CHECKS',operation=operation,source_squidpy=request['expected_squidpy'],
                facts=facts,files=len(actual),producer_selected_classes_checked=True,original_or_Java_analysis_reexecuted=False,
                H5AD_transport_decoded=False,opaque_metadata_arrays_not_independently_decoded=True,whole_tool_or_paper_reproduced=False)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--analysis',type=Path,required=True);parser.add_argument('--jar',type=Path,required=True);args=parser.parse_args()
    try:
        import numpy
        print(json.dumps(verify(args.analysis.resolve(),args.jar.resolve()),indent=2))
    except ModuleNotFoundError as error:
        print('SKIP3: independent saved-bundle verification skipped: '+str(error)+'. Install permitted NumPy, or use Java VERIFY; no new scientific analysis was run.',file=sys.stderr);raise SystemExit(3)
    except Exception as error:
        print('FAIL2: independent saved-bundle verification failed: '+str(error)+'. Preserve the capture and its exact producer; inspect the contradiction before downstream use.',file=sys.stderr);raise SystemExit(2)
