"""Read-only full-250 FUSION candidate/homonym audit, retaining failures."""
from pathlib import Path
import argparse
import json
import sys
from fusion_catalog.primary import ROOT, audit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    owned = False
    try:
        if not args.out.resolve().is_relative_to(ROOT / 'build'):
            raise ValueError('Use NEW current-owner build output')
        args.out.mkdir(parents=True, exist_ok=False)
        owned = True
        result = audit(args.cache)
        (args.out / 'CENSUS.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
        print(json.dumps(dict(status=result['status'], papers_checked=250, candidate_papers=len(result['candidates']),
                              software_analysis_or_comparator_papers=6, new_family_credit=0)))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        if owned:
            (args.out / 'FAILURE.json').write_text(json.dumps(dict(status='SKIP3' if code == 3 else 'FAIL2', error=repr(error),
                required_action='Restore exact permitted primary inputs or inspect changed candidates; preserve failed output. No provider/review credit.'), indent=2) + '\n', encoding='utf-8', newline='\n')
        print(('SKIP3: ' if code == 3 else 'FAIL2: ') + str(error), file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
