#!/usr/bin/env python3
"""Read the scoped PRSice checkpoint without executing or installing upstream software."""
from pathlib import Path
import argparse,json,sys
from check import pin

def require(value,message):
    if not value:raise ValueError(message)
def read(path):return json.loads(path.read_bytes())
def closure(root,members):
    require(bool(members),'Empty closure')
    for name,identity in members.items():
        path=Path(name);require(not path.is_absolute()and'..'not in path.parts and'\\'not in name and':'not in name,'Unsafe saved member');file=root/path;require(file.is_file()and file.resolve()==file.absolute()and pin(file)==identity,'Changed/missing saved member: '+name)

def verify(args):
    root=args.root.absolute();folder=root/'docs/literature/prsice-workflow-checkpoint';state=read(folder/'STATUS.json');require(state['status']=='PASS_SCOPED_ORIGINAL_PRSICE_WORKFLOW_PENDING_CATALOG','Unqualified checkpoint');require(not state['catalog_admitted']and not state['whole_goal_complete']and state['new_family_credit']==state['new_review_credit']==0,'Expanded family/review/goal claim');require(state['profile']=='prsice-2.3.5-9dbf55e','Changed runtime profile')
    require(pin(folder/'FILES.json')==state['public_manifest'],'Changed public closure');members=read(folder/'FILES.json');closure(root,members);v=read(folder/'VALIDATION.json');require(v['Java_tests']==dict(windows=38,linux=38),'Incomplete Java validation');require(v['main_checks']==dict(windows=50,linux=50)and v['scoring_matrix_checks']==dict(windows=13,linux=13),'Incomplete original runtime qualification');require(v['reader_refusals']==dict(windows=8,linux=8)and v['independent_R_fit_rows']==40 and v['independent_R_summary_rows']==8,'Incomplete independent/refusal checks');require(v['portable_saved_inspections']==4 and v['documented_example_runs']==5 and v['setup_version_checks']==dict(windows=True,linux=True),'Incomplete portable/setup/example qualification');require(v['internal_full_precision_identity_claimed']is False and v['original_paper_analyses_reexecuted']is False,'Expanded scientific claim')
    canonical=read(folder/'CANONICAL.json');closure(root,canonical);require(read(folder/'PREDECESSOR-CONTEXT.json')==state['predecessor_context'],'Changed predecessor preservation description');checked=0
    if args.jar is not None:
        if not args.jar.is_file():raise FileNotFoundError('Restore the exact retained producer JAR')
        require(pin(args.jar)==state['producer'],'Changed producer; restore exact JAR, never refresh old hashes')
    if args.private_root is not None:
        require(args.jar is not None,'--private-root requires --jar');private=args.private_root.absolute();manifest=private/'PRIVATE-CLOSURE.json';require(pin(manifest)==state['private_evidence_manifest'],'Changed private evidence closure');private_members=read(manifest);require(len(private_members)==state['private_evidence_files'],'Changed retained evidence population');closure(private,private_members);checked=len(private_members)
    return dict(status='PASS_SCOPED_PRSICE_WORKFLOW_CHECKPOINT',public_source_files=len(members),canonical_files=len(canonical),private_evidence_files_checked=checked,original_fit_rows_independently_checked=40,catalog_admitted=False,new_family_credit=0,new_review_credit=0,whole_goal_complete=False,original_software_executed_now=False)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[3]);p.add_argument('--jar',type=Path);p.add_argument('--private-root',type=Path);a=p.parse_args()
    try:print(json.dumps(verify(a),indent=2));return 0
    except (FileNotFoundError,PermissionError)as e:code=3;message='SKIP3: DEPENDENT CHECKPOINT VERIFICATION SKIPPED. '+str(e)+'. Restore exact prerequisites only where permitted; otherwise keep the skip.'
    except Exception as e:code=2;message='FAIL2: NO VERIFIED RESULT. '+repr(e)+'. Preserve evidence, resolve the named cause and never refresh old hashes.'
    print(message,file=sys.stderr);return code
if __name__=='__main__':sys.exit(main())
