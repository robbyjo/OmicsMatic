from pathlib import Path
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');B=R/'build/fu-1/build/smartpca-1';old=R/'build/check_smartpca_final_producer_20261123_204.py'
folder=B/'final-producer-check-2';(folder/'runner-source.py').write_bytes(old.read_bytes())
save(folder/'RESULT.json',dict(status='RETAINED_FAILED_DISASSEMBLY_CLASSIFIER_BEFORE_FINAL_PRODUCER_TESTS',
 cause='The direct compilation directory also contains SmartpcaPreparedTest; test classes are intentionally absent from the application JAR. The filter must select production classes only.',
 runner_source=pin(old),scientific_source_modified=False,analysis_admitted=False))
raw=old.read_text().replace("out=B/'final-producer-check-2'","out=B/'final-producer-check-3'")
needle="for path in sorted((direct/'org/jlinalg/genetics').glob('SmartpcaPrepared*.class')):\n"
assert raw.count(needle)==1
raw=raw.replace(needle,needle+" if path.stem=='SmartpcaPreparedTest':continue\n")
target=R/'build/check_smartpca_final_producer_20261123_205.py';assert not target.exists();target.write_text(raw,encoding='utf-8')
print('Preserved the classifier failure; production-class filter is now explicit.')
