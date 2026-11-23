/* Project-owned caller of unchanged EIG 7.2.1 ridoutlier.
 * Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Original source and BSD notice remain separately retained.
 */
#include "qpsubs.h"
#include "eigsubs.h"
#include "smartsubs.h"

int main(void) {
    int n, axes, mode;
    double threshold;
    if (scanf("%d %d %d %lf", &n, &axes, &mode, &threshold) != 4 ||
            n < 1 || n > 10000 || axes < 0 || axes > 64 || mode < 0 || mode > 2)
        return 2;
    double *vectors = calloc((size_t)n * (size_t)(axes + 1), sizeof(double));
    int *bad = calloc((size_t)n, sizeof(int));
    OUTLINFO **info = calloc((size_t)n, sizeof(OUTLINFO *));
    if (!vectors || !bad || !info) return 2;
    for (int i = 0; i < n; i++) {
        info[i] = calloc(1, sizeof(OUTLINFO));
        if (!info[i]) return 2;
        for (int axis = 0; axis < axes; axis++)
            if (scanf("%lf", &vectors[axis * n + i]) != 1) return 2;
    }
    setoutliermode(mode);
    int count = ridoutlier(vectors, n, axes, threshold, bad, info);
    printf("{\"count\":%d,\"hits\":[", count);
    for (int j = 0; j < count; j++) {
        int sample = bad[j];
        if (j) printf(",");
        printf("{\"sample\":%d,\"zero_based_axis\":%d,\"score\":",sample,info[sample]->vecno);
        double value = info[sample]->score;
        if (isnan(value)) printf("{\"kind\":\"NONFINITE\",\"literal\":\"NaN\"}");
        else if (isinf(value)) printf("{\"kind\":\"NONFINITE\",\"literal\":\"%sInf\"}",value < 0 ? "-" : "");
        else printf("{\"kind\":\"FINITE\",\"value\":%.17g}",value);
        printf("}");
    }
    printf("]}\n");
    for (int i=0;i<n;i++) free(info[i]);
    free(info); free(bad); free(vectors);
    return 0;
}
