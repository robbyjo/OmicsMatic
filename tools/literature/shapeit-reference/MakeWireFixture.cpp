// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Synthetic exhaustive biallelic BCF wire cells, including both missing encodings.
#include <htslib/vcf.h>
#include <fstream>
#include <string>
#include <vector>

int main(int argc, char** argv) {
    if (argc != 2 || std::ifstream(argv[1]).good()) return 2;
    htsFile* file = bcf_open(argv[1], "wb");
    bcf_hdr_t* header = bcf_hdr_init("w");
    if (!file || !header) return 3;
    if (bcf_hdr_append(header, "##contig=<ID=20,length=1000000>") < 0
            || bcf_hdr_append(header, "##FORMAT=<ID=GT,Number=1,Type=String,Description=Genotype>") < 0
            || bcf_hdr_append(header, "##INFO=<ID=AC,Number=A,Type=Integer,Description=Observed_ALT_alleles>") < 0
            || bcf_hdr_append(header, "##INFO=<ID=AN,Number=1,Type=Integer,Description=Observed_alleles>") < 0) return 4;
    for (int sample = 0; sample < 50; sample++) {
        std::string name = "S" + std::to_string(1000 + sample);
        if (bcf_hdr_add_sample(header, name.c_str()) < 0) return 5;
    }
    if (bcf_hdr_add_sample(header, nullptr) < 0 || bcf_hdr_write(file, header) < 0) return 6;
    std::vector<int32_t> second = {0, 1, 2, 3, 4, 5, bcf_int32_vector_end};
    for (int variant = 0; variant < 4; variant++) {
        bcf1_t* record = bcf_init();
        if (!record) return 7;
        record->rid = 0;
        record->pos = 999 + 1000 * variant;
        std::string id = "wire" + std::to_string(variant);
        if (bcf_update_alleles_str(header, record, "A,C") < 0 || bcf_update_id(header, record, id.c_str()) < 0) return 8;
        std::vector<int32_t> calls;
        for (int sample = 0; sample < 50; sample++) {
            int combination = (sample + 11 * variant) % 42;
            calls.push_back(combination / 7);
            calls.push_back(second[combination % 7]);
        }
        int32_t ac = 0, an = 0;
        for (int32_t value : calls) if (value >= 2 && value <= 5) { an++; ac += ((value >> 1) - 1 == 1); }
        if (bcf_update_info_int32(header, record, "AC", &ac, 1) < 0 || bcf_update_info_int32(header, record, "AN", &an, 1) < 0
                || bcf_update_genotypes(header, record, calls.data(), calls.size()) < 0 || bcf_write(file, header, record) < 0) return 9;
        bcf_destroy(record);
    }
    bcf_hdr_destroy(header);
    return bcf_close(file) == 0 ? 0 : 10;
}
