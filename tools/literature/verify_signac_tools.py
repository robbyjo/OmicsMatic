# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the public Signac interface evidence; never execute or install external tools."""
import sys
from signac_tools_evidence import verify

if __name__ == '__main__':
    try:
        if not __debug__:
            raise RuntimeError('Never disable evidence/source checks')
        verify(emit=True)
    except Exception as failure:
        print('SIGNAC EVIDENCE FAIL2: '+str(failure)+'\nRequired action: retain the evidence and correct the named source/archive mismatch before catalog admission. No partial proof is accepted.',file=sys.stderr)
        raise SystemExit(2)
