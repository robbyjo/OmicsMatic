"""Audit all frozen primary sources for Squidpy roles; never changes catalog credit."""
from pathlib import Path
import argparse,json,sys
from squidpy_catalog.primary import audit,ROOT


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--cache',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    output=args.out.resolve();output.relative_to((ROOT/'build').resolve())
    if output.exists():raise ValueError('Choose NEW output; original evidence is preserved')
    result=audit(args.cache);output.mkdir(parents=True);(output/'ROLES.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print(json.dumps(dict(status=result['status'],primary_XML_checked=250,roles=len(result['roles']),new_family_credit=0,new_reviews=0)));return 0


if __name__=='__main__':
    try:raise SystemExit(main())
    except Exception as error:print('FAIL2: source audit is incomplete: '+str(error),file=sys.stderr);raise SystemExit(2)
