"""Run the guarded cumulative edgeR catalog owner."""
import sys
sys.dont_write_bytecode = True
from edger_catalog.catalog import main

if __name__ == '__main__':
    raise SystemExit(main())
