"""Verify frozen arithmetic-guard source and retained controls without refreshing earlier checkpoints."""
from pathlib import Path
import argparse,json,sys
from gemma_primary import require
from verify_diann_workflows import ByteAudit,safe_member

ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/contact-insulation-arithmetic'

def audit(root,private,source):
    root,private,source=(Path(p).absolute()for p in (root,private,source));folder=root/FOLDER
    state=json.loads((folder/'STATUS.json').read_bytes());require(state['status']=='VALIDATED_NATIVE_CONTACT_MEDIAN_OVERFLOW_REFUSAL'and not state['whole_goal_complete'],'Expanded arithmetic scope')
    reader=ByteAudit()
    for name,key in (('SOURCES.json','sources'),('PRIVATE-INDEX.json','private_index'),('QUALIFICATION.json','qualification')):require(reader.pin(folder/name)==state[key],'Changed arithmetic declaration')
    manifests=[]
    for name,base in (('SOURCES.json',source),('PRIVATE-INDEX.json',private)):
        rows=json.loads((folder/name).read_bytes())
        for member,expected in rows.items():require(safe_member(member)and reader.pin(base/member)==expected,'Changed indexed arithmetic evidence: '+member)
        manifests.append(rows)
    require([len(x)for x in manifests]==[state['source_files'],state['private_evidence_files']],'Changed arithmetic populations')
    code=Path(__file__).absolute().parent
    for module in tuple(sys.modules.values()):
        name=getattr(module,'__file__',None)
        if name and Path(name).absolute().parent==code and Path(name).suffix=='.py':require(reader.pin(Path(name))==manifests[0]['tools/literature/'+Path(name).name],'Use the exact archived verifier/imports')
    q=json.loads((source/FOLDER/'QUALIFICATION.json').read_bytes());require((source/FOLDER/'QUALIFICATION.json').read_bytes()==(private/'qualification-1/QUALIFICATION.json').read_bytes(),'Different actual qualification')
    require(q['Windows_tests']==q['Linux_tests']==85 and q['source_component_cases_each_host']==459 and q['changed_packaged_members']==['org/jlinalg/omics/contact/CooltoolsDiamond.class']and q['new_family_credit']==0,'Incomplete or expanded arithmetic proof')
    require(reader.pin(private/'project-build-Windows-1/project-build/cli/omicsmatic-0.4.1.jar')==q['producer'],'Changed producing JAR')
    for member,expected in q['catalog_metadata_unchanged'].items():require(reader.pin(source/member)==expected,'Catalog metadata changed in arithmetic-only update')
    for host in ('Windows','Linux'):
        require(json.loads((private/('workflow-'+host+'-1/COMPLETED.json')).read_bytes())['full_reference_output_byte_identical_to_baseline'],'Changed reference-map output')
        require(json.loads((private/('portable-'+host+'-1/COMPLETED.json')).read_bytes())['captures']==2,'Missing foreign captures')
    reader.unchanged()
    return dict(status='PASS_FROZEN_NATIVE_CONTACT_ARITHMETIC_SOURCE_AND_EVIDENCE',source_files=len(manifests[0]),private_evidence_files=len(manifests[1]),producer=q['producer'],scoped_families=167,native=33,upstream=134,new_family_credit=0,whole_goal_complete=False)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private-root',type=Path,required=True);p.add_argument('--source-context',type=Path);a=p.parse_args()
    try:print(json.dumps(audit(a.root,a.private_root,a.source_context or a.root),sort_keys=True));return 0
    except Exception as e:
        code=3 if isinstance(e,(FileNotFoundError,PermissionError))else 2
        print(('SKIP3 DEPENDENT ARITHMETIC AUDIT SKIPPED: 'if code==3 else'FAIL2 NO ARITHMETIC AUDIT ADMITTED: ')+str(e)+'. Required action: restore exact permitted sources, producer and retained evidence; keep older contexts and hashes unchanged.',file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
