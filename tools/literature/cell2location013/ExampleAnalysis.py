#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Complete explicit synthetic example; no author data, parameter inference or convergence claim."""
import sys
sys.path.insert(0,str(input_directory))
from OriginalControl import analyze
analysis_complete=analyze(input_directory,result_directory/'workflow')
