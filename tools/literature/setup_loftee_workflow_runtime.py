# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Export a NEW exact compact execution closure from existing permitted original dependencies; never change shared environments."""
import argparse,concurrent.futures,hashlib,json,os,stat,subprocess,sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import unpack


def replace_checked(raw,profile,current_root,target_root=None):
    expected={k:profile[k] for k in ['bytes','sha256']};description=profile.get('prefix_relocation');baseline=raw
    if description:
        old=description['old_prefix'].encode('ascii');now=str(current_root).encode('ascii');assert len(now)<=len(old),'Use a shorter portable runtime prefix'
        if old!=now:
            assert old not in raw,'Partial old-prefix fallback is refused'
            if description['kind']=='exact-text':assert now in raw;baseline=raw.replace(now,old)
            else:
                result=bytearray(raw);previous=-1
                for region in description['regions']:
                    offset,length=region['offset'],region['length'];assert offset>previous and offset+length<len(raw);data=raw[offset:offset+length+1];field=data.split(b'\0',1)[0];assert now in field and not any(data[len(field):]);restored=field.replace(now,old);assert len(restored)==length;result[offset:offset+length+1]=restored+b'\0';previous=offset+length
                baseline=bytes(result)
    assert dict(bytes=len(baseline),sha256=hashlib.sha256(baseline).hexdigest())==expected,'Runtime source differs outside exact allowed prefix substitutions'
    if description and target_root is not None:
        old=description['old_prefix'].encode('ascii');new=str(target_root).encode('ascii');assert len(new)<=len(old),'Use a shorter portable runtime prefix'
        if description['kind']=='exact-text':return baseline.replace(old,new)
        result=bytearray(baseline)
        for region in description['regions']:
            offset,length=region['offset'],region['length'];value=baseline[offset:offset+length].replace(old,new);assert len(value)<=length;result[offset:offset+length+1]=value+b'\0'*(length+1-len(value))
        return bytes(result)
    return baseline


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--environment',required=True,type=Path);p.add_argument('--bioperl',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-prepare',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build') and not out.exists();out.mkdir(parents=True);source=Path(__file__).resolve();(out/'setup-at-attempt.py').write_bytes(source.read_bytes());profile_path=ROOT/'src/main/resources/org/jlinalg/omics/loftee-workflow/SOURCE-PROFILE.json';profile=json.loads(profile_path.read_bytes());state=dict(status='SKIP3_DEPENDENCY_UNAVAILABLE',variant_analysis_executed=False,implementation_credit=False,source_pins={source.relative_to(ROOT).as_posix():pin(source),profile_path.relative_to(ROOT).as_posix():pin(profile_path)},required_action='Where permitted prepare exact original dependencies in a NEW short Linux x86_64 path. This helper exports only the reviewed execution closure from existing dependencies; it installs nothing. Missing/forbidden/different source or unsupported relocation skips dependent analysis. Preserve every preparation error, use a shorter path if named, and do not substitute software versions or repin changed code.')
    if not a.allow_prepare or not sys.platform.startswith('linux'):
        save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state['required_action']+'\n',encoding='utf8');print(state['status']+': '+state['required_action']);return 3
    assert str(out).isascii() and all(c.isalnum() or c in '/._-' for c in str(out));save(out/'STARTED.json',dict(status='RUNNING_EXACT_PRIVATE_RUNTIME_EXPORT',source_pins=state['source_pins']))
    try:
        for name,value in profile['host_files'].items():assert pin(Path(name))==value,('Host dependency differs',name)
        source_roots={'environment':a.environment.resolve(),'bioperl':a.bioperl.resolve()};actual={}
        for group in ['environment','bioperl']:
            target=out/group;target.mkdir();old=Path(profile[group]['baseline_root']);root=source_roots[group];tasks=[];links=[];copied={}
            for name,value in profile[group]['files'].items():
                destination=target/name;assert destination.resolve().is_relative_to(target)
                if value['kind']=='symlink':links.append((name,value));continue
                original=Path(value['copied_host_command']) if 'copied_host_command' in value else root/name;tasks.append((name,original,destination,value))
            def copy(item):
                name,original,destination,value=item;assert original.is_file() and not original.is_symlink(),str(original);before=pin(original);raw=original.read_bytes();assert pin(original)==before and len(raw)==before['bytes'],str(original);
                try:data=replace_checked(raw,value,root,target)
                except Exception as error:raise RuntimeError('Exact runtime export failed for '+group+'/'+name+': '+str(error)) from error
                assert len(data)<=536870912;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(data);os.chmod(destination,value['mode']);replace_checked(destination.read_bytes(),value,target);return name,pin(destination)
            with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
                for name,value in pool.map(copy,tasks):copied[name]=value
            for name,value in links:
                original=root/name;assert original.is_symlink();expected=value['target'].replace(str(old),str(root));assert os.readlink(original)==expected;destination=target/name;destination.parent.mkdir(parents=True,exist_ok=True);destination.symlink_to(value['target'].replace(str(old),str(target)));assert destination.resolve().is_relative_to(target);copied[name]=dict(kind='symlink',target=os.readlink(destination))
            actual[group]=copied
        parent=ROOT/'docs/literature/loftee-audit-development';proof=json.loads((parent/'VALIDATION.json').read_bytes());assert pin(parent/'controls.zip')==proof['archive'];evidence=unpack(parent/'controls.zip',proof)
        for edition,label in [('grch38-1.0.4','loftee-grch38'),('cited-fork-c8fdde00','loftee38-cited-fork')]:
            root=out/'source'/edition;root.mkdir(parents=True)
            for name,value in profile['editions'][edition]['source_files'].items():
                raw=evidence['build/literature-review/loftee-runtime/source-intake-1/'+label+'/'+name];assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==value;target=root/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
        save(out/'runtime-file-identities.json',actual);environment=out/'environment';bioperl=out/'bioperl';prefix=['/usr/bin/env','-i','HOME='+str(out),'PATH='+str(environment/'bin'),'LD_LIBRARY_PATH='+str(environment/'lib'),'LC_ALL=C','PERL5LIB='+str(environment/profile['vep_include'])+':'+str(bioperl)]
        commands={'vep-help':[str(environment/profile['perl_program']),str(environment/profile['vep_program']),'--help'],'samtools-version':[str(environment/'bin/samtools'),'--version'],'bgzip-version':[str(environment/'bin/bgzip'),'--version'],'tabix-version':[str(environment/'bin/tabix'),'--version'],'gzip-version':[str(environment/'bin/gzip'),'--version']}
        for name,arguments in commands.items():
            r=subprocess.run(prefix+arguments,cwd=out,capture_output=True,timeout=120);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=prefix+arguments,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode)
        assert '105.0' in (out/'vep-help.stdout').read_text(encoding='utf8');state.update(status='PASS_EXACT_COMPACT_LOFTEE_RUNTIME_PREPARED_ONLY',environment=str(environment),bioperl=str(bioperl),sources={k:str(out/'source'/k) for k in profile['editions']},runtime_identities=pin(out/'runtime-file-identities.json'),runtime_analysis_installs_software=False);print('Exact compact runtime prepared with checked relocation; health only, no variant analysis')
    except Exception as error:state['error']=repr(error);print(state['status'],repr(error))
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Exact dependency preparation completed; no variant analysis was run.')+'\n\n'+state['required_action']+'\n',encoding='utf8');print('Warnings / required actions: '+str(out/'WARNINGS.md'));return 0 if state['status'].startswith('PASS_') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source/runtime relocation checks')
    raise SystemExit(main())
