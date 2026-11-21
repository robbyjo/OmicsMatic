from pathlib import Path
import hashlib,json,os,shutil,subprocess
R=Path('D:/projects/JLinAlg');O=R/'build/twosamplemr-historical-runtime-20261121-1';original=O/'versions/0.4.9/upstream';row=O/'versions/0.4.9-documentation-only-attempt-3';assert not row.exists();row.mkdir();source=row/'upstream-no-man';lib=row/'library';lib.mkdir()
def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
assert original.resolve().is_relative_to(O.resolve()) and source.resolve().is_relative_to(row.resolve())
omitted={p.relative_to(original).as_posix():pin(p) for p in (original/'man').rglob('*') if p.is_file()}
shutil.copytree(original,source,ignore=lambda folder,names: ['man'] if Path(folder).resolve()==original.resolve() else [])
retained={p.relative_to(original).as_posix():pin(p) for p in original.rglob('*') if p.is_file() and p.relative_to(original).parts[0]!='man'}
assert all(pin(source/name)==wanted for name,wanted in retained.items()) and not (source/'man').exists()
(row/'DOCUMENTATION-ONLY-BUILD.json').write_text(json.dumps(dict(reason='Original Rd parser fails on documentation encoding under R4.6.1, including --no-help because package indices parse Rd. This private build omits only man/; all other upstream bytes, including every R file, NAMESPACE, DESCRIPTION, data and inst files, are identical. It is a documented documentation-only build, not an unmodified full package archive.',upstream_source_modified=True,scientific_code_namespace_and_data_modified=False,omitted_man_files=omitted,retained_files=retained,first_two_failures_retained=True),indent=2)+'\n',encoding='utf-8',newline='\n')
command=['C:/Program Files/R/R-4.6.1/bin/R.exe','CMD','INSTALL','--no-multiarch','--no-help','--no-html','--library='+str(lib),str(source)]
environment=dict(os.environ,LANG='C',LC_ALL='C',R_LIBS=os.pathsep.join([str(O/'library'),str(R/'build/twosamplemr-runtime-intake-20261121-1/library')]),R_LIBS_USER=str(lib),R_PROFILE_USER=str(row/'no-user-profile'),R_ENVIRON_USER=str(row/'no-user-environ'))
(row/'COMMAND.json').write_text(json.dumps(dict(command=command,build_variant='private-documentation-only-no-man',scientific_code_unchanged=True),indent=2)+'\n',encoding='utf-8',newline='\n')
with (row/'stdout').open('xb') as stdout,(row/'stderr').open('xb') as stderr:
    p=subprocess.Popen(command,cwd=row,env=environment,stdout=stdout,stderr=stderr,creationflags=subprocess.CREATE_NO_WINDOW);(row/'PROCESS.json').write_text(json.dumps(dict(pid=p.pid))+'\n');print('Started documentation-only original-code0.4.9 install',p.pid,flush=True);code=p.wait()
(row/'TERMINAL.json').write_text(json.dumps(dict(exit_code=code,installed=(lib/'TwoSampleMR/DESCRIPTION').exists(),upstream_package_modified_only_by_documentation_omission=True,scientific_code_unchanged=True),indent=2)+'\n',encoding='utf-8',newline='\n');print('Documentation-only build exit',code,flush=True)
