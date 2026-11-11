# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Set only the public NumPy RNG seed and execute the unchanged original program."""
import runpy,sys
import numpy as np
source=sys.argv[1];np.random.seed(int(sys.argv[2]));sys.argv=[source,*sys.argv[3:]]
runpy.run_path(source,run_name='__main__')
