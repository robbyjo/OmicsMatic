"""Guarded native cytometry catalog admission; no original paper analysis is run."""
import sys
if not __debug__:
    print('FAIL2: source-audit assertions must remain enabled. Rerun without Python -O; no catalog result is admitted.',file=sys.stderr)
    raise SystemExit(2)
from cytometry_catalog.catalog import main

if __name__=="__main__":raise SystemExit(main())
