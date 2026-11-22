"""Create a NEW source audit; full selected text stays in its private build folder."""
from pathlib import Path
import argparse
import json
import sys
import traceback
from genomicranges_catalog.primary import ROOT, audit, encoded, require


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    owned = False
    try:
        require(__debug__, 'Run Python without -O')
        require(args.out.resolve().is_relative_to(ROOT / 'build'), 'Use a NEW current-owner build output')
        args.out.mkdir(parents=True, exist_ok=False)
        owned = True
        public, private = audit(args.cache)
        (args.out / 'PUBLIC-CENSUS.json').write_bytes(encoded(public))
        (args.out / 'PRIVATE-SELECTED-TEXT.json').write_bytes(encoded(private))
        print(json.dumps(dict(status=public['status'], papers=public['papers_checked'], roles=len(public['roles']),
                              new_reviews_pending=len(public['pending_roles']), catalog_modified=False)))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        if owned:
            (args.out / 'FAILURE.json').write_bytes(encoded(dict(status='SKIP3' if code == 3 else 'FAIL2',
                error=repr(error), traceback=traceback.format_exc(), complete_result=False,
                required_action='Keep this attempt. Supply the named permitted primary cache/dependency or resolve the exact source mismatch; then retry NEW output. No provider attribution, family credit or admitted catalog result from this failure.')))
        print(('SKIP3' if code == 3 else 'FAIL2') + ': ' + str(error), file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
