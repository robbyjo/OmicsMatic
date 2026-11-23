from pathlib import Path
import shutil
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-1';folder=B/'freeze-first-attempt';folder.mkdir()
for path in (R/'build/freeze_smartpca_component_20261123_207.py',T/'tools/literature/audit_smartpca_sources.py'):shutil.copyfile(path,folder/path.name)
import json
windows=B/'source-roles-2/ROLES.json';linux=B/'source-roles-linux-1/ROLES.json';assert json.loads(windows.read_bytes())==json.loads(linux.read_bytes())
save(folder/'RESULT.json',dict(status='RETAINED_FREEZE_NEWLINE_ASSERTION_BEFORE_ARCHIVE_CREATION',
 windows=pin(windows),linux=pin(linux),parsed_roles_and_identities_equal=True,
 cause='Windows default text newline writes CRLF; Linux writes LF. Recheck both hosts with an explicitly LF-serialized source audit.',
 scientific_source_or_primary_bytes_changed=False,public_archive_created=False))
path=R/'build/freeze_smartpca_component_20261123_207.py';text=path.read_text().replace("source-roles-2/ROLES.json","source-roles-3/ROLES.json").replace("source-roles-linux-1/ROLES.json","source-roles-linux-2/ROLES.json")
text=text.replace("'source-roles-linux-1'):","'source-roles-linux-1','source-roles-3','source-roles-linux-2','freeze-first-attempt'):")
path.write_text(text,encoding='utf-8')
print('Preserved byte-distinct but semantically identical source-audit receipts before explicit LF serialization.')
