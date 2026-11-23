from pathlib import Path
import json,shutil,zipfile
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-1';Q=T/'docs/literature/smartpca-native-development'
roots=[B/'saved-reader-controls-1'/name for name in ('false-count','wrong-source-identity')]
roots += [B/'final-producer-check-3'/host/'example-classes' for host in ('windows21','windows25','linux21')]
index=json.loads((Q/'EVIDENCE.json').read_bytes())['files'];entries=[]
with zipfile.ZipFile(Q/'reader-control-recovery.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as recovery:
 for root in roots:
  rows=[]
  for path in sorted(root.rglob('*')):
   if not path.is_file():continue
   relative=path.relative_to(root).as_posix();identity=pin(path)
   if root.name=='example-classes':
    member=path.relative_to(B).as_posix();assert index[member]==identity
    row=dict(path=relative,identity=identity,recovery='evidence.zip',member=member)
   elif path.name in ('EVIDENCE.json','evidence.zip'):
    assert pin(Q/path.name)==identity;row=dict(path=relative,identity=identity,recovery='published-source',source='docs/literature/smartpca-native-development/'+path.name)
   else:
    member=root.name+'/'+relative;recovery.write(path,member);row=dict(path=relative,identity=identity,recovery='reader-control-recovery.zip',member=member)
   rows.append(row)
  entries.append(dict(root=str(root),files=rows))
save(Q/'CLEANUP-PLAN.json',dict(status='EXACT_REDUNDANT_READER_AND_EXAMPLE_COPIES_AFTER_VERIFIED_PUBLICATION_ONLY',roots=entries,
 retained_altered_archive_root=str(B/'saved-reader-controls-1/altered-archive'),scientific_failures_or_runtime_roots_eligible=False,
 recovery_archive=pin(Q/'reader-control-recovery.zip')))
state=json.loads((Q/'STATUS.json').read_bytes())
for name in ('reader-control-recovery.zip','CLEANUP-PLAN.json'):state['source_code']['docs/literature/smartpca-native-development/'+name]=pin(Q/name)
save(Q/'STATUS.json',state)
for path in (R/'build/check_smartpca_saved_reader_20261123_209.py',Path(__file__)):shutil.copyfile(path,Q/'helpers'/path.name)
readme=Q/'README.md';readme.write_text(readme.read_text(encoding='utf-8').replace('Only verified\nredundant example compiler copies with complete retained recovery may be cleaned.',
 'Only verified\nredundant reader-packet and example compiler copies with complete retained recovery\nmay be cleaned after byte-exact publication.'),encoding='utf-8',newline='\n')
print('Prepared exact five-root cleanup plan with public recovery; corrupted-archive context, scientific evidence and runtimes retained.')
