"""Exact additive limma global route/help inverse; no scientific source/hash waiver."""
import hashlib
DISPATCH='src/main/java/org/jlinalg/cli/JLinAlgCli.java'
BASE='521b04bee9289b6113a0562d0d8a8366a0f9dbdf'
BEFORE={'bytes': 56054, 'sha256': '8c656d6b1cb0bb17eea925f3d593684ed667ca74bc289682d166d040e3ded72b'}
AFTER={'bytes': 56316, 'sha256': 'cf2fbc2a265a1fffa9c5eced445934008ca1d4c25398daa6139c0a5fdee4b8fa'}
ROUTE=b'            if (arguments.length > 0 && arguments[0].equals("limma-tools"))\n                return LimmaWorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'
HELP=b'              java -jar omicsmatic-<version>.jar limma-tools --help\n'
ANCHOR=b'            if (arguments.length > 0 && arguments[0].equals("edger-tools"))\n'
HELP_ANCHOR=b'              java -jar omicsmatic-<version>.jar edger-tools --help\n'

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def require(ok,message):
    if not ok:raise ValueError(message)

def dispatcher_before(after):
    require(after.count(ROUTE)==after.count(HELP)==1,'Missing/duplicate limma global route/help')
    before=after.replace(ROUTE,b'',1).replace(HELP,b'',1)
    require(pin(before)==BEFORE and pin(after)==AFTER,'Dispatcher differs beyond the three limma lines')
    require(before.count(ANCHOR)==before.count(HELP_ANCHOR)==1,'Ambiguous original edgeR anchors')
    require(after==before.replace(ANCHOR,ROUTE+ANCHOR,1).replace(HELP_ANCHOR,HELP+HELP_ANCHOR,1),'Limma route/help moved outside reviewed anchors')
    return before
