# Upstream sources and reuse

The [ISAC standards page](https://isac-net.org/data-standards/) supplies
Gating-ML 2.0, edition 2013-01-22. The original archive used here has size
2,228,245 bytes and SHA-256
`f3ec0bd75cfecd5faf30ac2bc73104307464405cc09349b8db8da44fccdc7eae`.
Its [official archive URL](https://isac-net.org/wp-content/uploads/2026/03/GatingML_2.0_Specification.20130122.full_.zip)
is recorded with the intake identity. The normative document is CC BY-SA 3.0.
ISAC's XSD/compliance readme permits free distribution and read-only use and
reserves modification/other rights. Original schemas, examples, control data,
expected bitmaps and archive remain unchanged. The bundled schemas are checked
against exact SHA-256 identities and resolved offline.

The original Stanford Java transform implementation is BSD-3-Clause,
copyright 2009, 2011, 2012 The Board of Trustees of The Leland Stanford Junior
University. Its seven classes are relocated to
`org.jlinalg.cytometry.internal.stanford`. Changes are package/line-ending
normalization, a serialization UID, the Hyperlog constructor lint annotation
and project-change comments; numerical algorithms are unchanged. The complete
notice/disclaimer is in `LICENSES/stanford-cytometry-transforms.txt`, source
headers and the executable resource
`org/jlinalg/cytometry/gatingml20/LICENSE-Stanford-transforms.txt`.
Original source and package documentation, including its patent note, remain in
the indexed evidence. Wrapper guards and the explicit linear `A=T` branch are
project code, rather than undisclosed edits to the original algorithms.

[FlowUtils](https://flowutils.readthedocs.io/en/latest/) 1.2.2 is an independent
reference, with its original source, BSD notices and pinned official PyPI
Windows CPython 3.12 wheel retained in the development records. It uses NumPy
2.3.5 for the recorded comparisons. Its C transforms/polygon engine and
NumPy solve/SVD calculations are reference-only dependencies; production
analysis executes native Java and does not call Python. The reference retains
its three polygon-edge differences instead of silently using the native result
as its oracle.

[FlowIO](https://github.com/whitews/FlowIO) 1.4.0, source commit
`9ae65421cbe64d3ff17eeaf81f9dd0fbb9079436`, supplies the separately retained
independent FCS-to-scale preparation. Its source is BSD-3-Clause. Its treatment
of an ambiguous legacy TEXT token differs from the strict native reader;
prepared set-1 values do not establish native raw-file ingestion. Existing
[reader source locks](../../../tools/literature/cytometry/reader-reference-lock.json)
and historical qualifications remain preserved. No third-party runtime or
license is replaced by a project implementation label.
