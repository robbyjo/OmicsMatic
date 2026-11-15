# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Owned caches for derived verification renders; original runtime rules stay intact."""
from pathlib import Path
from xml.sax.saxutils import escape
import hashlib
from Runtime import environment

FONTCONFIG_REFERENCE='https://fontconfig.pages.freedesktop.org/fontconfig/fontconfig-user.html'

def verification_environment(ready,out):
    out=Path(out).resolve();prefix=Path(ready['r_prefix']).resolve()
    env=environment(ready,out)
    identifier=hashlib.sha256(str(prefix).encode()).hexdigest()[:16]
    cache=out/'font-cache'/identifier;cache.mkdir(parents=True,exist_ok=True)
    original=prefix/'etc/fonts/fonts.conf'
    if not original.is_file():raise OSError('Selected verification runtime lacks its original Fontconfig configuration')
    config=out/('fontconfig-'+identifier+'.conf')
    # Fontconfig writes to the first accessible cachedir. Preserve its complete
    # original font matching configuration after the owned, preferred cache.
    raw=('<?xml version="1.0"?>\n<!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd">\n'
      '<fontconfig>\n<cachedir>'+escape(str(cache))+'</cachedir>\n'
      '<include ignore_missing="no">'+escape(str(original))+'</include>\n</fontconfig>\n').encode()
    if config.exists():
        if config.read_bytes()!=raw:raise ValueError('Owned verification Fontconfig declaration changed')
    else:
        with config.open('xb') as stream:stream.write(raw)
    env.update(FONTCONFIG_FILE=str(config),FONTCONFIG_PATH=str(original.parent),
      XDG_CACHE_HOME=str(out/'xdg-cache'))
    return env
