# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create closed file-counting profiles from five validated original source bindings."""
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,save


def main():
    binding=json.loads((ROOT/'build/literature-review/htseq-runtime/count-reference-binding-2/COMPLETED.json').read_bytes())
    target=ROOT/'src/main/resources/org/jlinalg/omics/htseq-file-count';assert not target.exists();target.mkdir(parents=True)
    profiles={}
    for row in binding['profiles']:
        version=row['version'];old=version in ['0.5.3','0.6.0'];profile=row['profile']
        py='2.7.18' if old else '3.9.20' if version in ['0.11.1','0.13.5'] else '3.12.3'
        scientific={r['installed_path']:{k:r[k] for k in ['bytes','sha256']} for r in row['scientific_source_bindings']}
        profiles[version]=dict(version=version,python=py,numpy=profile['numpy'],pysam=profile['pysam'],
            site='lib/python'+'.'.join(py.split('.')[:2])+'/site-packages',scientific_sources=scientific,
            generated_source_exceptions=['HTSeq/StepVector.py','HTSeq/_version.py'],
            source_archive_not_installed=row['source_archive_modules_not_installed'],
            nonunique=['none'] if old else ['none','all'] if version=='0.11.1' else ['none','all','fraction'],
            flags_always_scored=old,sam_name_only=version=='0.5.3',
            native_counting_profile=version=='2.1.2')
    save(target/'PROFILES.json',dict(schema_version=1,profiles=profiles,
        scope='Exact installed scientific-source profiles and tested dependency versions. Generated bindings, compiled libraries and interpreter are freshly inventoried during each analysis, not execution-authenticated. No whole historical environment claim. RANDOM is not admitted.'))
    (target/'.gitattributes').write_text('* -text\n',encoding='ascii')
    (target/'bridge.py').write_text('''# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
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
''',encoding='utf8',newline='\n')
    print('Prepared five exact scientific-source file-count profiles and Python2/3 byte-preserving bridge')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable profile derivation checks')
    main()
