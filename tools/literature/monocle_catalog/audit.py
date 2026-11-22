"""Compare a fresh all250 Monocle source audit with the frozen nine-role scope census."""
from pathlib import Path
import argparse,sys
from monocle_catalog import primary as p

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--primary-cache',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();owned=False
    try:
        p.require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');p.require(not args.out.exists(),'Use NEW output');args.out.mkdir(parents=True,exist_ok=False);owned=True
        fresh=p.audit(args.primary_cache);p.require(fresh==p.frozen(),'Fresh source roles differ from frozen census');(args.out/'RESULT.json').write_bytes(p.encoded(dict(status='PASS_FRESH250_MONOCLE9_SOURCE_CENSUS',census=p.pin(p.encoded(fresh)),papers=250,roles=9,pointers=15,canonical_metadata_modified=False)));print('PASS fresh250-paper/nine-role Monocle source audit');return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        if owned:(args.out/'FAILURE.json').write_bytes(p.encoded(dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),required_action='Supply the exact permitted primary XML cache for SKIP3;retain evidence and resolve source/census contradictions for FAIL2;retry NEW output. No model or canonical update admitted.')))
        print(('SKIP3' if code==3 else 'FAIL2')+': '+str(error),file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
