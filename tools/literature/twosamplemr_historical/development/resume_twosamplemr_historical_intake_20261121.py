from pathlib import Path
R=Path('D:/projects/JLinAlg');original=R/'build/intake_twosamplemr_historical_20261121.py';O=R/'build/twosamplemr-historical-intake-20261121-1'
(O/'ATTEMPT-1.py').write_bytes(original.read_bytes());(O/'ATTEMPT-1-FAILURE.txt').write_text('Source intake stopped before downloading 0.5.6 because both prefixed and unprefixed tags exist. Both peel to 8db843c08084ee79476a8804735cc7e40d88d8b7; retain both aliases and continue without overwriting 0.4.9.\n',encoding='utf-8')
s=original.read_text(encoding='utf-8');s=s.replace(";assert not O.exists();O.mkdir()","")
start=s.index("command=['git'");end=s.index('refs=dict',start);s=s[:start]+"tags=(O/'tags.stdout').read_bytes()\n"+s[end:];s=s.replace('p.stdout.decode()','tags.decode()').replace('pin(p.stdout)','pin(tags)')
s=s.replace('directory=O/version;directory.mkdir();candidates=',"directory=O/version;directory.mkdir(exist_ok=True)\n    if (directory/'RESULT.json').exists():\n        prior=json.loads((directory/'RESULT.json').read_bytes());assert prior['version']==version and prior['status']=='PINNED_EXACT_OFFICIAL_RELEASE_TAG_SOURCE_ONLY';results[version]=prior;continue\n    assert not any(directory.iterdir()),'Inspect partial unrecorded source before retry'\n    candidates=")
s=s.replace('assert len(candidates)==1,candidates',"assert len({refs.get('refs/tags/'+name+'^{}',refs['refs/tags/'+name]) for name in candidates})==1,('Different commits share the reported version; audit all variants before choosing',candidates)")
s=s.replace("tag_object=refs[ref],commit=sha,", "tag_object=refs[ref],tag_aliases=candidates,commit=sha,")
target=R/'build/intake_twosamplemr_historical_complete_20261121.py';target.write_text(s,encoding='utf-8',newline='\n');print(target)
