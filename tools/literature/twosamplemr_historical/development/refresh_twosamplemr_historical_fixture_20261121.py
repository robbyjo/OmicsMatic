from pathlib import Path
import hashlib,json,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/mr-5';B=T/'build/twosamplemr-historical-1';O=B/'fixture-2';O.mkdir();source=B/'java-pilot-2';result=json.loads((source/'RESULT.json').read_bytes());assert result['all_pass'];target=(T/'src/test/resources/org/jlinalg/cli/twosamplemr-historical.zip').resolve();previous=(B/'fixture-1/original44.zip').resolve();assert target.is_relative_to(T.resolve()) and previous.is_relative_to(B.resolve()) and not previous.exists();target.rename(previous)
captures={key:source/key for key,row in result['results'].items() if row['exit_code']==0};captures['0.4.9/multivariable-latin']=B/'column-order-control-3/analysis';assert len(captures)==45;assert json.loads((B/'column-order-control-3/RESULT.json').read_bytes())['exit_code']==0
pins={}
with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for key,root in sorted(captures.items()):
        for path in sorted(root.rglob('*')):
            if not path.is_file():continue
            name=key+'/'+path.relative_to(root).as_posix();raw=path.read_bytes();pins[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());info=zipfile.ZipInfo(name,(2026,11,21,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,raw)
(O/'INVENTORY.json').write_text(json.dumps(pins,indent=2)+'\n',encoding='utf-8',newline='\n');(O/'PROFILE.json').write_text(json.dumps(dict(scope='Original tagged-package R execution captures via quick-compiled Java; not normal-JAR qualification',cases=sorted(captures),files=len(pins),archive_bytes=target.stat().st_size,archive_sha256=hashlib.sha256(target.read_bytes()).hexdigest(),non_ascii_R_order_preserved=True,previous_fixture_preserved=str(previous),new_catalog_credit=0,whole_goal_complete=False),indent=2)+'\n',encoding='utf-8',newline='\n');print('Saved',len(captures),'genuine captures,',len(pins),'files',target.stat().st_size,'bytes')
