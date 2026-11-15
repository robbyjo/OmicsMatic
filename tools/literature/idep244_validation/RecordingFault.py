# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Inject only a parent JSON-writer I/O fault; never alter original R methods."""
from pathlib import Path
import argparse
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"idep244"))
import run

p = argparse.ArgumentParser(description=__doc__)
p.add_argument("--fault",choices=["after-return","after-promotion"],required=True)
p.add_argument("--runtime",type=Path,required=True)
p.add_argument("--request",type=Path,required=True)
p.add_argument("--out",type=Path,required=True)
a = p.parse_args()
real = run.save
def injected(path,value):
    if a.fault=="after-return" and Path(path).name=="RUN.json":
        raise PermissionError("INJECTED_PARENT_RECORDING_IO_FAILURE_AFTER_ORIGINAL_RETURN")
    result=real(path,value)
    if a.fault=="after-promotion" and Path(path).name=="COMPLETED.json":
        raise PermissionError("INJECTED_PARENT_RECORDING_IO_FAILURE_AFTER_PROMOTION")
    return result
run.save=injected
sys.argv=["run.py","--runtime",str(a.runtime),"--request",str(a.request),"--out",str(a.out)]
raise SystemExit(run.main())
