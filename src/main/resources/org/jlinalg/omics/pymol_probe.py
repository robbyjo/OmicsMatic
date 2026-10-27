"""Isolated original-runtime version probe; no structure or user script is loaded."""
import json
import platform
import sys

sys.path.insert(0, sys.argv[1])
import numpy
import pymol2

with pymol2.PyMOL() as instance:
    print(json.dumps(dict(python=list(sys.version_info[:3]),
                          implementation=platform.python_implementation(),
                          numpy=numpy.__version__, pymol=instance.cmd.get_version(),
                          atoms=instance.cmd.count_atoms())))
