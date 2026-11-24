# Original method attribution

The native Java scoring method follows selected hard-call arithmetic in
[PRSice-2](https://github.com/choishingwan/PRSice), copyright Shing Wan Choi
and Paul F. O'Reilly, licensed GPL-3.0-or-later. The selected tag is 2.3.5,
commit `9dbf55e3f7f3ede2c1b1b6a334c676163e0cbe34`.

Relevant original files are `src/binaryplink.cpp`, `inc/genotype.hpp`,
`inc/misc.hpp`, `src/genotype.cpp` and `inc/prsice.hpp`. Their exact identities
are retained in `source-profile.json`. The full upstream license is available
at the pinned commit's `LICENSE`; its SHA-256 is
`589ed823e9a84c56feb95ac58e7cf384626b9cbf4fda2a907bc36e103de1bad2`.

The project's GPL license covers the Java implementation and verification code.
Source and binary validation captures remain separate. No source modification
to the original PRSice program and no runtime binary redistribution is part of
this component.
