from pathlib import Path
import ast,json
R=Path('D:/projects/JLinAlg');B=R/'build/gc-7/build/gcta-global-1';prior=B/'global-workflows-1';rows={}
for host in ['windows21','windows25','linux21']:
    progress=json.loads((prior/host/'PROGRESS.json').read_bytes());assert progress[-1]['name']=='native-run' and progress[-1]['exit_code']==3;rows[host]=dict(native_request_moved_without_rebasing_relative_genotype_paths=True,adapter_correctly_skipped_missing_inputs=True,old_attempt_preserved=True)
(prior/'REVIEW.json').write_text(json.dumps(dict(status='RETAINED_HARNESS_PATH_FAILURE_ADAPTER_BEHAVIOR_CORRECT',results=rows,scientific_source_changed=False,remedy='Resolve original fixture-relative genotype paths before writing the derived request into a different directory'),indent=2)+'\n',encoding='utf-8',newline='\n')
source=(R/'build/check_gcta_global_workflows_20261120.py').read_text(encoding='utf-8');source=source.replace("O=B/'global-workflows-1'","O=B/'global-workflows-2'")
start=source.index("    template=json.loads((R/'build/gc-3")
end=source.index('    # The public native fixture builder',start)
source=source[:start]+source[end:]
old="template=json.loads((fixture/'request.json').read_bytes());template['out']=";new="template=json.loads((fixture/'request.json').read_bytes());template['genotypes']={key:str((fixture/value).resolve()) for key,value in template['genotypes'].items()};template['out']=";assert source.count(old)==1;source=source.replace(old,new)
ast.parse(source);exec(compile(source,__file__,'exec'))
