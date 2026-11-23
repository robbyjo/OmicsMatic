from pathlib import Path
import json
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');B=R/'build/fu-1/build/smartpca-1'
old=R/'build/check_smartpca_final_producer_20261123_203.py';raw=old.read_text();folder=B/'final-producer-check-1'
(folder/'runner-source.py').write_bytes(old.read_bytes())
save(folder/'RESULT.json',dict(status='RETAINED_FAILED_CLASSIFIER_BEFORE_ANY_FINAL_PRODUCER_TEST',
 cause='Assumed byte equality between direct-javac and Gradle builds; Gradle adds debug metadata. No final-producer comparison was executed by this attempt.',
 runner_source=pin(old),scientific_source_modified=False,analysis_admitted=False))
raw=raw.replace("out=B/'final-producer-check-1'","out=B/'final-producer-check-2'")
start=raw.index('with zipfile.ZipFile(jar) as archive:');end=raw.index('for host,home in ',start)
replacement='''# Compare instructions while retaining both complete disassemblies; Java debug
# metadata differs across Gradle/direct-javac builds and is not instruction code.
import re
javap='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javap.exe'
direct=B/'component-check-1/windows21/classes'
instruction_checks=[]
for path in sorted((direct/'org/jlinalg/genetics').glob('SmartpcaPrepared*.class')):
 name='org.jlinalg.genetics.'+path.stem;dumps=[]
 for label,cp in [('normal',jar),('direct',direct)]:
  p=subprocess.run([javap,'-c','-p','-constants','-classpath',str(cp),name],capture_output=True,timeout=60)
  assert p.returncode==0,p.stderr
  (out/(path.stem+'.'+label+'.javap')).write_bytes(p.stdout)
  dumps.append(re.sub(rb'#[0-9]+',b'#CP',p.stdout).replace(b'\\r\\n',b'\\n'))
 assert dumps[0]==dumps[1],'Native instructions differ: '+name
 instruction_checks.append(name)
save(out/'INSTRUCTION-CHECKS.json',dict(status='PASS_SAME_INSTRUCTIONS_WITH_DIFFERENT_DEBUG_METADATA',classes=instruction_checks,
 constant_pool_indices_normalized_only=True,binary_equality_claim=False))
'''
raw=raw[:start]+replacement+raw[end:]
target=R/'build/check_smartpca_final_producer_20261123_204.py';assert not target.exists();target.write_text(raw,encoding='utf-8')
print('Preserved first classifier; prepared instruction comparison and actual final-producer tests.')
