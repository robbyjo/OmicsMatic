"""Reuse the unchanged qualified 250-paper and linked-code source review."""
import json
import limma_primary as previous

ROOT=previous.ROOT
require=previous.require
pin=previous.pin
encoded=previous.encoded
validate=previous.validate

def frozen():
    result=json.loads((ROOT/previous.FOLDER/'SOURCE-CENSUS.json').read_bytes())
    validate(result)
    require(result['author_code_cache_verified'],'The qualified source review must include the linked-code cache')
    return result

def audit(cache,code_cache):
    require(code_cache is not None,'A fresh source audit also requires --author-code-cache')
    result=previous.audit(cache,code_cache)
    require(result==frozen(),'Fresh primary/code witnesses differ from the qualified crosswalk')
    return result
