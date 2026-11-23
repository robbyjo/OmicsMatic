from pathlib import Path
import ast,hashlib,json,zipfile
R=Path('D:/projects/JLinAlg');S=R/'build/fu-1/build/squidpy-1';D=R/'build/fu-1/tools/literature/squidpy_workflow'
intakes=json.loads((S/'upstream-intake-1/INTAKE.json').read_bytes());editions={}
for intake in intakes:
 version=intake['version'];wheel=S/'upstream-intake-1'/intake['artifact']
 assert hashlib.sha256(wheel.read_bytes()).hexdigest()==intake['sha256']
 functions={};members={}
 with zipfile.ZipFile(wheel) as z:
  for name in z.namelist():
   if name.startswith('squidpy/') and not name.endswith('/'):
    raw=z.read(name);members[name[len('squidpy/'):]]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
  for operation,filename,function in [('SPATIAL_NEIGHBORS','squidpy/gr/_build.py','spatial_neighbors'),('SPATIAL_AUTOCORR','squidpy/gr/_ppatterns.py','spatial_autocorr'),('LIGREC','squidpy/gr/_ligrec.py','ligrec'),('READ_NANOSTRING','squidpy/read/_read.py','nanostring')]:
   if filename not in z.namelist():continue
   tree=ast.parse(z.read(filename).decode())
   node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==function)
   params=[a.arg for a in node.args.args+node.args.kwonlyargs if a.arg not in ['adata','copy','path','table_key','elements_to_coordinate_systems','interactions','interactions_params','transmitter_params','receiver_params']]
   if operation=='LIGREC' and version!='1.8.4':params+=['clusters','n_perms','seed','alpha','numba_parallel','n_jobs','backend','show_progress_bar']
   functions[operation]=dict(symbol=('squidpy.read.' if operation=='READ_NANOSTRING' else 'squidpy.gr.')+function,parameters=params)
 editions[version]=dict(official_wheel=intake['artifact'],official_wheel_URL=intake['artifact_URL'],official_wheel_sha256=intake['sha256'],members=members,functions=functions)
target=D/'SOURCE-PROFILE.json';assert not target.exists()
target.write_text(json.dumps(dict(schema_version=1,profile='squidpy-original-workflow-v1',editions=editions),indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(status='ORIGINAL_WHEEL_FILE_AND_OPERATION_PROFILES_SAVED',versions=list(editions))),flush=True)
