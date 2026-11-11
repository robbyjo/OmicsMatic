# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
from __future__ import print_function
import json,os,random,runpy,sys
if sys.argv[1] == 'probe':
 import HTSeq,numpy,pysam
 print(json.dumps(dict(HTSeq=HTSeq.__version__,numpy=numpy.__version__,pysam=pysam.__version__,python='.'.join(str(x) for x in sys.version_info[:3]),
  HTSeq_file=os.path.realpath(HTSeq.__file__),numpy_file=os.path.realpath(numpy.__file__),pysam_file=os.path.realpath(pysam.__file__),
  executable=os.path.realpath(sys.executable)),sort_keys=True))
elif sys.argv[1] == 'count':
 arguments=json.loads(open(sys.argv[2],'rb').read())
 if sys.version_info[0] == 2:arguments=[value.encode('utf8') for value in arguments]
 sys.argv=['htseq-count']+arguments
 runpy.run_module('HTSeq.scripts.count',run_name='__main__')
else:
 raise RuntimeError('Closed HTSeq bridge accepts probe or count only')
