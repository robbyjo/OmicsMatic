# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recover metadata for the already-installed reference; do not reinstall or alter the failed attempt."""
import argparse
import json
import subprocess
from pathlib import Path
from setup_htseq_reference import ROOT,BASE,DISTRO,linux,pin,runtime_inventory


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve()
    setup=BASE/'reference-setup-1'
    assert out.is_relative_to(BASE) and not out.exists()
    assert json.loads((setup/'install.process.json').read_bytes())['exit_code']==0
    assert json.loads((setup/'probe.process.json').read_bytes())['exit_code']==0
    failure=json.loads((setup/'FAILURE.json').read_bytes())
    assert failure['status']=='FAIL2_HTSEQ_REFERENCE_SETUP' and 'OSError' in failure['error']
    out.mkdir()
    prefix=setup/'venv'
    probe='import HTSeq,numpy,pysam,sys,json;assert (HTSeq.__version__,numpy.__version__,pysam.__version__)==("2.1.2","2.2.6","0.23.3");print(json.dumps(dict(python=sys.version,HTSeq=HTSeq.__version__,numpy=numpy.__version__,pysam=pysam.__version__)))'
    argv=['wsl.exe','-d',DISTRO,'--',linux(prefix/'bin/python'),'-I','-c',probe]
    result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120)
    (out/'probe.stdout').write_bytes(result.stdout)
    (out/'probe.stderr').write_bytes(result.stderr)
    def save(path,value):
        path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
    save(out/'probe.process.json',dict(command=argv,exit_code=result.returncode))
    assert result.returncode==0
    inventory=runtime_inventory(prefix,out)
    save(out/'RUNTIME-FILES.json',inventory)
    save(out/'COMPLETED.json',dict(schema_version=1,status='recovered-private-exact-HTSeq-reference-inventory',
        python=linux(prefix/'bin/python'),profile=json.loads(result.stdout),runtime_files=len(inventory['files']),
        runtime_symlinks=len(inventory['symlinks']),runtime_file_inventory=pin((out/'RUNTIME-FILES.json').read_bytes()),
        parent_setup='reference-setup-1',parent_failure=pin((setup/'FAILURE.json').read_bytes()),
        attempted_setup_source=pin((setup/'attempted-setup.py').read_bytes()),
        recovery_source=pin(Path(__file__).read_bytes()),reused_already_installed_runtime=True,
        dependency_installation_repeated=False,shared_or_system_python_modified=False,
        old_failure_preserved=True,original_method_controls_pending=True,implementation_credit=False,
        warning='This recovers Linux metadata only. Failed initial inventory remains historical; no analysis result or whole tool integration is admitted.'))
    print(json.dumps(dict(status='recovered-existing-reference-without-reinstall',runtime_files=len(inventory['files']),runtime_symlinks=len(inventory['symlinks']))))


if __name__=='__main__':
    if not __debug__:
        raise RuntimeError('Never disable recovery provenance checks')
    main()
