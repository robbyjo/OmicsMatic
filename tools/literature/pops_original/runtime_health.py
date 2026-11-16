#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact dependency and original CLI health; never a statistical analysis."""
from pathlib import Path
import json,platform,subprocess,sys
import numpy,pandas,scipy,sklearn,statsmodels
import statsmodels.api

def main():
    original=Path(sys.argv[1]).resolve();versions=dict(python='.'.join(map(str,sys.version_info[:3])),numpy=numpy.__version__,pandas=pandas.__version__,scipy=scipy.__version__,sklearn=sklearn.__version__,statsmodels=statsmodels.__version__)
    expected=dict(python='3.8.20',numpy='1.19.5',pandas='1.0.5',scipy='1.5.2',sklearn='0.24.2',statsmodels='0.12.2')
    assert versions==expected,(versions,expected)
    commands=[]
    for name in ['0.1/pops.feature_selection.py','0.1/pops.predict_scores.py','0.2/pops.py','0.2/munge_feature_directory.py']:
        command=[sys.executable,'-I','-B',str(original/name),'--help'];result=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=90)
        assert result.returncode==0,(name,result.stderr.decode(errors='replace'));commands.append(dict(source=name,exit_code=result.returncode))
    print(json.dumps(dict(status='PASS_EXACT_ORIGINAL_POPS_RUNTIME_HEALTH_NOT_ANALYSIS',versions=versions,platform=platform.platform(),commands=commands,full_runtime_adoption_complete=False,scientific_analysis_performed=False)),flush=True)

if __name__=='__main__':main()
