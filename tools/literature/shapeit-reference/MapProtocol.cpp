// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Calls original map parsing/interpolation functions, not a copied formula.
#define _DECLARE_TOOLBOX_HERE
#include <containers/variant_map.h>
#include <fstream>
#include <cstring>
#include <iomanip>
#include <cstdint>

static void bits(std::ostream& output, double value) {
    uint64_t wire; std::memcpy(&wire, &value, sizeof(wire));
    output << std::hex << std::setw(16) << std::setfill('0') << wire << std::dec;
}
int main(int argc, char** argv) {
    if (argc != 5 || std::ifstream(argv[4]).good()) return 2;
    std::string mode(argv[1]);
    gmap_reader reader;
    variant_map absolute, relative;
    std::ifstream positions(argv[3]); std::ofstream output(argv[4]);
    if (!positions.good() || !output.good()) return 3;
    if (mode != "default") {
        reader.readGeneticMapFile(argv[2]);
        for (size_t i = 0; i < reader.pos_bp.size(); i++) {
            output << "K\t" << i << '\t' << reader.pos_bp[i] << '\t'; bits(output, reader.pos_cm[i]); output << '\n';
        }
        output.flush();
        if (mode == "read") { output << "E\t" << reader.pos_bp.size() << '\n'; return 0; }
    }
    int position, index = 0;
    while (positions >> position) {
        std::string chr("20"), id = std::to_string(index), ref("A"), alt("C");
        absolute.push(new variant(chr, position, id, ref, alt, index));
        relative.push(new variant(chr, position, id, ref, alt, index)); index++;
    }
    if (!positions.eof()) return 4;
    int direct = 0, interpolated = 0;
    if (mode == "default") relative.setGeneticMap();
    else {
        direct = absolute.setCentiMorgan(reader.pos_bp, reader.pos_cm);
        interpolated = absolute.interpolateCentiMorgan(reader.pos_bp, reader.pos_cm);
        relative.setGeneticMap(reader);
    }
    output << "C\t" << direct << '\t' << interpolated << '\n';
    for (int i = 0; i < index; i++) {
        output << "V\t" << i << '\t' << relative.vec_pos[i]->bp << '\t';
        if (mode == "default") output << '-'; else bits(output, absolute.vec_pos[i]->cm);
        output << '\t'; bits(output, relative.vec_pos[i]->cm); output << '\n';
    }
    output << "E\t" << index << '\n'; output.flush();
    return output.good() ? 0 : 5;
}
