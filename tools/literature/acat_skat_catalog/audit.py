"""Run a fresh XML/PDF audit; no catalog mutation or scientific model execution."""
import argparse,sys,traceback
from pathlib import Path
from acat_skat_catalog import primary as p
ACTION='SKIP3: supply the named permitted XML/PDF or pypdf6.21.4;otherwise retain the skipped dependent audit. FAIL2: preserve source and failed evidence,resolve the identity/census contradiction and retry NEW output. No complete source audit or analysis is admitted.'
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for key in ['primary-cache','supplement-pdf','out']:parser.add_argument('--'+key,type=Path,required=True)
    args=parser.parse_args();owned=False
    try:
        p.require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');p.require(args.out.resolve().is_relative_to(p.ROOT/'build'),'Use NEW workspace build output');args.out.mkdir(parents=True,exist_ok=False);owned=True
        result=p.audit(args.primary_cache,args.supplement_pdf);p.require(result==p.frozen(),'Fresh source review differs from frozen census')
        (args.out/'RESULT.json').write_bytes(p.encoded(dict(status='PASS_FRESH250_ACAT_SKAT22_XML_AND_P190_SUPPLEMENT',papers=250,roles=22,XML_candidate_paragraphs=95,PDF_pages=3,census=p.pin(p.encoded(result)),canonical_metadata_modified=False,scientific_models_executed=False)))
        print('PASS fresh250 ACAT/SKAT22 roles;95 candidate paragraphs;3PDF pages');return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        if owned:(args.out/'FAILURE.json').write_bytes(p.encoded(dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),traceback=traceback.format_exc(),complete_result=False,required_action=ACTION)))
        print(('SKIP3' if code==3 else 'FAIL2')+': '+str(error)+'\n'+ACTION,file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
