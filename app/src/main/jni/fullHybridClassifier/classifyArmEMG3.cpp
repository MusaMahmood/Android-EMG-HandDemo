//
// Academic License - for use in teaching, academic research, and meeting
// course requirements at degree granting institutions only.  Not for
// government, commercial, or other organizational use.
// File: classifyArmEMG3.cpp
//
// MATLAB Coder version            : 3.3
// C/C++ source code generated on  : 13-Sep-2017 10:45:48
//

// Include Files
#include "rt_nonfinite.h"
#include "classifyArmEMG3.h"
#include "cstdlib"

// Function Declarations
static void b_filter(const double b[4], const double a[4], const double x[768],
                     const double zi[3], double y[768]);

static void b_filtfilt(const double x_in[750], double y_out[750]);

static void b_flipud(double x[768]);

static void b_power(const double a[4464], double y[4464]);

static void count_nonfinites(const double b_data[], int *nMInf, int *nFinite,
                             int *nPInf, int *nNaN);

static double eps(double x);

static void filter(const double b[7], const double a[7], const double x[786],
                   const double zi[6], double y[786]);

static void filtfilt(const double x_in[750], double y_out[750]);

static void flipud(double x[786]);

static void hist(const double Y[3], const double X_data[], const int X_size[1],
                 double no_data[], int no_size[2]);

static void histc(const double X[3], const double edges_data[], const int
edges_size[2], double N_data[], int N_size[1]);

static double knn(const double tsX[9], const double tX[4464], const double tY
[496]);

static double mean(const double x_data[], const int x_size[1]);

static void merge(int idx[496], double x[496], int offset, int np, int nq, int
iwork[496], double xwork[496]);

static void merge_block(int idx[496], double x[496], int offset, int n, int
preSortLevel, int iwork[496], double xwork[496]);

static void merge_pow2_block(int idx[496], double x[496], int offset);

static void power(const double a[259], double y[259]);

static double rms(const double x[250]);

static void sig_rms_pad_fixed(const double b_signal[250], double y[250]);

static void sort(double x[496], int idx[496]);

static void sortIdx(const double x[496], int idx[496]);

static void sum(const double x[4464], double y[496]);

static double trapz(const double x[250]);

// Function Definitions

//
// Arguments    : const double b[4]
//                const double a[4]
//                const double x[768]
//                const double zi[3]
//                double y[768]
// Return Type  : void
//
static void b_filter(const double b[4], const double a[4], const double x[768],
                     const double zi[3], double y[768]) {
    int k;
    int naxpy;
    int j;
    double as;
    for (k = 0; k < 3; k++) {
        y[k] = zi[k];
    }

    memset(&y[3], 0, 765U * sizeof(double));
    for (k = 0; k < 768; k++) {
        naxpy = 768 - k;
        if (!(naxpy < 4)) {
            naxpy = 4;
        }

        for (j = 0; j + 1 <= naxpy; j++) {
            y[k + j] += x[k] * b[j];
        }

        naxpy = 767 - k;
        if (!(naxpy < 3)) {
            naxpy = 3;
        }

        as = -y[k];
        for (j = 1; j <= naxpy; j++) {
            y[k + j] += as * a[j];
        }
    }
}

//
// Arguments    : const double x_in[750]
//                double y_out[750]
// Return Type  : void
//
static void b_filtfilt(const double x_in[750], double y_out[750]) {
    double d2;
    double d3;
    int i;
    double y[768];
    double b_y[768];
    double a[3];
    static const double b_a[3] = {-0.95097188792826548, 1.9019437758560462,
                                  -0.95097188792780118};

    static const double dv3[4] = {0.950971887923409, -2.85291566377023,
                                  2.85291566377023, -0.950971887923409};

    static const double dv4[4] = {1.0, -2.89947959461186, 2.803947977383,
                                  -0.904347531392409};

    d2 = 2.0 * x_in[0];
    d3 = 2.0 * x_in[749];
    for (i = 0; i < 9; i++) {
        y[i] = d2 - x_in[9 - i];
    }

    memcpy(&y[9], &x_in[0], 750U * sizeof(double));
    for (i = 0; i < 9; i++) {
        y[i + 759] = d3 - x_in[748 - i];
    }

    for (i = 0; i < 3; i++) {
        a[i] = b_a[i] * y[0];
    }

    memcpy(&b_y[0], &y[0], 768U * sizeof(double));
    b_filter(dv3, dv4, b_y, a, y);
    b_flipud(y);
    for (i = 0; i < 3; i++) {
        a[i] = b_a[i] * y[0];
    }

    memcpy(&b_y[0], &y[0], 768U * sizeof(double));
    b_filter(dv3, dv4, b_y, a, y);
    b_flipud(y);
    memcpy(&y_out[0], &y[9], 750U * sizeof(double));
}

//
// Arguments    : double x[768]
// Return Type  : void
//
static void b_flipud(double x[768]) {
    int i;
    double xtmp;
    for (i = 0; i < 384; i++) {
        xtmp = x[i];
        x[i] = x[767 - i];
        x[767 - i] = xtmp;
    }
}

//
// Arguments    : const double a[4464]
//                double y[4464]
// Return Type  : void
//
static void b_power(const double a[4464], double y[4464]) {
    int k;
    for (k = 0; k < 4464; k++) {
        y[k] = a[k] * a[k];
    }
}

//
// Arguments    : const double b_data[]
//                int *nMInf
//                int *nFinite
//                int *nPInf
//                int *nNaN
// Return Type  : void
//
static void count_nonfinites(const double b_data[], int *nMInf, int *nFinite,
                             int *nPInf, int *nNaN) {
    int k;
    k = 0;
    while ((k + 1 <= 496) && rtIsInf(b_data[k]) && (b_data[k] < 0.0)) {
        k++;
    }

    *nMInf = k;
    k = 496;
    while ((k >= 1) && rtIsNaN(b_data[k - 1])) {
        k--;
    }

    *nNaN = 496 - k;
    while ((k >= 1) && rtIsInf(b_data[k - 1]) && (b_data[k - 1] > 0.0)) {
        k--;
    }

    *nPInf = 496 - (k + *nNaN);
    *nFinite = k - *nMInf;
}

//
// Arguments    : double x
// Return Type  : double
//
static double eps(double x) {
    double r;
    double absxk;
    int exponent;
//    absxk = std::abs(x);
    absxk = fabs(x);
    if ((!rtIsInf(absxk)) && (!rtIsNaN(absxk))) {
        if (absxk <= 2.2250738585072014E-308) {
            r = 4.94065645841247E-324;
        } else {
            frexp(absxk, &exponent);
            r = std::ldexp(1.0, exponent - 53);
        }
    } else {
        r = rtNaN;
    }

    return r;
}

//
// Arguments    : const double b[7]
//                const double a[7]
//                const double x[786]
//                const double zi[6]
//                double y[786]
// Return Type  : void
//
static void filter(const double b[7], const double a[7], const double x[786],
                   const double zi[6], double y[786]) {
    int k;
    int naxpy;
    int j;
    double as;
    for (k = 0; k < 6; k++) {
        y[k] = zi[k];
    }

    memset(&y[6], 0, 780U * sizeof(double));
    for (k = 0; k < 786; k++) {
        naxpy = 786 - k;
        if (!(naxpy < 7)) {
            naxpy = 7;
        }

        for (j = 0; j + 1 <= naxpy; j++) {
            y[k + j] += x[k] * b[j];
        }

        naxpy = 785 - k;
        if (!(naxpy < 6)) {
            naxpy = 6;
        }

        as = -y[k];
        for (j = 1; j <= naxpy; j++) {
            y[k + j] += as * a[j];
        }
    }
}

//
// Arguments    : const double x_in[750]
//                double y_out[750]
// Return Type  : void
//
static void filtfilt(const double x_in[750], double y_out[750]) {
    double d0;
    double d1;
    int i;
    double y[786];
    double b_y[786];
    double a[6];
    static const double b_a[6] = {0.22275347859979613, 0.16989850397289177,
                                  0.33991371041886664, 0.34619414482388972, 0.12656228167104569,
                                  0.17313682189292717};

    static const double dv1[7] = {0.777246521400202, -0.295149620198606,
                                  2.36909935327861, -0.591875563889248, 2.36909935327861,
                                  -0.295149620198606,
                                  0.777246521400202};

    static const double dv2[7] = {1.0, -0.348004594825511, 2.53911455972459,
                                  -0.585595129484226, 2.14946749012577, -0.248575079976725,
                                  0.604109699507276
    };

    d0 = 2.0 * x_in[0];
    d1 = 2.0 * x_in[749];
    for (i = 0; i < 18; i++) {
        y[i] = d0 - x_in[18 - i];
    }

    memcpy(&y[18], &x_in[0], 750U * sizeof(double));
    for (i = 0; i < 18; i++) {
        y[i + 768] = d1 - x_in[748 - i];
    }

    for (i = 0; i < 6; i++) {
        a[i] = b_a[i] * y[0];
    }

    memcpy(&b_y[0], &y[0], 786U * sizeof(double));
    filter(dv1, dv2, b_y, a, y);
    flipud(y);
    for (i = 0; i < 6; i++) {
        a[i] = b_a[i] * y[0];
    }

    memcpy(&b_y[0], &y[0], 786U * sizeof(double));
    filter(dv1, dv2, b_y, a, y);
    flipud(y);
    memcpy(&y_out[0], &y[18], 750U * sizeof(double));
}

//
// Arguments    : double x[786]
// Return Type  : void
//
static void flipud(double x[786]) {
    int i;
    double xtmp;
    for (i = 0; i < 393; i++) {
        xtmp = x[i];
        x[i] = x[785 - i];
        x[785 - i] = xtmp;
    }
}

//
// Arguments    : const double Y[3]
//                const double X_data[]
//                const int X_size[1]
//                double no_data[]
//                int no_size[2]
// Return Type  : void
//
static void hist(const double Y[3], const double X_data[], const int X_size[1],
                 double no_data[], int no_size[2]) {
    int edges_size[2];
    int k;
    double edges_data[497];
    double nn_data[497];
    int nn_size[1];
    edges_size[0] = 1;
    edges_size[1] = (short) (X_size[0] + 1);
    for (k = 0; k <= X_size[0] - 2; k++) {
        edges_data[k + 1] = X_data[k] + (X_data[k + 1] - X_data[k]) / 2.0;
    }

    edges_data[0] = rtMinusInf;
    edges_data[(short) (X_size[0] + 1) - 1] = rtInf;
    for (k = 1; k - 1 <= X_size[0] - 2; k++) {
        edges_data[k] += eps(edges_data[k]);
    }

    histc(Y, edges_data, edges_size, nn_data, nn_size);
    no_size[0] = 1;
    no_size[1] = nn_size[0] - 1;
    for (k = 0; k <= nn_size[0] - 2; k++) {
        no_data[k] = nn_data[k];
    }

    if (nn_size[0] - 1 > 0) {
        no_data[nn_size[0] - 2] += nn_data[nn_size[0] - 1];
    }
}

//
// Arguments    : const double X[3]
//                const double edges_data[]
//                const int edges_size[2]
//                double N_data[]
//                int N_size[1]
// Return Type  : void
//
static void histc(const double X[3], const double edges_data[], const int
edges_size[2], double N_data[], int N_size[1]) {
    int xind;
    int k;
    int low_i;
    int low_ip1;
    int high_i;
    int mid_i;
    N_size[0] = (short) edges_size[1];
    xind = (short) edges_size[1];
    for (k = 0; k < xind; k++) {
        N_data[k] = 0.0;
    }

    xind = 0;
    for (k = 0; k < 3; k++) {
        low_i = 0;
        if (!rtIsNaN(X[xind])) {
            if ((X[xind] >= edges_data[0]) && (X[xind] < edges_data[edges_size[1] - 1])) {
                low_i = 1;
                low_ip1 = 2;
                high_i = edges_size[1];
                while (high_i > low_ip1) {
                    mid_i = (low_i >> 1) + (high_i >> 1);
                    if (((low_i & 1) == 1) && ((high_i & 1) == 1)) {
                        mid_i++;
                    }

                    if (X[xind] >= edges_data[mid_i - 1]) {
                        low_i = mid_i;
                        low_ip1 = mid_i + 1;
                    } else {
                        high_i = mid_i;
                    }
                }
            }

            if (X[xind] == edges_data[edges_size[1] - 1]) {
                low_i = edges_size[1];
            }
        }

        if (low_i > 0) {
            N_data[low_i - 1]++;
        }

        xind++;
    }
}

//
// function yfit = knnclassification(testsamplesX,samplesX, samplesY, Knn, type)
//  Classify using the Nearest neighbor algorithm
//  Inputs:
//   tX    - Train samples
//  tY    - Train labels
//    tsX (testsamplesX) - Test  samples to classify
//  Knn         - Number of nearest neighbors
//
//  Outputs
//  result - Predicted targets
// if nargin < 5
//     type = '2norm';
// end
// Arguments    : const double tsX[9]
//                const double tX[4464]
//                const double tY[496]
// Return Type  : double
//
static double knn(const double tsX[9], const double tX[4464], const double tY[496]) {
    int idx[496];
    int k;
    double Uc_data[496];
    int khi;
    int n;
    int nNaN;
    int nb;
    double x;
    int exitg1;
    boolean_T p;
    int i2;
    int Uc_size[1];
    double b_tX[4464];
    double dv5[4464];
    double n_data[496];
    double b_tY[3];
    int n_size[2];
    boolean_T exitg2;
    sortIdx(tY, idx);
    for (k = 0; k < 496; k++) {
        Uc_data[k] = tY[idx[k] - 1];
    }

    count_nonfinites(Uc_data, &k, &khi, &n, &nNaN);
    nb = -1;
    if (k > 0) {
        nb = 0;
    }

    khi += k;
    while (k + 1 <= khi) {
        x = Uc_data[k];
        do {
            exitg1 = 0;
            k++;
            if (k + 1 > khi) {
                exitg1 = 1;
            } else {
//                if ((std::abs(x - Uc_data[k]) < eps(x / 2.0)) || (rtIsInf(Uc_data[k]) && rtIsInf(x) && ((Uc_data[k] > 0.0) == (x > 0.0)))) {
                if ((fabs(x - Uc_data[k]) < eps(x / 2.0)) ||
                    (rtIsInf(Uc_data[k]) && rtIsInf(x) && ((Uc_data[k] > 0.0) == (x > 0.0)))) {
                    p = true;
                } else {
                    p = false;
                }

                if (!p) {
                    exitg1 = 1;
                }
            }
        } while (exitg1 == 0);

        nb++;
        Uc_data[nb] = x;
    }

    if (n > 0) {
        nb++;
        Uc_data[nb] = Uc_data[khi];
    }

    k = khi + n;
    for (khi = 1; khi <= nNaN; khi++) {
        nb++;
        Uc_data[nb] = Uc_data[(k + khi) - 1];
    }

    if (1 > nb + 1) {
        i2 = -1;
    } else {
        i2 = nb;
    }

    Uc_size[0] = i2 + 1;
    for (khi = 0; khi < 496; khi++) {
        for (n = 0; n < 9; n++) {
            b_tX[khi + 496 * n] = tX[khi + 496 * n] - tsX[n];
        }
    }

    b_power(b_tX, dv5);
    sum(dv5, n_data);
    sort(n_data, idx);
    for (khi = 0; khi < 3; khi++) {
        b_tY[khi] = tY[idx[khi] - 1];
    }

    hist(b_tY, Uc_data, Uc_size, n_data, n_size);
    khi = 1;
    n = n_size[1];
    x = n_data[0];
    k = 0;
    if (n_size[1] > 1) {
        if (rtIsNaN(n_data[0])) {
            nNaN = 2;
            exitg2 = false;
            while ((!exitg2) && (nNaN <= n)) {
                khi = nNaN;
                if (!rtIsNaN(n_data[nNaN - 1])) {
                    x = n_data[nNaN - 1];
                    k = nNaN - 1;
                    exitg2 = true;
                } else {
                    nNaN++;
                }
            }
        }

        if (khi < n_size[1]) {
            while (khi + 1 <= n) {
                if (n_data[khi] > x) {
                    x = n_data[khi];
                    k = khi;
                }

                khi++;
            }
        }
    }

    return Uc_data[k];
}

//
// Arguments    : const double x_data[]
//                const int x_size[1]
// Return Type  : double
//
static double mean(const double x_data[], const int x_size[1]) {
    double y;
    int k;
    if (x_size[0] == 0) {
        y = 0.0;
    } else {
        y = x_data[0];
        for (k = 2; k <= x_size[0]; k++) {
            y += x_data[k - 1];
        }
    }

    y /= (double) x_size[0];
    return y;
}

//
// Arguments    : int idx[496]
//                double x[496]
//                int offset
//                int np
//                int nq
//                int iwork[496]
//                double xwork[496]
// Return Type  : void
//
static void merge(int idx[496], double x[496], int offset, int np, int nq, int
iwork[496], double xwork[496]) {
    int n;
    int qend;
    int p;
    int iout;
    int exitg1;
    if (nq != 0) {
        n = np + nq;
        for (qend = 0; qend + 1 <= n; qend++) {
            iwork[qend] = idx[offset + qend];
            xwork[qend] = x[offset + qend];
        }

        p = 0;
        n = np;
        qend = np + nq;
        iout = offset - 1;
        do {
            exitg1 = 0;
            iout++;
            if (xwork[p] <= xwork[n]) {
                idx[iout] = iwork[p];
                x[iout] = xwork[p];
                if (p + 1 < np) {
                    p++;
                } else {
                    exitg1 = 1;
                }
            } else {
                idx[iout] = iwork[n];
                x[iout] = xwork[n];
                if (n + 1 < qend) {
                    n++;
                } else {
                    n = (iout - p) + 1;
                    while (p + 1 <= np) {
                        idx[n + p] = iwork[p];
                        x[n + p] = xwork[p];
                        p++;
                    }

                    exitg1 = 1;
                }
            }
        } while (exitg1 == 0);
    }
}

//
// Arguments    : int idx[496]
//                double x[496]
//                int offset
//                int n
//                int preSortLevel
//                int iwork[496]
//                double xwork[496]
// Return Type  : void
//
static void merge_block(int idx[496], double x[496], int offset, int n, int
preSortLevel, int iwork[496], double xwork[496]) {
    int nPairs;
    int bLen;
    int tailOffset;
    int nTail;
    nPairs = n >> preSortLevel;
    bLen = 1 << preSortLevel;
    while (nPairs > 1) {
        if ((nPairs & 1) != 0) {
            nPairs--;
            tailOffset = bLen * nPairs;
            nTail = n - tailOffset;
            if (nTail > bLen) {
                merge(idx, x, offset + tailOffset, bLen, nTail - bLen, iwork, xwork);
            }
        }

        tailOffset = bLen << 1;
        nPairs >>= 1;
        for (nTail = 1; nTail <= nPairs; nTail++) {
            merge(idx, x, offset + (nTail - 1) * tailOffset, bLen, bLen, iwork, xwork);
        }

        bLen = tailOffset;
    }

    if (n > bLen) {
        merge(idx, x, offset, bLen, n - bLen, iwork, xwork);
    }
}

//
// Arguments    : int idx[496]
//                double x[496]
//                int offset
// Return Type  : void
//
static void merge_pow2_block(int idx[496], double x[496], int offset) {
    int b;
    int bLen;
    int bLen2;
    int nPairs;
    int k;
    int blockOffset;
    int q;
    int p;
    int iwork[256];
    double xwork[256];
    int exitg1;
    for (b = 0; b < 6; b++) {
        bLen = 1 << (b + 2);
        bLen2 = bLen << 1;
        nPairs = 256 >> (b + 3);
        for (k = 1; k <= nPairs; k++) {
            blockOffset = (offset + (k - 1) * bLen2) - 1;
            for (q = 1; q <= bLen2; q++) {
                iwork[q - 1] = idx[blockOffset + q];
                xwork[q - 1] = x[blockOffset + q];
            }

            p = 0;
            q = bLen;
            do {
                exitg1 = 0;
                blockOffset++;
                if (xwork[p] <= xwork[q]) {
                    idx[blockOffset] = iwork[p];
                    x[blockOffset] = xwork[p];
                    if (p + 1 < bLen) {
                        p++;
                    } else {
                        exitg1 = 1;
                    }
                } else {
                    idx[blockOffset] = iwork[q];
                    x[blockOffset] = xwork[q];
                    if (q + 1 < bLen2) {
                        q++;
                    } else {
                        q = blockOffset - p;
                        while (p + 1 <= bLen) {
                            idx[(q + p) + 1] = iwork[p];
                            x[(q + p) + 1] = xwork[p];
                            p++;
                        }

                        exitg1 = 1;
                    }
                }
            } while (exitg1 == 0);
        }
    }
}

//
// Arguments    : const double a[259]
//                double y[259]
// Return Type  : void
//
static void power(const double a[259], double y[259]) {
    int k;
    for (k = 0; k < 259; k++) {
        y[k] = a[k] * a[k];
    }
}

//
// Arguments    : const double x[250]
// Return Type  : double
//
static double rms(const double x[250]) {
    double y;
    int i;
    double b_x[250];
    for (i = 0; i < 250; i++) {
        b_x[i] = x[i] * x[i];
    }

    y = b_x[0];
    for (i = 0; i < 249; i++) {
        y += b_x[i + 1];
    }

    return std::sqrt(y / 250.0);
}

//
// Arguments    : const double b_signal[250]
//                double y[250]
// Return Type  : void
//
static void sig_rms_pad_fixed(const double b_signal[250], double y[250]) {
    int i;
    double c_signal[259];
    double S[259];
    int b_index;
    static const unsigned char uv0[250] = {1U, 2U, 3U, 4U, 5U, 6U, 7U, 8U, 9U,
                                           10U, 11U, 12U, 13U, 14U, 15U, 16U, 17U, 18U, 19U, 20U,
                                           21U, 22U, 23U, 24U,
                                           25U, 26U, 27U, 28U, 29U, 30U, 31U, 32U, 33U, 34U, 35U,
                                           36U, 37U, 38U, 39U,
                                           40U, 41U, 42U, 43U, 44U, 45U, 46U, 47U, 48U, 49U, 50U,
                                           51U, 52U, 53U, 54U,
                                           55U, 56U, 57U, 58U, 59U, 60U, 61U, 62U, 63U, 64U, 65U,
                                           66U, 67U, 68U, 69U,
                                           70U, 71U, 72U, 73U, 74U, 75U, 76U, 77U, 78U, 79U, 80U,
                                           81U, 82U, 83U, 84U,
                                           85U, 86U, 87U, 88U, 89U, 90U, 91U, 92U, 93U, 94U, 95U,
                                           96U, 97U, 98U, 99U,
                                           100U, 101U, 102U, 103U, 104U, 105U, 106U, 107U, 108U,
                                           109U, 110U, 111U, 112U,
                                           113U, 114U, 115U, 116U, 117U, 118U, 119U, 120U, 121U,
                                           122U, 123U, 124U, 125U,
                                           126U, 127U, 128U, 129U, 130U, 131U, 132U, 133U, 134U,
                                           135U, 136U, 137U, 138U,
                                           139U, 140U, 141U, 142U, 143U, 144U, 145U, 146U, 147U,
                                           148U, 149U, 150U, 151U,
                                           152U, 153U, 154U, 155U, 156U, 157U, 158U, 159U, 160U,
                                           161U, 162U, 163U, 164U,
                                           165U, 166U, 167U, 168U, 169U, 170U, 171U, 172U, 173U,
                                           174U, 175U, 176U, 177U,
                                           178U, 179U, 180U, 181U, 182U, 183U, 184U, 185U, 186U,
                                           187U, 188U, 189U, 190U,
                                           191U, 192U, 193U, 194U, 195U, 196U, 197U, 198U, 199U,
                                           200U, 201U, 202U, 203U,
                                           204U, 205U, 206U, 207U, 208U, 209U, 210U, 211U, 212U,
                                           213U, 214U, 215U, 216U,
                                           217U, 218U, 219U, 220U, 221U, 222U, 223U, 224U, 225U,
                                           226U, 227U, 228U, 229U,
                                           230U, 231U, 232U, 233U, 234U, 235U, 236U, 237U, 238U,
                                           239U, 240U, 241U, 242U,
                                           243U, 244U, 245U, 246U, 247U, 248U, 249U, 250U};

    int i0;
    int i1;
    int S_size[1];
    int loop_ub;
    double x;

    //  CALCULATE RMS
    //  Zeropad signal
    //  Square the samples
    for (i = 0; i < 250; i++) {
        y[i] = 0.0;
        c_signal[i] = b_signal[i];
    }

    memset(&c_signal[250], 0, 9U * sizeof(double));
    power(c_signal, S);
    b_index = -1;
    for (i = 0; i < 250; i++) {
        b_index++;

        //  Average and take the square root of each window
        if (uv0[i] > uv0[i] + 9) {
            i0 = 0;
            i1 = 0;
        } else {
            i0 = i;
            i1 = i + 10;
        }

        S_size[0] = i1 - i0;
        loop_ub = i1 - i0;
        for (i1 = 0; i1 < loop_ub; i1++) {
            c_signal[i1] = S[i0 + i1];
        }

        x = mean(c_signal, S_size);
        x = std::sqrt(x);
        y[b_index] = x;
    }
}

//
// Arguments    : double x[496]
//                int idx[496]
// Return Type  : void
//
static void sort(double x[496], int idx[496]) {
    int i;
    double xwork[496];
    double x4[4];
    int nNaNs;
    short idx4[4];
    int ib;
    int k;
    signed char perm[4];
    int iwork[496];
    int i2;
    int i3;
    int i4;
    memset(&idx[0], 0, 496U * sizeof(int));
    for (i = 0; i < 4; i++) {
        x4[i] = 0.0;
        idx4[i] = 0;
    }

    memset(&xwork[0], 0, 496U * sizeof(double));
    nNaNs = -495;
    ib = 0;
    for (k = 0; k < 496; k++) {
        if (rtIsNaN(x[k])) {
            idx[-nNaNs] = k + 1;
            xwork[-nNaNs] = x[k];
            nNaNs++;
        } else {
            ib++;
            idx4[ib - 1] = (short) (k + 1);
            x4[ib - 1] = x[k];
            if (ib == 4) {
                i = (k - nNaNs) - 498;
                if (x4[0] <= x4[1]) {
                    ib = 1;
                    i2 = 2;
                } else {
                    ib = 2;
                    i2 = 1;
                }

                if (x4[2] <= x4[3]) {
                    i3 = 3;
                    i4 = 4;
                } else {
                    i3 = 4;
                    i4 = 3;
                }

                if (x4[ib - 1] <= x4[i3 - 1]) {
                    if (x4[i2 - 1] <= x4[i3 - 1]) {
                        perm[0] = (signed char) ib;
                        perm[1] = (signed char) i2;
                        perm[2] = (signed char) i3;
                        perm[3] = (signed char) i4;
                    } else if (x4[i2 - 1] <= x4[i4 - 1]) {
                        perm[0] = (signed char) ib;
                        perm[1] = (signed char) i3;
                        perm[2] = (signed char) i2;
                        perm[3] = (signed char) i4;
                    } else {
                        perm[0] = (signed char) ib;
                        perm[1] = (signed char) i3;
                        perm[2] = (signed char) i4;
                        perm[3] = (signed char) i2;
                    }
                } else if (x4[ib - 1] <= x4[i4 - 1]) {
                    if (x4[i2 - 1] <= x4[i4 - 1]) {
                        perm[0] = (signed char) i3;
                        perm[1] = (signed char) ib;
                        perm[2] = (signed char) i2;
                        perm[3] = (signed char) i4;
                    } else {
                        perm[0] = (signed char) i3;
                        perm[1] = (signed char) ib;
                        perm[2] = (signed char) i4;
                        perm[3] = (signed char) i2;
                    }
                } else {
                    perm[0] = (signed char) i3;
                    perm[1] = (signed char) i4;
                    perm[2] = (signed char) ib;
                    perm[3] = (signed char) i2;
                }

                idx[i] = idx4[perm[0] - 1];
                idx[i + 1] = idx4[perm[1] - 1];
                idx[i + 2] = idx4[perm[2] - 1];
                idx[i + 3] = idx4[perm[3] - 1];
                x[i] = x4[perm[0] - 1];
                x[i + 1] = x4[perm[1] - 1];
                x[i + 2] = x4[perm[2] - 1];
                x[i + 3] = x4[perm[3] - 1];
                ib = 0;
            }
        }
    }

    if (ib > 0) {
        for (i = 0; i < 4; i++) {
            perm[i] = 0;
        }

        if (ib == 1) {
            perm[0] = 1;
        } else if (ib == 2) {
            if (x4[0] <= x4[1]) {
                perm[0] = 1;
                perm[1] = 2;
            } else {
                perm[0] = 2;
                perm[1] = 1;
            }
        } else if (x4[0] <= x4[1]) {
            if (x4[1] <= x4[2]) {
                perm[0] = 1;
                perm[1] = 2;
                perm[2] = 3;
            } else if (x4[0] <= x4[2]) {
                perm[0] = 1;
                perm[1] = 3;
                perm[2] = 2;
            } else {
                perm[0] = 3;
                perm[1] = 1;
                perm[2] = 2;
            }
        } else if (x4[0] <= x4[2]) {
            perm[0] = 2;
            perm[1] = 1;
            perm[2] = 3;
        } else if (x4[1] <= x4[2]) {
            perm[0] = 2;
            perm[1] = 3;
            perm[2] = 1;
        } else {
            perm[0] = 3;
            perm[1] = 2;
            perm[2] = 1;
        }

        for (k = 1; k <= ib; k++) {
            idx[(k - nNaNs) - ib] = idx4[perm[k - 1] - 1];
            x[(k - nNaNs) - ib] = x4[perm[k - 1] - 1];
        }
    }

    i = (nNaNs + 495) >> 1;
    for (k = 1; k <= i; k++) {
        ib = idx[k - nNaNs];
        idx[k - nNaNs] = idx[496 - k];
        idx[496 - k] = ib;
        x[k - nNaNs] = xwork[496 - k];
        x[496 - k] = xwork[k - nNaNs];
    }

    if (((nNaNs + 495) & 1) != 0) {
        x[(i - nNaNs) + 1] = xwork[(i - nNaNs) + 1];
    }

    memset(&iwork[0], 0, 496U * sizeof(int));
    i = 2;
    if (1 - nNaNs > 1) {
        ib = (1 - nNaNs) >> 8;
        if (ib > 0) {
            for (i = 1; i <= ib; i++) {
                merge_pow2_block(idx, x, (i - 1) << 8);
            }

            i = ib << 8;
            ib = 1 - (nNaNs + i);
            if (ib > 0) {
                merge_block(idx, x, i, ib, 2, iwork, xwork);
            }

            i = 8;
        }

        merge_block(idx, x, 0, 1 - nNaNs, i, iwork, xwork);
    }
}

//
// Arguments    : const double x[496]
//                int idx[496]
// Return Type  : void
//
static void sortIdx(const double x[496], int idx[496]) {
    int k;
    int i;
    boolean_T p;
    int i2;
    int j;
    int pEnd;
    int b_p;
    int q;
    int qEnd;
    int kEnd;
    int iwork[496];
    for (k = 0; k <= 495; k += 2) {
        if ((x[k] <= x[k + 1]) || rtIsNaN(x[k + 1])) {
            p = true;
        } else {
            p = false;
        }

        if (p) {
            idx[k] = k + 1;
            idx[k + 1] = k + 2;
        } else {
            idx[k] = k + 2;
            idx[k + 1] = k + 1;
        }
    }

    i = 2;
    while (i < 496) {
        i2 = i << 1;
        j = 1;
        for (pEnd = 1 + i; pEnd < 497; pEnd = qEnd + i) {
            b_p = j;
            q = pEnd - 1;
            qEnd = j + i2;
            if (qEnd > 497) {
                qEnd = 497;
            }

            k = 0;
            kEnd = qEnd - j;
            while (k + 1 <= kEnd) {
                if ((x[idx[b_p - 1] - 1] <= x[idx[q] - 1]) || rtIsNaN(x[idx[q] - 1])) {
                    p = true;
                } else {
                    p = false;
                }

                if (p) {
                    iwork[k] = idx[b_p - 1];
                    b_p++;
                    if (b_p == pEnd) {
                        while (q + 1 < qEnd) {
                            k++;
                            iwork[k] = idx[q];
                            q++;
                        }
                    }
                } else {
                    iwork[k] = idx[q];
                    q++;
                    if (q + 1 == qEnd) {
                        while (b_p < pEnd) {
                            k++;
                            iwork[k] = idx[b_p - 1];
                            b_p++;
                        }
                    }
                }

                k++;
            }

            for (k = 0; k + 1 <= kEnd; k++) {
                idx[(j + k) - 1] = iwork[k];
            }

            j = qEnd;
        }

        i = i2;
    }
}

//
// Arguments    : const double x[4464]
//                double y[496]
// Return Type  : void
//
static void sum(const double x[4464], double y[496]) {
    int j;
    double s;
    int k;
    for (j = 0; j < 496; j++) {
        s = x[j];
        for (k = 0; k < 8; k++) {
            s += x[j + (k + 1) * 496];
        }

        y[j] = s;
    }
}

//
// Arguments    : const double x[250]
// Return Type  : double
//
static double trapz(const double x[250]) {
    double z;
    int iy;
    double ylast;
    int k;
    z = 0.0;
    iy = 0;
    ylast = x[0];
    for (k = 0; k < 249; k++) {
        iy++;
        z += (ylast + x[iy]) / 2.0;
        ylast = x[iy];
    }

    return z;
}

//
// , RMS, COMBMAX, sigRMSIntegral
// Arguments    : const double dW[2250]
//                const double F[4960]
// Return Type  : double
//
double classifyArmEMG3(const double dW[2250], const double F[4960]) {
    int i;
    double dWF0[2250];
    double b_dWF0[750];
    double RMS[9];
    int ix;
    int ixstart;
    double mtmp;
    double dWF[750];
    int b_ix;
    double dv0[250];
    boolean_T exitg1;
    double MAX[3];
    double sigRMSIntegral[3];
    double sigRMS[250];
    double b_sigRMS[750];
    double b_RMS[3];

    // classifyArmEMG
    //  Wn = [55. 65]*2/Fs;
    //  [b,a] = butter(3, Wn, 'stop');
    //  Wn2 = (2)*2/Fs; %high pass:
    //  [b1,a1] = butter(3, Wn2, 'high');
    //  2Hz High Pass:
    //  LAST 1s / 6s
    for (i = 0; i < 3; i++) {
        filtfilt(*(double (*)[750]) &dW[750 * i], *(double (*)[750]) &dWF0[750 * i]);
        memcpy(&b_dWF0[0], &dWF0[i * 750], 750U * sizeof(double));
        b_filtfilt(b_dWF0, *(double (*)[750]) &dWF0[750 * i]);

        //      dWF(:,i) = dWF0(end-249:end,i);
        memcpy(&dWF[i * 250], &dWF0[i * 750 + 375], 250U * sizeof(double));

        //  Feature Extraction
        sig_rms_pad_fixed(*(double (*)[250]) &dWF[250 * i], dv0);
        for (ix = 0; ix < 250; ix++) {
            b_sigRMS[i + 3 * ix] = dv0[ix];
            sigRMS[ix] = b_sigRMS[i + 3 * ix];
        }

        sigRMSIntegral[i] = trapz(sigRMS);
        b_RMS[i] = rms(*(double (*)[250]) &dWF[250 * i]);
    }

    for (i = 0; i < 3; i++) {
        ix = i * 250;
        ixstart = i * 250 + 1;
        mtmp = dWF[ix];
        if (rtIsNaN(dWF[ix])) {
            b_ix = ixstart + 1;
            exitg1 = false;
            while ((!exitg1) && (b_ix <= ix + 250)) {
                ixstart = b_ix;
                if (!rtIsNaN(dWF[b_ix - 1])) {
                    mtmp = dWF[b_ix - 1];
                    exitg1 = true;
                } else {
                    b_ix++;
                }
            }
        }

        if (ixstart < ix + 250) {
            while (ixstart + 1 <= ix + 250) {
                if (dWF[ixstart] > mtmp) {
                    mtmp = dWF[ixstart];
                }

                ixstart++;
            }
        }

        MAX[i] = mtmp;
        RMS[i] = b_RMS[i];
        RMS[i + 3] = sigRMSIntegral[i];
        RMS[i + 6] = MAX[i];
    }

    return knn(RMS, *(double (*)[4464]) &F[0], *(double (*)[496]) &F[4464]);
}

//
// Arguments    : void
// Return Type  : void
//
void classifyArmEMG3_initialize() {
    rt_InitInfAndNaN(8U);
}

//
// Arguments    : void
// Return Type  : void
//
void classifyArmEMG3_terminate() {
    // (no terminate code required)
}

//
// File trailer for classifyArmEMG3.cpp
//
// [EOF]
//
