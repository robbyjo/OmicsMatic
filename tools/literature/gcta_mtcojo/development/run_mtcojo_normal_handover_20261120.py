from pathlib import Path
import ast
source=Path('D:/projects/JLinAlg/build/run_mtcojo_workflow_prototype_20261120.py').read_text(encoding='utf-8')
start=source.index("C=B/'compile-1'")
end=source.index('def linux(p):',start)
source=source[:start]+'''G=B/'normal-gradle-2';jar=G/'project-build/cli/omicsmatic-0.4.0.jar';UP=R/'build/gc-1/build/gcta-high-impact-1/upstream-1.94.1-intake-1'
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\\n',encoding='utf-8',newline='\\n')
record=json.loads((G/'COMPLETED.json').read_bytes());assert record['exit_code']==0
for name,w in record['inputs'].items():assert pin(T/name)==w,name
''' +source[end:]
for old,new in {
"O=B/'prototype-workflow-1'":"O=B/'normal-workflow-runs-1'",
"str(C/'classes')+';'+str(jar)":"str(jar)",
"linux(C/'classes')+':'+linux(jar)":"linux(jar)",
"Prototype mtCOJO":"Normal producer mtCOJO",
"DEVELOPMENT_COMPOSITIONS_REVIEW_REQUIRED_NOT_NORMAL_PRODUCER":"DEVELOPMENT_NORMAL_PRODUCER_RUNS_REVIEW_REQUIRED_NOT_FULL_QUALIFICATION",
"source_owner=str(C)":"source_owner=str(G),producer=pin(jar)"
}.items():
    assert old in source,old
    source=source.replace(old,new)
source=source.replace("save(O/'COMPLETED.json',", "assert all(r['exit_code']==r['verify_exit']==0 and r['all_original_table_tokens_equal'] for rows in results.values() for r in rows)\nsave(O/'COMPLETED.json',")
ast.parse(source)
exec(compile(source,__file__,'exec'))
