# ---- spyrrow ----
# spatial data analysis

import os

# set version
__version__ = '0.1'


from packaging import version

# move on the actual spyrrow imports
from . import sst
from .sst import utils
from .sst import Adata
from .sst import pl
from .sst import register
from . import visium
from .visium import utils
from .visium import Adata
from .visium import visual
from .visium import spaceGCN
