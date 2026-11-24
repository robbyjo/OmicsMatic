"""Guarded PRSice source-role catalog admission CLI."""
import sys
if not __debug__:
    print('FAIL2: source assertions require normal Python; rerun without -O. No catalog result admitted.',file=sys.stderr)
    raise SystemExit(2)
from prsice_catalog.catalog import main
if __name__=='__main__':raise SystemExit(main())
