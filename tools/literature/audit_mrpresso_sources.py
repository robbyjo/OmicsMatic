"""Read-only full250 primary MR-PRESSO audit; choose a NEW output directory."""
from pathlib import Path
import argparse,json
from mrpresso_catalog.primary import audit

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--cache',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    try:
        result=audit(args.cache);args.out.mkdir(parents=True,exist_ok=False)
        (args.out/'ROLES.json').write_bytes((json.dumps(result,indent=2,ensure_ascii=False)+'\n').encode('utf-8'))
        print(json.dumps(dict(status=result['status'],papers_checked=250,positive_roles=len(result['roles']),discussion_only=len(result['discussion_only']),new_reviews=0,new_family_credit=0)))
    except (OSError,ValueError,KeyError) as error:parser.exit(2,'FAIL2: source audit refused: '+str(error)+'\n')

if __name__=='__main__':main()
