#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the unchanged250-paper audit with only an exact reviewed dispatcher view."""
from audit_snapshot import main
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source checks')
 raise SystemExit(main())
