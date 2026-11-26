"""Verify frozen ALLCools source, complete retained proof and bounded catalog admission."""
from pathlib import Path
import argparse,hashlib,json,sys,zipfile
import allcools_posterior_catalog as catalog
import allcools_posterior_model as model
from contact_primary import audit as primary_audit
from gemma_primary import require
from verify_diann_workflows import ByteAudit,safe_member
ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/allcools-posterior-validation'
def audit(root,private,source,artifact=None,cache=None):
 root,private,source=(Path(p).absolute()for p in (root,private,source));artifact=Path(artifact).absolute()if artifact is not None else root
 folder=root/FOLDER;state=json.loads((folder/'STATUS.json').read_bytes());require(state['status']=='VALIDATED_SCOPED_NATIVE_ALLCOOLS_POSTERIOR_AND_CATALOG'and not state['whole_goal_complete'],'Different or expanded checkpoint')
 reader=ByteAudit()
 for name,key in [('SOURCES.json','sources'),('PRIVATE-INDEX.json','private_index'),('QUALIFICATION.json','qualification')]:require(reader.pin(folder/name)==state[key],'Changed frozen declaration: '+name)
 sources=None;counts=[]
 for name,base in [('SOURCES.json',source),('PRIVATE-INDEX.json',private)]:
  rows=json.loads((folder/name).read_bytes());require(isinstance(rows,dict),'Expected named evidence index')
  for member,expected in rows.items():require(safe_member(member)and reader.pin(base/member)==expected,'Changed exact frozen member: '+member)
  counts.append(len(rows))
  if name=='SOURCES.json':sources=rows
 require(counts==[state['source_files'],state['private_evidence_files']],'Different frozen populations')
 code=Path(__file__).absolute().parent
 for module in tuple(sys.modules.values()):
  name=getattr(module,'__file__',None)
  if name and Path(name).absolute().parent==code and Path(name).suffix=='.py':require(reader.pin(Path(name))==sources['tools/literature/'+Path(name).name],'Use exact archived verifier and imports: '+Path(name).name)
 q=json.loads((source/model.Q).read_bytes());model.qualified(q);require(reader.pin(source/model.Q)==state['qualification'],'Different source-context qualification')
 catalog.private_controls(source,private,q);result=catalog.check(source,artifact);require(result==state['catalog_result'],'Different bounded catalog result')
 ui=json.loads((private/'ui-1/COMPLETED.json').read_bytes());require(ui['status'].startswith('PASS_ALLCOOLS_POSTERIOR_AND_CONTACT_SOURCE_ROLES')and not ui['page_errors']and(ui['native'],ui['missing'])==(34,253),'Missing current role/filter/action UI proof')
 snap=private/'runtime-snapshot-1';runtime=json.loads((snap/'COMPLETED.json').read_bytes());require(runtime['status']=='PASS_PRIVATE_RUNTIME_EXACT_REGULAR_FILE_BYTE_SNAPSHOT'and not runtime['raw_runtime_published'],'Missing private source-runtime byte evidence')
 inventory=json.loads((snap/'FILES.json').read_bytes())['regular_files'];require(reader.pin(snap/'PRIVATE-RUNTIME-BYTES.zip')==runtime['archive']and reader.pin(snap/'FILES.json')==runtime['file_inventory']and len(inventory)==runtime['regular_files'],'Changed private runtime snapshot')
 # Read every archived regular-file byte without thousands of remote filesystem opens.
 with zipfile.ZipFile(snap/'PRIVATE-RUNTIME-BYTES.zip')as z:
  require(len(z.namelist())==len(set(z.namelist()))==len(inventory)and set(z.namelist())==set(inventory),'Different archived runtime file population')
  for name,expected in inventory.items():
   require(safe_member(name),'Unsafe archived runtime path');h=hashlib.sha256();count=0
   with z.open(name)as f:
    for block in iter(lambda:f.read(1048576),b''):h.update(block);count+=len(block)
   require(dict(bytes=count,sha256=h.hexdigest())==expected,'Changed archived source-runtime bytes: '+name)
 primary=primary_audit(source,cache)if cache is not None else None
 reader.unchanged()
 return dict(status='PASS_FROZEN_NATIVE_ALLCOOLS_POSTERIOR_SOURCE_AND_EVIDENCE',source_files=counts[0],private_evidence_files=counts[1],archived_runtime_regular_files=len(inventory),producer=q['producer'],catalog_result=result,full_primary_census_rechecked=primary is not None,whole_goal_complete=False)
def main():
 if sys.version_info<(3,11):
  print('SKIP3 FROZEN ALLCOOLS AUDIT SKIPPED: Python 3.11 or newer is required. Required action: supply that permitted interpreter or retain the audit skip.',file=sys.stderr);return 3
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private-root',type=Path,required=True);p.add_argument('--source-context',type=Path);p.add_argument('--artifact-root',type=Path);p.add_argument('--cache',type=Path);a=p.parse_args()
 try:print(json.dumps(audit(a.root,a.private_root,a.source_context or a.root,a.artifact_root,a.cache),sort_keys=True));return 0
 except Exception as e:
  code=3 if isinstance(e,(FileNotFoundError,PermissionError))else 2;print(('SKIP3 DEPENDENT FROZEN ALLCOOLS AUDIT SKIPPED: 'if code==3 else'FAIL2 NO FROZEN ALLCOOLS AUDIT ADMITTED: ')+str(e)+'. Required action: restore exact permitted sources, producer and retained proof. Use archived entrypoints/imports for historical checkpoints. Keep failures and original contexts; never refresh old hashes.',file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
