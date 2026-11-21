"""Exact GCTA route/help inverse. No scientific source or historical hash waiver."""
import hashlib
DISPATCH='src/main/java/org/jlinalg/cli/JLinAlgCli.java'
BASE='cfeee186d5068a2af3be33ef9fb69508feefdd17'
BEFORE={'bytes': 55795, 'sha256': '69b3851ccecc66423bd1acf2b9ffcf313973e5ca2c9f7f2cb0a58669e0424d43'}
AFTER={'bytes': 56054, 'sha256': '8c656d6b1cb0bb17eea925f3d593684ed667ca74bc289682d166d040e3ded72b'}
ROUTE=b'            if (arguments.length > 0 && arguments[0].equals("gcta-tools"))\n                return GctaWorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'
HELP=b'              java -jar omicsmatic-<version>.jar gcta-tools --help\n'
ANCHOR=b'            if (arguments.length > 0 && arguments[0].equals("broad-gsea-tools"))\n'
HELP_ANCHOR=b'              java -jar omicsmatic-<version>.jar broad-gsea-tools --help\n'
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(ok,message):
    if not ok:raise ValueError(message)
def dispatcher_before(after):
    require(after.count(ROUTE)==after.count(HELP)==1,'Missing/duplicate GCTA global route/help')
    before=after.replace(ROUTE,b'',1).replace(HELP,b'',1)
    require(pin(before)==BEFORE and pin(after)==AFTER,'Dispatcher differs beyond the three GCTA lines')
    require(before.count(ANCHOR)==before.count(HELP_ANCHOR)==1,'Ambiguous original Broad GSEA anchors')
    require(after==before.replace(ANCHOR,ROUTE+ANCHOR,1).replace(HELP_ANCHOR,HELP+HELP_ANCHOR,1),'GCTA route/help moved outside reviewed anchors')
    return before
