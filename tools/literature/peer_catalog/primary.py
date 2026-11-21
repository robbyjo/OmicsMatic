"""Reuse the qualified all-250 source identities and eighteen PEER roles."""
import peer_primary as previous
ROOT=previous.ROOT
require=previous.require
pin=previous.pin
encoded=previous.encoded
validate=previous.validate
frozen=previous.frozen

def audit(cache):
    result=previous.audit(cache)
    require(result==frozen(),'Fresh primary witnesses differ from the qualified PEER crosswalk')
    return result
