"""Read back the frozen native contact-insulation sources, catalog and retained named evidence."""
from pathlib import Path
import argparse,json,sys
import contact_insulation_catalog as catalog
from gemma_primary import require
from verify_diann_workflows import ByteAudit,safe_member

ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/contact-insulation-validation'

def audit(root,private,source,artifact=None):
    root,private,source=(Path(p).absolute()for p in (root,private,source));artifact=Path(artifact).absolute()if artifact is not None else root
    folder=root/FOLDER;state=json.loads((folder/'STATUS.json').read_bytes())
    require(state['status']=='VALIDATED_SCOPED_NATIVE_CONTACT_INSULATION_AND_CATALOG'and state['whole_goal_complete']is False,'Different or expanded native checkpoint')
    reader=ByteAudit()
    for name,key in (('SOURCES.json','sources'),('PRIVATE-INDEX.json','private_index'),('QUALIFICATION.json','qualification')):
        require(reader.pin(folder/name)==state[key],'Changed native checkpoint declaration: '+name)
    counts=[];source_rows=None
    for manifest,base in (('SOURCES.json',source),('PRIVATE-INDEX.json',private)):
        rows=json.loads((folder/manifest).read_bytes());require(isinstance(rows,dict),'Require explicit named evidence index')
        for name,expected in rows.items():
            require(safe_member(name),'Unsafe indexed evidence member')
            require(reader.pin(base/name)==expected,'Changed frozen '+manifest+' member: '+name)
        counts.append(len(rows))
        if manifest=='SOURCES.json':source_rows=rows
    require(counts==[state['source_files'],state['private_evidence_files']],'Changed source/evidence populations')
    code=Path(__file__).absolute().parent
    for module in tuple(sys.modules.values()):
        name=getattr(module,'__file__',None)
        if name is None:continue
        path=Path(name).absolute()
        if path.suffix=='.py'and path.parent==code:
            require(reader.pin(path)==source_rows['tools/literature/'+path.name],'Running verifier differs; use the exact archived entrypoint and imports: '+path.name)
    q=json.loads((source/catalog.model.Q).read_bytes());catalog.private_controls(source,private,q)
    require(reader.pin(source/catalog.model.Q)==state['qualification'],'Different source-context qualification')
    result=catalog.check(source,artifact);require(result==state['catalog_result'],'Different native catalog admission')
    reader.unchanged()
    return dict(status='PASS_FROZEN_NATIVE_CONTACT_INSULATION_SOURCE_CATALOG_AND_PRIVATE_EVIDENCE',source_files=counts[0],private_evidence_files=counts[1],physical_files_read=len(reader.memo),producer=q['producer'],catalog_result=result,whole_goal_complete=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--private-root',type=Path,required=True);parser.add_argument('--source-context',type=Path);parser.add_argument('--artifact-root',type=Path);args=parser.parse_args()
    try:print(json.dumps(audit(args.root,args.private_root,args.source_context or args.root,args.artifact_root),sort_keys=True));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2
        print(('SKIP3 DEPENDENT SAVED AUDIT SKIPPED: 'if code==3 else'FAIL2 NO SAVED AUDIT ADMITTED: ')+str(error)+'. Required action: preserve attempts and historical hashes; restore the exact permitted producing JAR, sources and named evidence. Use archived verifier/imports for historical contexts. No complete tool, paper or goal is claimed.',file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
