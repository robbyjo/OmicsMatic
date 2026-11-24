"""Read the admitted PRSice catalog evidence; never rerun original scientific analyses."""
from pathlib import Path
import argparse,hashlib,json,sys

def require(value,message):
    if not value:raise ValueError(message)
def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb')as f:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def closure(root,members):
    require(bool(members),'Empty saved closure')
    for name,identity in members.items():
        p=Path(name);require(not p.is_absolute()and '..'not in p.parts and '\\'not in name and ':'not in name,'Unsafe saved source member');file=root/p;require(file.is_file()and file.resolve()==file.absolute()and pin(file)==identity,'Changed/missing saved member: '+name)

def verify(root,cache=None,source_root=None,private=None,jar=None):
    root=Path(root).absolute();d=root/'docs/literature/prsice-catalog-checkpoint';state=read(d/'STATUS.json');require(state['status']=='PASS_PRSICE_CATALOG_ONE_FAMILY_FIVE_ROLES_FOUR_NEW_REVIEWS'and state['catalog_admitted']and not state['whole_goal_complete'],'Expanded catalog claim');require((state['new_upstream_family_credit'],state['new_reviews'],state['refined_existing_reviews'],state['historical_paper_editions_qualified'],state['original_paper_analyses_reexecuted'])==(1,4,1,False,False),'Expanded review/historical credit')
    require(pin(d/'FILES.json')==state['public_manifest'],'Changed public closure');files=read(d/'FILES.json');closure(root,files);v=read(d/'VALIDATION.json');require(v['catalog_tests']==dict(windows=18,linux=18)and(v['primary_XML_checked'],v['source_roles'],v['source_paragraphs'])==(250,5,17),'Incomplete catalog/source validation');require(len(v['refusals'])==16 and all(r['exit_code']==r['expected']and not r['complete_result']and r['canonical_unchanged']for r in v['refusals']),'Incomplete actual refusal controls');require(v['desktop_mobile_preview_and_installed_passed']and v['browser_profile_processes_after']==0 and v['no_OS_process_termination'],'Incomplete browser lifecycle/contents validation');require(v['literature_reports_withheld_by_unchanged_Pages_workflow'],'Changed withheld-page policy')
    sys.path.insert(0,str(root/'tools/literature'));from prsice_catalog import catalog as c,model as m,primary as p
    require(c.ROOT.resolve()==root.resolve(),'Wrong source root');proof=read(root/m.PROOF);require(proof['status']==c.STATUS and proof['base_commit']==c.BASE and proof['source_code']==c.sources(),'Changed admission source/scope')
    for name,identity in proof['evidence'].items():require(pin(root/name)==identity,'Changed admission evidence: '+name)
    before=c.read_before(root/c.FOLDER/'metadata-before.zip');c.baseline(before);require(proof['before']=={name:p.pin(raw)for name,raw in before.items()},'Changed exact predecessor');q=m.qualification();require(proof['qualification']==p.pin(p.encoded(q))and(root/c.FOLDER/'QUALIFICATION.json').read_bytes()==p.encoded(q),'Changed workflow qualification');after={name:(root/name).read_bytes()for name in m.META};result=m.check(before,after,p.frozen(),q);require(result==proof['result']==state['counts']and proof['after']=={name:p.pin(raw)for name,raw in after.items()},'Changed installed deterministic transition');require((root/c.FOLDER/'REGISTRY.json').read_bytes()==p.encoded(m.registry())and len(m.registry())==485 and c.clean_manifest()['entries']==875,'Changed registry or full catalog manifest');require(state['source_code']=={**c.sources(),'.gitattributes':pin(root/'.gitattributes'),'tools/literature/verify_prsice_catalog_checkpoint.py':pin(root/'tools/literature/verify_prsice_catalog_checkpoint.py')},'Changed checkpoint reader/source')
    context=read(d/'PREDECESSOR-CONTEXT.json');require(len(context)==40 and pin(d/'PREDECESSOR-CONTEXT.json')==state['predecessor_context_manifest'],'Changed historical workflow source population');fresh=0;checked=0
    if source_root is not None:
        source_root=Path(source_root).absolute();closure(source_root,context)
        for name,raw in before.items():require((source_root/name).read_bytes()==raw,'Changed original predecessor metadata')
    if cache is not None:
        require(source_root is not None,'--cache requires --workflow-source-root');_,actual=p.audit(cache,before[m.META[0]],source_root);fresh=actual['primary_XML_checked']
    if jar is not None:require(pin(Path(jar))==state['producer'],'Restore exact complete producer; never refresh old hashes')
    if private is not None:
        require(source_root is not None and jar is not None,'--private-root requires original source context and producer');private=Path(private).absolute();old=read(source_root/m.CHECKPOINT);manifest=private/'PRIVATE-CLOSURE.json';require(pin(manifest)==old['private_evidence_manifest'],'Changed original private evidence closure');saved=read(manifest);require(len(saved)==9702,'Incomplete old workflow evidence');closure(private,saved);checked=len(saved)
    return dict(status='PASS_PRSICE_ADMITTED_CATALOG_CHECKPOINT',public_files=len(files),primary_XML_checked_now=fresh,private_workflow_evidence_files_checked=checked,counts=result,scientific_models_run=False,whole_goal_complete=False)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2])
    for name in('cache','workflow-source-root','private-root','jar'):p.add_argument('--'+name,type=Path)
    a=p.parse_args()
    try:print(json.dumps(verify(a.root,a.cache,a.workflow_source_root,a.private_root,a.jar),indent=2));return 0
    except(FileNotFoundError,PermissionError)as e:print('SKIP3: dependent verification skipped. '+str(e)+'. Restore exact permitted prerequisites or retain the skip.',file=sys.stderr);return 3
    except Exception as e:print('FAIL2: no complete verified result. '+repr(e)+'. Preserve evidence, correct the cause and never refresh old hashes.',file=sys.stderr);return 2
if __name__=='__main__':sys.exit(main())
