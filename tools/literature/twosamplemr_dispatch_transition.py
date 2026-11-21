"""Exact additive TwoSampleMR route/help inverse; no scientific source waiver."""
import hashlib
DISPATCH='src/main/java/org/jlinalg/cli/JLinAlgCli.java'
BASE='0d6abe231b48e93b1dff814dfdf1ba1259343489'
BEFORE={'bytes': 56575, 'sha256': '77ba748364a427fcc2f7e13b8c2a227ada36b168287d2d5f6921d7862d3ad9cd'}
AFTER={'bytes': 56852, 'sha256': '2215625019cca5b0d1c8a1dfcd47295ef4d6cd80c8281f654f36053fd08919ed'}
ROUTE=b'            if (arguments.length > 0 && arguments[0].equals("twosamplemr-tools"))\n                return TwoSampleMrToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'
HELP=b'              java -jar omicsmatic-<version>.jar twosamplemr-tools --help\n'
ANCHOR=b'            if (arguments.length > 0 && arguments[0].equals("peer-tools"))\n'
HELP_ANCHOR=b'              java -jar omicsmatic-<version>.jar peer-tools --help\n'

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(ok,message):
    if not ok:raise ValueError(message)
def dispatcher_before(after):
    require(after.count(ROUTE)==after.count(HELP)==1,'Missing/duplicate TwoSampleMR global route/help')
    before=after.replace(ROUTE,b'',1).replace(HELP,b'',1)
    require(pin(before)==BEFORE and pin(after)==AFTER,'Dispatcher differs beyond the exact TwoSampleMR addition')
    require(before.count(ANCHOR)==before.count(HELP_ANCHOR)==1,'Ambiguous preceding PEER anchors')
    require(after==before.replace(ANCHOR,ROUTE+ANCHOR,1).replace(HELP_ANCHOR,HELP+HELP_ANCHOR,1),'TwoSampleMR route/help moved outside reviewed anchors')
    return before
