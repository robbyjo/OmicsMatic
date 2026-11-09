# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Fixed isolated launcher. Does not patch any Sniffles package or callable."""
import sys
import os
import json
from pathlib import Path


def save(path,value):
    with path.open('x',encoding='utf8',newline='\n') as stream:
        json.dump(value,stream,indent=2,allow_nan=False);stream.write('\n')


def startup(root,output):
    assert sys.version_info[:3]==(3,12,3), 'Exact tested Python required'
    assert sys.flags.no_site and sys.flags.no_user_site and sys.flags.safe_path
    assert sys.flags.dont_write_bytecode and not sys.flags.ignore_environment
    assert sys.flags.optimize==0
    assert sys.flags.hash_randomization==0 and os.environ.get('PYTHONHASHSEED')=='0'
    assert os.environ.get('PYTHONHOME')=='/usr'
    expected=['/usr/lib/python312.zip','/usr/lib/python3.12','/usr/lib/python3.12/lib-dynload']
    assert sys.path==expected,('Unexpected pre-import path',sys.path)
    assert sys.prefix==sys.base_prefix=='/usr'
    assert Path(sys.executable)==root/'python'
    cache=Path(sys.pycache_prefix)
    assert cache.parent==output and not cache.exists()
    # Original Sniffles uses bare exit(). Normal CPython site startup provides
    # this helper. Initialize only the original stdlib helper function; importing
    # site under -S does not run site.main, .pth files or customization hooks.
    import site
    site.setquit()
    # -S suppresses all .pth, sitecustomize and usercustomize processing.
    # The private site follows the bound standard library, preventing shadowing.
    site=root/'site';sys.path.append(str(site))
    report=dict(status='PASS_EXPLICIT_PYTHON_STARTUP_ONLY',python=sys.version,
                executable=sys.executable,original_sys_path=expected,private_site=str(site),
                no_site=sys.flags.no_site,no_user_site=sys.flags.no_user_site,
                safe_path=sys.flags.safe_path,ignore_environment=sys.flags.ignore_environment,
                dont_write_bytecode=sys.flags.dont_write_bytecode,
                optimize=sys.flags.optimize,
                hash_randomization=sys.flags.hash_randomization,python_hash_seed='0',
                pycache_prefix=str(cache),startup_hooks_executed=False,
                standard_site_exit_helpers_initialized=True)
    save(output/'python-startup.json',report)
    return report


def main():
    assert len(sys.argv)>=4
    mode=sys.argv[1];root=Path(sys.argv[2]);output=Path(sys.argv[3])
    assert root.is_absolute() and output.is_absolute() and root.resolve()==root and output.resolve()==output
    startup(root,output)
    if mode=='health':
        import importlib.metadata as metadata
        import pysam,psutil,edlib
        assert metadata.version('pysam')=='0.23.3' and metadata.version('edlib')=='1.3.9.post1' and metadata.version('psutil')=='7.1.0'
        modules={m.__name__:str(Path(m.__file__).resolve()) for m in [pysam,psutil,edlib]}
        assert all(Path(p).is_relative_to(root/'site') for p in modules.values())
        save(output/'HEALTH.json',dict(status='PASS_BOUND_ORIGINAL_DEPENDENCY_IMPORTS_ONLY',packages=dict(pysam='0.23.3',edlib='1.3.9.post1',psutil='7.1.0'),modules=modules,
                                      source_namespace_patched=False,analysis_installed_software=False))
    elif mode=='version':
        import runpy
        assert len(sys.argv)==5
        tag=sys.argv[4];assert tag in ['v2.0.2','v2.0.7','v2.3.2','v2.4']
        source=root/'sources'/tag/'src'
        sys.path.append(str(source))
        sys.argv=[str(source/'sniffles/sniffles'),'--version']
        runpy.run_path(str(source/'sniffles/sniffles'),run_name='__main__')
    else:
        raise ValueError('Unsupported fixed launcher mode')


if __name__=='__main__':
    try:main()
    except SystemExit:raise
    except BaseException:
        import traceback
        traceback.print_exc();sys.exit(3)
