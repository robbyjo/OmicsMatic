"""Read the installed GenomicSEM catalog evidence without statistical refits."""
from pathlib import Path
import argparse,json,subprocess,sys,hashlib

def require(value,message):
    if not value:raise ValueError(message)
def read(p):return json.loads(p.read_bytes())
def pin(p):
    with p.open('rb')as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def closure(root,files):
    require(files and len(files)<50000,'Empty/excessive closure')
    for name,identity in files.items():
        p=Path(name);require(not p.is_absolute()and '..'not in p.parts and '\\'not in name and ':'not in name,'Unsafe saved source member');target=root/p;require(target.resolve()==target.absolute()and target.is_file()and pin(target)==identity,'Changed/missing saved member: '+name)

def verify(root,cache=None,source_root=None,private=None,jar=None):
    root=Path(root).absolute();d=root/'docs/literature/genomicsem-catalog-checkpoint';state=read(d/'STATUS.json');require(state['status']=='PASS_GENOMICSEM_CATALOG_ONE_FAMILY_FIVE_ROLES'and state['catalog_admitted']and not state['whole_goal_complete'],'Expanded admission claim');require((state['new_upstream_family_credit'],state['new_reviews'],state['historical_paper_editions_qualified'],state['original_paper_analyses_reexecuted'])==(1,5,False,False),'Expanded historical or credit claim')
    require(pin(d/'FILES.json')==state['public_manifest'],'Changed public closure');files=read(d/'FILES.json');closure(root,files);validation=read(d/'VALIDATION.json');require(validation['Windows_catalog_tests']==validation['Linux_catalog_tests']==16 and validation['primary_XML_checked']==250 and validation['source_roles']==5,'Incomplete actual catalog checks');require(len(validation['refusals'])==16 and all(x['exit_code']==x['expected']and not x['complete_result']and x['canonical_unchanged']for x in validation['refusals']),'Incomplete refusal controls');require(validation['browser_profile_processes_after']==0 and validation['no_OS_process_termination']and validation['desktop_mobile_preview_and_installed_passed'],'Incomplete owned browser checks')
    require(validation['full_checkWebsite_passed']is False and validation['preexisting_missing_citation_panels']==33 and validation['new_missing_citation_panels']==0 and validation['literature_reports_withheld_by_unchanged_Pages_workflow'],'Expanded website claim')
    sys.path.insert(0,str(root/'tools/literature'));from genomicsem_catalog import catalog as c,model as m,primary as p
    require(c.ROOT.resolve()==root.resolve(),'Wrong current source root');proof=read(root/m.PROOF);require(proof['status']==c.STATUS and proof['base_commit']==c.BASE and proof['source_code']==c.sources(),'Changed qualified catalog source')
    for name,identity in proof['evidence'].items():require(pin(root/name)==identity,'Changed applied evidence: '+name)
    before=c.read_before(root/c.FOLDER/'metadata-before.zip');c.baseline(before);require(proof['before']=={n:p.pin(raw)for n,raw in before.items()},'Changed exact original metadata');q=m.qualification();require(proof['qualification']==p.pin(p.encoded(q))and(root/c.FOLDER/'QUALIFICATION.json').read_bytes()==p.encoded(q),'Changed scoped workflow qualification');after={n:(root/n).read_bytes()for n in m.META};result=m.check(before,after,p.frozen(),q);require(result==proof['result']==state['counts']and proof['after']=={n:p.pin(raw)for n,raw in after.items()},'Changed deterministic transition')
    require((root/c.FOLDER/'REGISTRY.json').read_bytes()==p.encoded(m.registry())and len(m.registry())==485 and c.clean_manifest()['entries']==873,'Changed complete registry/manifest');require(state['source_code']=={**c.sources(),'.gitattributes':pin(root/'.gitattributes'),'tools/literature/verify_genomicsem_catalog_checkpoint.py':pin(root/'tools/literature/verify_genomicsem_catalog_checkpoint.py')},'Changed checkpoint reader/source')
    context=read(d/'PREDECESSOR-CONTEXT.json');require(len(context)==109 and pin(d/'PREDECESSOR-CONTEXT.json')==state['predecessor_context_manifest'],'Changed old source population');fresh=0;checked=0
    if source_root is not None:
        source_root=Path(source_root).absolute();closure(source_root,context)
        for n,raw in before.items():require((source_root/n).read_bytes()==raw,'Changed historical canonical bytes')
    if cache is not None:
        require(source_root is not None,'--cache requires --workflow-source-root');_,actual=p.audit(cache,before[m.META[0]],source_root);fresh=actual['primary_XML_checked']
    if jar is not None:require(pin(Path(jar))==state['producer'],'Restore exact original whole producer; never refresh old hashes')
    if private is not None:
        require(source_root is not None and jar is not None,'--private-root requires original source context and producer');private=Path(private).absolute();old=read(source_root/m.CHECKPOINT);manifest=private/'PRIVATE-CLOSURE.json';require(pin(manifest)==old['private_evidence_manifest'],'Changed preparation evidence closure');saved=read(manifest);require(len(saved)==5875,'Incomplete old preparation evidence');closure(private,saved);checked=len(saved)
    return dict(status=state['status'],counts=result,complete_manifest_entries=873,public_source_and_evidence_files=len(files),predecessor_source_files_checked=109 if source_root is not None else 0,private_preparation_files_checked=checked,fresh_primary_XML_checked=fresh,exact_JAR_checked=jar is not None,catalog_admitted=True,scientific_analysis_reexecuted=False,historical_source_hashes_refreshed=False,whole_goal_complete=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2]);parser.add_argument('--cache',type=Path);parser.add_argument('--workflow-source-root',type=Path);parser.add_argument('--private-root',type=Path);parser.add_argument('--jar',type=Path);a=parser.parse_args()
    try:require(__debug__,'Run without -O');print(json.dumps(verify(a.root,a.cache,a.workflow_source_root,a.private_root,a.jar),indent=2));return 0
    except(FileNotFoundError,PermissionError)as e:print('SKIP3: exact permitted verification prerequisite unavailable: '+str(e)+'. Restore it by an allowed method or retain the skip.',file=sys.stderr);return 3
    except Exception as e:print('FAIL2: GenomicSEM catalog checkpoint invalid: '+repr(e)+'. Preserve evidence and restore the exact named source/metadata/producer. Never refresh old hashes.',file=sys.stderr);return 2

if __name__=='__main__':raise SystemExit(main())
