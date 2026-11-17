#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise complete Java provider verification and contradictory private copies; install nothing."""
from pathlib import Path
import argparse,json,os,shutil,sys,traceback
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from setup_deployment import manager_tree
from setup_runtime import pin,write
from qualify_deployment import execute

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--compiled',type=Path,required=True);p.add_argument('--adoption',type=Path,required=True);p.add_argument('--native-out',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);stage=None
    shutil.copyfile(__file__,out/'as-run-qualifier.py')
    try:
        assert sys.platform=='linux';stage=a.native_out.absolute();assert stage==stage.resolve() and stage.parent.is_dir() and not stage.exists() and not str(stage).startswith('/mnt/');stage.mkdir();compiled=json.loads((a.compiled/'COMPILED.json').read_bytes());adoption=a.adoption.resolve()
        for name,expected in compiled['sources'].items():assert pin(ROOT/name)==expected,name
        source=out/'as-run';source.mkdir()
        for name in ['qualify_providers.py','setup_deployment.py','setup_runtime.py','qualify_deployment.py']:shutil.copyfile(HERE/name,source/name)
        command=['/usr/bin/java','-cp',compiled['classpath'],compiled['probe']];environment=dict(PATH='/usr/bin:/bin',HOME=str(stage),LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC');rows=[]
        def run(name,mode,request,positive):
            result=execute(out,name,[*command,mode,str(request)],environment,stage,180);assert (result['exit_code']==0)==positive,(name,'Inspect retained Java diagnostics');rows.append(dict(name=name,expected_success=positive,exit_code=result['exit_code']));print(name+' PASS_EXPECTED_'+str(result['exit_code']),flush=True)
        run('actual-adopted-providers','providers',adoption,True)
        for name in ['unchanged','changed-bytes','missing-file','unlisted-file','changed-mode','changed-link','resolved-dangling-target','file-to-fifo','malformed-node','malformed-file-pin']:
            case=stage/name;case.mkdir();prefix=case/'prefix';prefix.mkdir();(prefix/'folder').mkdir();(prefix/'other').mkdir();(prefix/'data').write_bytes(b'original complete payload\n');(prefix/'alias').symlink_to('data');(prefix/'folder/link').symlink_to('../other',target_is_directory=True);(prefix/'folder/through').symlink_to('link/../data');outside=case/'unresolved-target';(prefix/'dangling').symlink_to(str(outside));nodes=manager_tree(prefix)
            if name=='changed-bytes':(prefix/'data').write_bytes(b'changed complete payload!\n')
            elif name=='missing-file':(prefix/'data').unlink()
            elif name=='unlisted-file':(prefix/'unexpected').write_bytes(b'extra')
            elif name=='changed-mode':(prefix/'data').chmod(0o700)
            elif name=='changed-link':(prefix/'alias').unlink();(prefix/'alias').symlink_to('folder')
            elif name=='resolved-dangling-target':outside.write_bytes(b'newly appeared target')
            elif name=='file-to-fifo':(prefix/'data').unlink();os.mkfifo(prefix/'data')
            elif name=='malformed-node':nodes['data']['unqualified-field']=True
            elif name=='malformed-file-pin':nodes['data']['pin']['unqualified-field']=True
            request=out/(name+'.json');write(request,dict(prefix=str(prefix),nodes=nodes));run(name,'provider-tree',request,name=='unchanged')
        for name in ['false-analysis-admission','wrong-version','unlisted-metadata','changed-metadata','resealed-wrong-provider-file-pin']:
            copy=out/name;shutil.copytree(adoption,copy);marker=copy/'ADOPTED.json';declared=json.loads(marker.read_bytes())
            if name=='false-analysis-admission':declared['user_analysis_admitted']=True
            elif name=='wrong-version':declared['components']['conda']['version']='conda 0.0.0'
            elif name=='unlisted-metadata':(copy/'unexpected').write_text('extra')
            elif name=='changed-metadata':(copy/'WARNINGS.md').write_text('changed warning')
            else:
                manifest=copy/'conda-files.json';tree=json.loads(manifest.read_bytes());tree['nodes']['LICENSE.txt']['pin']['sha256']='0'*64;manifest.write_text(json.dumps(tree,indent=2)+'\n');manifest_pin=pin(manifest);declared['components']['conda']['manifest']=manifest_pin;declared['metadata']['conda-files.json']=manifest_pin
                installed=copy/'CONDA-INSTALLED.json';value=json.loads(installed.read_bytes());value['manifest']=manifest_pin;installed.write_text(json.dumps(value,indent=2)+'\n');declared['metadata']['CONDA-INSTALLED.json']=pin(installed)
            marker.write_text(json.dumps(declared,indent=2)+'\n');run(name,'providers',copy,False)
        for name,expected in compiled['sources'].items():assert pin(ROOT/name)==expected,name
        write(out/'COMPLETED.json',dict(schema_version=1,status='PASS_COMPLETE_JAVA_PROVIDER_AND_CONTRADICTION_CONTROLS_NOT_WORKFLOW_ADMISSION',cases=rows,compiled_marker=pin(a.compiled/'COMPILED.json'),native_stage=str(stage),original_provider_adoption_unchanged=pin(adoption/'ADOPTED.json'),user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False))
    except BaseException as error:
        write(out/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),native_stage=str(stage),required_action='Preserve original providers/adoption, every damaged private copy and raw Java diagnostics. Correct the named discrepancy and retry NEW paths; no workflow or catalog admission follows from provider controls.'));raise
if __name__=='__main__':main()
