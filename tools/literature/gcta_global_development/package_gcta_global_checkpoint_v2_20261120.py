from pathlib import Path
import ast,json
R=Path('D:/projects/JLinAlg');T=R/'build/gc-7';B=T/'build/gcta-global-1';O=B/'reader-binding-first-1'
for source,target in [(T/'docs/literature/gcta-global-development',O/'initial-public-output'),(T/'tools/literature/gcta_global_development',O/'initial-helper-snapshots')]:
    assert source.resolve().is_relative_to(T.resolve()) and target.resolve().is_relative_to(T.resolve()) and not target.exists();source.rename(target)
(O/'CORRECTION.json').write_text(json.dumps(dict(status='UNPUBLISHED_READER_BINDING_STRENGTHENED',cause='Initial reader checked source-role/census structure but omitted their explicit profile file-pin checks',action='Add both pin checks and build a separately preserved new checkpoint profile',scientific_sources_or_results_changed=False,old_reader_and_metadata_retained=True),indent=2)+'\n',encoding='utf-8',newline='\n')
source=(R/'build/package_gcta_global_checkpoint_20261120.py').read_text(encoding='utf-8')
old="'package_gcta_global_checkpoint_20261120.py','intake_gcta_census_20261120.py'";assert old in source;source=source.replace(old,"'package_gcta_global_checkpoint_20261120.py','package_gcta_global_checkpoint_v2_20261120.py','intake_gcta_census_20261120.py'")
old='inventory={};blobs={}'
assert source.count(old)==1;source=source.replace(old,"for p in (B/'reader-binding-first-1').iterdir():\n    if p.is_file():add(p,'current/reader-binding-first-1/'+p.name)\n"+old)
ast.parse(source);exec(compile(source,__file__,'exec'))
