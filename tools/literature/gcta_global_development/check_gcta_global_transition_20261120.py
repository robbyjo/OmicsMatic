from pathlib import Path
import importlib.util,json,subprocess,sys
R=Path('D:/projects/JLinAlg');T=R/'build/gc-7';B=T/'build/gcta-global-1';O=B/'transition-controls-1';O.mkdir()
def load(name,path):
    spec=importlib.util.spec_from_file_location(name,path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module
m=load('gcta_transition',T/'tools/literature/gcta_dispatch_transition.py');gsea=load('gsea_transition',T/'tools/literature/gsea_dispatch_transition.py');raw=(T/m.DISPATCH).read_bytes();before=m.dispatcher_before(raw);assert before==subprocess.check_output(['git','show',m.BASE+':'+m.DISPATCH],cwd=T)
older=gsea.dispatcher_before(before);assert gsea.pin(older)==gsea.BEFORE
cases={'missing-route':raw.replace(m.ROUTE,b'',1),'missing-help':raw.replace(m.HELP,b'',1),'duplicate-route':raw+m.ROUTE,'duplicate-help':raw+m.HELP,'moved-route':raw.replace(m.ROUTE,b'',1)+m.ROUTE,'moved-help':raw.replace(m.HELP,b'',1)+m.HELP,'unrelated-change':raw+b'\n'};controls=[]
for name,value in cases.items():
    try:m.dispatcher_before(value)
    except ValueError as error:controls.append(dict(name=name,status='EXPECTED_REFUSAL',cause=str(error)))
    else:raise AssertionError('Admitted '+name)
prior=R/'build/gc-6';status=subprocess.check_output(['git','status','--porcelain=v1','-z','--untracked-files=all'],cwd=prior);assert not status
command=[sys.executable,'-B',str(prior/'tools/literature/close_gsea_catalog.py'),'--verify','--out',str(prior/'build/gcta-global-predecessor-check-1')]
with (O/'predecessor.stdout').open('xb') as stdout,(O/'predecessor.stderr').open('xb') as stderr:p=subprocess.run(command,cwd=prior,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
assert subprocess.check_output(['git','status','--porcelain=v1','-z','--untracked-files=all'],cwd=prior)==status
record=dict(status='GCTA_THREE_LINE_INVERSE_AND_UNCHANGED_PREDECESSOR_OBSERVATIONS',before=m.pin(before),after=m.pin(raw),preceding_GSEA_inverse_result=gsea.pin(older),controls=controls,predecessor_command=command,predecessor_exit=p.returncode,predecessor_worktree_unchanged=True,source_overlay_used=False,old_hashes_refreshed=False)
path=prior/'build/gcta-global-predecessor-check-1/COMPLETED.json'
if path.is_file():record['predecessor_completed']=json.loads(path.read_bytes())
(O/'RESULT.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8',newline='\n');print('GCTA inverse7 controls PASS;unchanged previous catalog exit',p.returncode,flush=True)
assert p.returncode==0,'Inspect saved predecessor diagnostics; no source/hash replacement'
