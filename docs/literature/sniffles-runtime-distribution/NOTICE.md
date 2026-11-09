# Original Sniffles 1.0.7 native binary

This is the tested Linux x86_64 binary built from unmodified upstream Sniffles commit 752bd21266125c16f1ea17d1b19c7a76860d4856. It is not an identified paper author's binary. Original Sniffles, BamTools, TCLAP, zlib, Google Test and JsonCpp notices accompany it unchanged in assets.zip. No original scientific source was patched.

The retained reference build selected CMAKE_CXX_STANDARD=11 with GCC 13 and CMake Release; older BamTools does not compile with the compiler's default C++17 comparator requirements. A build in another absolute directory may have different binary bytes. This distribution supplies the exact tested binary rather than silently accepting a new binary hash.

Original source: https://github.com/fritzsedlazeck/Sniffles/tree/752bd21266125c16f1ea17d1b19c7a76860d4856

Complete immutable source archive and preparation/build logs are retained in docs/literature/sniffles-vcf-development/controls.zip. System C/C++ runtime libraries and Python are not redistributed here. This asset preparation grants no SV calling or catalog availability credit.
