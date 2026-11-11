// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Calls the original, unmodified target reader and inspects its actual genotype storage.
#define _DECLARE_TOOLBOX_HERE
#ifdef SHAPEIT_FOUR
#include <io/genotype_reader.h>
#else
#include <io/genotype_reader/genotype_reader_header.h>
#endif
#include <fstream>
#include <htslib/vcf.h>

int main(int argc, char** argv) {
    if (argc != 3 || std::ifstream(argv[2]).good()) return 2;
    haplotype_set H;
    genotype_set G;
    variant_map V;
#ifdef SHAPEIT_FOUR
    genotype_reader reader(H, G, V, "20", false);
    reader.scanGenotypes(argv[1]);
    reader.allocateGenotypes();
    reader.readGenotypes0(argv[1]);
#else
    genotype_reader reader(H, G, V);
    reader.setMainFilename(argv[1]);
    reader.setRegion("20");
    reader.scanGenotypes();
    reader.allocateGenotypes();
    reader.readGenotypes();
#endif
    htsFile* file = bcf_open(argv[1], "r");
    if (!file) return 3;
    bcf_hdr_t* header = bcf_hdr_read(file);
    bcf1_t* record = bcf_init();
    int32_t* calls = nullptr;
    int capacity = 0;
    std::ofstream output(argv[2]);
    if (!header || !record || !output) return 4;
    output << "variant\tsample\twire0\twire1\tpacked\tcref\tcalt\tcmis\n";
    uint64_t variant = 0, cells = 0;
    int status;
    while ((status = bcf_read(file, header, record)) == 0) {
        if (record->n_allele != 2 || variant >= reader.n_variants) return 5;
        int count = bcf_get_genotypes(header, record, &calls, &capacity);
        if (count != 2 * bcf_hdr_nsamples(header)) return 6;
        for (int sample = 0; sample < bcf_hdr_nsamples(header); sample++) {
            if (G.vecG[sample]->name != header->samples[sample]) return 7;
            unsigned state = (G.vecG[sample]->Variants[variant / 2] >> (4 * (variant % 2))) & 15;
            output << variant << '\t' << header->samples[sample] << '\t' << calls[2 * sample] << '\t'
                   << calls[2 * sample + 1] << '\t' << state << '\t' << V.vec_pos[variant]->cref << '\t'
                   << V.vec_pos[variant]->calt << '\t' << V.vec_pos[variant]->cmis << '\n';
            cells++;
        }
        variant++;
    }
    output.flush();
    bool complete = status == -1 && output.good() && variant == reader.n_variants && cells == variant * reader.n_main_samples;
    free(calls);
    bcf_destroy(record);
    bcf_hdr_destroy(header);
    if (bcf_close(file) != 0) complete = false;
    return complete ? 0 : 8;
}
