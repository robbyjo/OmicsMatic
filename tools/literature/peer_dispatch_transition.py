"""Exact additive PEER route/help inverse; no scientific source/hash waiver."""
import hashlib
DISPATCH='src/main/java/org/jlinalg/cli/JLinAlgCli.java'
BASE='c54d9186f74b92fc28c1250ce4b364771f8c708f'
BEFORE={'bytes': 56316, 'sha256': 'cf2fbc2a265a1fffa9c5eced445934008ca1d4c25398daa6139c0a5fdee4b8fa'}
AFTER={'bytes': 56575, 'sha256': '77ba748364a427fcc2f7e13b8c2a227ada36b168287d2d5f6921d7862d3ad9cd'}
ROUTE=b'            if (arguments.length > 0 && arguments[0].equals("peer-tools"))\n                return PeerWorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'
HELP=b'              java -jar omicsmatic-<version>.jar peer-tools --help\n'
ANCHOR=b'            if (arguments.length > 0 && arguments[0].equals("limma-tools"))\n'
HELP_ANCHOR=b'              java -jar omicsmatic-<version>.jar limma-tools --help\n'

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def require(ok,message):
    if not ok:raise ValueError(message)

def dispatcher_before(after):
    require(after.count(ROUTE)==after.count(HELP)==1,'Missing/duplicate peer global route/help')
    before=after.replace(ROUTE,b'',1).replace(HELP,b'',1)
    require(pin(before)==BEFORE and pin(after)==AFTER,'Dispatcher differs beyond the three peer lines')
    require(before.count(ANCHOR)==before.count(HELP_ANCHOR)==1,'Ambiguous original limma anchors')
    require(after==before.replace(ANCHOR,ROUTE+ANCHOR,1).replace(HELP_ANCHOR,HELP+HELP_ANCHOR,1),'PEER route/help moved outside reviewed anchors')
    return before
