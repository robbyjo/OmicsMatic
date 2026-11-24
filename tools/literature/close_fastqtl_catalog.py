"""Guarded FastQTL paper-use catalog admission CLI."""
import sys
if not __debug__:
    print('FAIL2: FastQTL source-audit assertions require normal Python; rerun without -O. No catalog result is admitted.',file=sys.stderr)
    raise SystemExit(2)
from fastqtl_catalog.catalog import main
if __name__=="__main__":raise SystemExit(main())
