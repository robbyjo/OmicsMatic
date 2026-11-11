/* Original-function reference driver; GPL-3.0-or-later.
 * Compile against an unchanged pinned RSEM source tree. */
#include "WriteResults.h"
#include <iomanip>
#include <iostream>
#include <sstream>

bool verbose = false;

struct ExplicitLengths {
    int minimum;
    std::vector<double> mass;
    void copyTo(double*& pdf, double*& cdf, int& lower, int& upper, int& span) {
        lower = minimum - 1; span = mass.size(); upper = lower + span;
        pdf = new double[span + 1]; cdf = new double[span + 1];
        pdf[0] = cdf[0] = 0;
        for (int i = 1; i <= span; ++i) {pdf[i] = mass[i - 1]; cdf[i] = cdf[i - 1] + pdf[i];}
    }
};
struct ExplicitModel {
    ExplicitLengths lengths;
    ExplicitLengths& getGLD() { return lengths; }
};
void values(const std::vector<double>& data, int begin) {
    for (int i = begin; i < int(data.size()); ++i) {
        if (i > begin) std::cout << ',';
        std::cout << data[i];
    }
}
int main() {
    std::cout << std::setprecision(17);
    std::string id;
    int size, minimum, span;
    while (std::cin >> id >> size >> minimum >> span) {
        std::vector<int> lengths(size), tails(size);
        for (int i = 0; i < size; ++i) std::cin >> lengths[i];
        for (int i = 0; i < size; ++i) std::cin >> tails[i];
        ExplicitModel model; model.lengths.minimum = minimum; model.lengths.mass.resize(span);
        for (int i = 0; i < span; ++i) std::cin >> model.lengths.mass[i];
        std::vector<double> theta(size + 1), mw(size + 1), effective, tpm, fpkm;
        for (int i = 0; i <= size; ++i) std::cin >> theta[i];
        for (int i = 0; i <= size; ++i) std::cin >> mw[i];
        if (!std::cin) return 2;
        Refs refs; refs.getRefs().push_back(RefSeq());
        for (int i = 0; i < size; ++i) refs.getRefs().push_back(RefSeq("t", std::string(lengths[i], 'C'), tails[i]));
        calcExpectedEffectiveLengths(size, refs, model, effective);
        polishTheta(size, theta, effective, &mw[0]);
        calcExpressionValues(size, theta, effective, tpm, fpkm);
        std::cout << id << '\t'; values(effective, 1); std::cout << '\t'; values(theta, 0);
        std::cout << '\t'; values(tpm, 1); std::cout << '\t'; values(fpkm, 1); std::cout << '\n';
    }
    return 0;
}
