"""Repeat the exact XML/PDF audit against fresh source bytes; no model or catalog mutation."""
from pathlib import Path
import argparse,sys,traceback
from qtltools_catalog import primary as p

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--primary-cache',required=True,type=Path);parser.add_argument('--supplement-pdf',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();owned=False
    try:
        p.require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');p.require(args.out.resolve().is_relative_to(p.ROOT/'build'),'Use NEW workspace build output');args.out.mkdir(parents=True,exist_ok=False);owned=True
        fresh=p.audit(args.primary_cache,args.supplement_pdf);p.require(fresh==p.frozen(),'Fresh QTLtools source-role census differs')
        (args.out/'RESULT.json').write_bytes(p.encoded(dict(status='PASS_FRESH250_QTLTOOLS9_XML_AND_P190_SUPPLEMENT',papers=250,roles=9,XML_pointers=19,PDF_pages=2,census=p.pin(p.encoded(fresh)),canonical_metadata_modified=False,scientific_models_executed=False)))
        print('PASS fresh250 QTLtools9 roles plus exact P190 publisher supplement');return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        if owned:(args.out/'FAILURE.json').write_bytes(p.encoded(dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),traceback=traceback.format_exc(),required_action='SKIP3: supply the named exact permitted XML/PDF or pypdf6.21.4;otherwise retain the skipped audit. FAIL2: preserve evidence,resolve the source/census contradiction and retry NEW output. No catalog or analysis result admitted.')))
        print(('SKIP3' if code==3 else 'FAIL2')+': '+str(error),file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
