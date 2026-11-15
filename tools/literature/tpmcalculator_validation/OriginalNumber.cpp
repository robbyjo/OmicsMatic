// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Actual original comparison runtime's default ostream precision6, including ties.
#include <iostream>
#include <iomanip>
#include <cstring>
#include <cstdint>
#include <random>
#include <cmath>
#include <limits>
void emit(std::uint64_t bits) {
    double value;std::memcpy(&value,&bits,sizeof(value));
    std::cout<<std::hex<<std::setw(16)<<std::setfill('0')<<bits<<std::dec<<'\t'<<value<<'\n';
}
void value(double x) {std::uint64_t bits;std::memcpy(&bits,&x,sizeof(x));emit(bits);}
int main() {
    for(double x:{0.0,-0.0,1.0,999999.0,1000000.0,1000001.0,1234567.0,2147483647.0,4294967295.0,
        0.00009999995,0.000009999995,9.999995,999999.5,1e-300,1e300}) {
        value(x);value(std::nextafter(x,-std::numeric_limits<double>::infinity()));value(std::nextafter(x,std::numeric_limits<double>::infinity()));
    }
    emit(0x7ff0000000000000ULL);emit(0xfff0000000000000ULL);emit(0x7ff8000000000000ULL);emit(0xfff8000000000000ULL);
    std::mt19937_64 random(181537);for(int i=0;i<50000;i++)emit(random());
}
