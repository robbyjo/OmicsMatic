"""Verify frozen native fragment-preparation source and private evidence without resealing."""
from pathlib import Path
import argparse,hashlib,json,sys

ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/spectral-annotations-validation'

def require(yes,message):
    if not yes:raise ValueError(message)

def pin(path):
    require(path.is_file()and not path.is_symlink()and path.resolve()==path.absolute(),'Require canonical regular checkpoint member: '+str(path))
    with path.open('rb')as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())

def audit(root,private,source):
    root,private,source=(Path(x).absolute()for x in(root,private,source));folder=root/FOLDER
    state=json.loads((folder/'STATUS.json').read_bytes())
    for name,key in(('SOURCES.json','sources'),('PRIVATE-INDEX.json','private_index'),('QUALIFICATION.json','qualification')):require(pin(folder/name)==state[key],'Changed checkpoint declaration: '+name)
    counts=[]
    for name,base in(('SOURCES.json',source),('PRIVATE-INDEX.json',private)):
        rows=json.loads((folder/name).read_bytes())
        for member,expected in rows.items():
            require(member and'\\'not in member and':'not in member and not member.startswith('/')and all(p not in('','.','..')for p in member.split('/')),'Unsafe checkpoint path')
            require(pin(base/member)==expected,'Changed frozen '+name+' member: '+member)
        counts.append(len(rows))
    require(counts==[state['source_files'],state['private_evidence_files']],'Different frozen populations')
    q=json.loads((folder/'QUALIFICATION.json').read_bytes())
    require(pin(root/q['producer_private_path'])==q['producer'],'Restore exact original producer; never refresh historical expected hashes')
    require(q['catalog_family_credit']==0 and q['raw_MS_search_qualified']is False and q['whole_goal_complete']is False,'Expanded preparation checkpoint claim')
    return dict(status='PASS_FROZEN_SPECTRAL_ANNOTATION_SOURCE_AND_PRIVATE_EVIDENCE',source_files=counts[0],private_evidence_files=counts[1],producer=q['producer'],catalog_family_credit=0,raw_MS_search_qualified=False,whole_goal_complete=False)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private-root',type=Path,required=True);p.add_argument('--source-context',type=Path);a=p.parse_args()
    try:print(json.dumps(audit(a.root,a.private_root,a.source_context or a.root),sort_keys=True));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2
        print(('SKIP3 DEPENDENT SAVED AUDIT SKIPPED: 'if code==3 else'FAIL2 NO SAVED AUDIT ADMITTED: ')+str(error)+'. Required action: preserve attempts and old hashes; restore the exact permitted source, producer and private evidence.',file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
