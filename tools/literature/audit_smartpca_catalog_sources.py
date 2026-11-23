"""Audit all250 frozen primary records before smartpca catalog admission."""
from pathlib import Path
import argparse,json
from smartpca_catalog.primary import audit,require
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--cache',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args()
    require(__debug__,'Run without -O');require(not a.out.exists(),'Use NEW output');r=audit(a.cache);a.out.parent.mkdir(parents=True,exist_ok=True);a.out.write_text(json.dumps(r,indent=2,ensure_ascii=False)+'\n',encoding='utf-8',newline='\n');print('PASS all250 primary identities and six context-specific smartpca roles; zero credit before admission.')
