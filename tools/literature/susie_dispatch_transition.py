"""Exact additive SuSiE route inverse; never overlay a historical source lookup.

The only accepted delta is the reviewed route/help insertion below. Scientific
programs, native routing and previous owner/reader sources stay byte-for-byte.
"""
import hashlib

DISPATCH='src/main/java/org/jlinalg/cli/JLinAlgCli.java'
BEFORE=dict(bytes=54720,sha256='9e44cc6837834e4dece3b68bc41451536cb0c12db1b7476c47b2a14569389cce')
ROUTE=(b'            if (arguments.length > 0 && arguments[0].equals("susie-tools"))\n'
       b'                return SusieWorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n')
HELP=b'              java -jar omicsmatic-<version>.jar susie-tools --help\n'
ANCHOR=b'            if (arguments.length > 0 && arguments[0].equals("scanpy-tools"))\n'
HELP_ANCHOR=b'              java -jar omicsmatic-<version>.jar scanpy-tools --help\n'


def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def require(value,message):
    if not value:raise ValueError(message)


def dispatcher_before(after):
    require(after.count(ROUTE)==after.count(HELP)==1,'Missing or duplicate SuSiE route/help')
    before=after.replace(ROUTE,b'',1).replace(HELP,b'',1)
    require(pin(before)==BEFORE,'Dispatcher differs beyond the exact three additive lines')
    require(before.count(ANCHOR)==before.count(HELP_ANCHOR)==1,'Ambiguous original Scanpy anchors')
    require(after==before.replace(ANCHOR,ROUTE+ANCHOR,1).replace(HELP_ANCHOR,HELP+HELP_ANCHOR,1),
            'SuSiE route/help inserted outside the reviewed anchors')
    return before
