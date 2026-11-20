"""Fresh source audit and proposed edgeR crosswalk; never mutates the catalog."""
from pathlib import Path
import argparse
import json
import sys
from edger_catalog.primary import audit, require
from edger_catalog.coverage import build


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--primary-cache', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    owned = False
    try:
        require(__debug__ and sys.version_info >= (3, 11), 'Use Python 3.11+ without -O')
        require(not args.out.exists(), 'Choose NEW output; preserve earlier source/review evidence')
        args.out.mkdir(parents=True, exist_ok=False)
        owned = True
        primary = audit(args.primary_cache)
        result = build(primary)
        for name, value in [('PRIMARY.json', primary), ('COVERAGE.json', result)]:
            (args.out / name).write_bytes((json.dumps(value, indent=2, ensure_ascii=False) + '\n').encode())
        (args.out / 'COMPLETED.json').write_bytes((json.dumps(dict(status=result['status'],
            source_roles=17, explicit_function_mentions=result['explicit_function_mentions'],
            new_canonical_credit=0, canonical_reviews_appended=0, whole_goal_complete=False), indent=2) + '\n').encode())
        print('PASS 250 exact sources; 17 edgeR roles; literal function witnesses; no canonical mutation')
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        value = dict(status='SKIP3' if code == 3 else 'FAIL2', error=repr(error), complete_result=False,
            required_action='Restore the named exact permitted source/cache or retain the dependent skip. Preserve failures, correct source/schema/integrity/output issues and retry NEW output. Do not refresh old hashes or infer missing author functions.')
        if owned:
            (args.out / 'FAILURE.json').write_bytes((json.dumps(value, indent=2) + '\n').encode())
        print(value['status'] + ': ' + str(error) + '\n' + value['required_action'], file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
