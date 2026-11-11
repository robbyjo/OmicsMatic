# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Closed read-only runtime identity probe; no user Python code or analysis.
import importlib.metadata as md
import json
import pathlib
import sys
try:
    import MACS2
    import MACS2.IO.Parser
    import numpy
except (ImportError, OSError) as error:
    print(json.dumps(dict(status='SKIP3_DEPENDENCY_UNAVAILABLE',error=str(error))))
    sys.exit(0)
print(json.dumps(dict(python='.'.join(map(str,sys.version_info[:3])),prefix=sys.prefix,
    executable=sys.executable,package=str(pathlib.Path(MACS2.__file__).parent.resolve()),
    parser=str(pathlib.Path(MACS2.IO.Parser.__file__).resolve()),
    versions=dict(MACS2=md.version('MACS2'),numpy=md.version('numpy'))),sort_keys=True))
