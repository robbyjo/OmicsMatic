"""Read-only full-cohort Genomic SEM source audit. This does not run an analysis."""
from pathlib import Path
import argparse,json,sys
from genomicsem.primary import audit

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2])
    p.add_argument('--cache',required=True,type=Path,help='Directory containing the exact 250 original PMCID.xml.gz files')
    a=p.parse_args(argv)
    try:print(json.dumps(audit(a.root,a.cache),indent=2));return 0
    except (OSError,ValueError) as e:
        print('FAIL2: source audit did not pass: '+str(e)+'. Restore the exact frozen primary XML/corpus/role files. Catalog and analysis status are unchanged.',file=sys.stderr);return 2

if __name__=='__main__':raise SystemExit(main())
