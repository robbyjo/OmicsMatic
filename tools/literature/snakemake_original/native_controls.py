#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unchanged advanced controls on permitted native Linux temporary storage."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,stat,subprocess,sys,tempfile,time,traceback
if not __debug__:raise RuntimeError('Native-filesystem qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3]
def encoded(value):return (json.dumps(value,indent=2)+'\n').encode()
def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False);stage=None
    try:
        if sys.platform!='linux':raise FileNotFoundError('Use a permitted Linux runtime/filesystem for FIFO controls')
        stage=Path(tempfile.mkdtemp(prefix='omicsmatic-snakemake-',dir='/tmp')).resolve();assert stage.parent==Path('/tmp') and stage.name.startswith('omicsmatic-snakemake-')
        # The deployed scripts/resources keep their exact bytes and their ordinary layout.
        sources={}
        for name in ['prototype_driver.py','advanced_driver.py']:
            source=ROOT/'tools/literature/snakemake_original'/name;target=stage/'tools/literature/snakemake_original'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target);assert pin(source)==pin(target);sources[source.relative_to(ROOT).as_posix()]=pin(source)
        helpers=ROOT/'src/main/resources/org/jlinalg/omics/snakemake-original-v1';shutil.copytree(helpers,stage/'src/main/resources/org/jlinalg/omics/snakemake-original-v1')
        for path in helpers.rglob('*'):
            if path.is_file():sources[path.relative_to(ROOT).as_posix()]=pin(path)
        fifo=stage/'native-fifo-capability-probe';os.mkfifo(fifo);assert stat.S_ISFIFO(fifo.stat().st_mode)
        deployment=dict(native_stage=str(stage),source_deployment='Byte-exact qualifier/resource deployment to native Linux temporary storage',git_identity_claimed=False,source_files=sources,fifo_supported=True,original_ntfs_failure_retained=str(ROOT/'build/snakemake-original/advanced-driver-1'),native_stage_retained=True)
        (out/'DEPLOYMENT.json').write_bytes(encoded(deployment));command=[sys.executable,'-I','-B',str(stage/'tools/literature/snakemake_original/advanced_driver.py'),'--out',str(stage/'build/controls')];start=time.monotonic()
        with (out/'process.stdout').open('xb') as so,(out/'process.stderr').open('xb') as se:process=subprocess.run(command,stdout=so,stderr=se,stdin=subprocess.DEVNULL,timeout=600)
        (out/'PROCESS.json').write_bytes(encoded(dict(command=command,exit_code=process.returncode,seconds=round(time.monotonic()-start,3))))
        shutil.copytree(stage/'build/controls',out/'records',symlinks=True)
        assert process.returncode==0,'Inspect retained native controls and original diagnostics'
        result=json.loads((out/'records/COMPLETED.json').read_bytes());assert len(result['cases'])==5
        assert all(pin(ROOT/name)==expected for name,expected in sources.items())
        (out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_NATIVE_LINUX_ADVANCED_ORIGINAL_DRIVER_CONTROLS_NOT_JAVA_ADMISSION',native_stage=str(stage),cases=result['cases'],fifo_supported=True,source_code_changed_for_filesystem=False,new_catalog_credit=0,whole_goal_complete=False)))
        print('PASS_NATIVE_LINUX_ADVANCED_ORIGINAL_DRIVER_CONTROLS_NOT_JAVA_ADMISSION',flush=True);return 0
    except Exception as error:
        (out/'FAILURE.json').write_bytes(encoded(dict(reason=repr(error),traceback=traceback.format_exc(),native_stage=str(stage),required_action='Retain all native scratch files and exported diagnostics. Missing/forbidden FIFO support skips dependent pipe analysis; use a permitted native Linux filesystem. No user analysis or catalog credit is admitted by these controls.')));raise
if __name__=='__main__':raise SystemExit(main())
