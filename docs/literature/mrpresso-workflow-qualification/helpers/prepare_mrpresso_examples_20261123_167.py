from pathlib import Path
import json
from handover_inventory_20261122_111 import save
T=Path('D:/projects/JLinAlg/build/fu-1');D=T/'examples/mrpresso-native';D.mkdir()
source=T/'build/mrpresso-1/workflow-original-references-1/windows21/original-default-B'
(D/'associations.tsv').write_bytes((source/'associations.tsv').read_bytes())
request=json.loads((source/'request.json').read_bytes());request['out']='../../build/NEW-mrpresso-native-example';request['inputs']['associations']='associations.tsv'
save(D/'run.json',request);save(D/'replay.json',dict(schema_version=1,mode='REPLAY',analysis='../../build/NEW-mrpresso-native-example',out='../../build/NEW-mrpresso-native-example-replay'))
print('Prepared copyable synthetic RUN and REPLAY examples, bound to the original-default-B witness.')
