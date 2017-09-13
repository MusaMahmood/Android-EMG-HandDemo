//
// Academic License - for use in teaching, academic research, and meeting
// course requirements at degree granting institutions only.  Not for
// government, commercial, or other organizational use.
// File: ctrainingRoutineKNN.cpp
//
// MATLAB Coder version            : 3.3
// C/C++ source code generated on  : 13-Sep-2017 12:28:08
//

// Include Files
#include "rt_nonfinite.h"
#include "ctrainingRoutineKNN.h"

// Function Declarations
static void b_filter(const double b[4], const double a[4], const double x[30018],
                     const double zi[3], double y[30018]);
static void b_filtfilt(const double x_in[30000], double y_out[30000]);
static void b_flipud(double x[30018]);
static void c_filtfilt(const double x_in[90000], double y_out[90000]);
static void d_filtfilt(const double x_in[30000], double y_out[30000]);
static void filter(const double b[7], const double a[7], const double x[30036],
                   const double zi[6], double y[30036]);
static void filtfilt(const double x_in[90000], double y_out[90000]);
static void flipud(double x[30036]);
static double mean(const double x_data[], const int x_size[1]);
static void merge(int idx[250], double x[250], int offset, int np, int nq, int
                  iwork[250], double xwork[250]);
static void power(const double a[259], double y[259]);
static double rms(const double x[250]);
static void sig_rms_pad_fixed(const double b_signal[250], double y[250]);
static void sort(double x[250]);
static double trapz(const double x[250]);

// Function Definitions

//
// Arguments    : const double b[4]
//                const double a[4]
//                const double x[30018]
//                const double zi[3]
//                double y[30018]
// Return Type  : void
//
static void b_filter(const double b[4], const double a[4], const double x[30018],
                     const double zi[3], double y[30018])
{
  int k;
  int naxpy;
  int j;
  double as;
  for (k = 0; k < 3; k++) {
    y[k] = zi[k];
  }

  memset(&y[3], 0, 30015U * sizeof(double));
  for (k = 0; k < 30018; k++) {
    naxpy = 30018 - k;
    if (!(naxpy < 4)) {
      naxpy = 4;
    }

    for (j = 0; j + 1 <= naxpy; j++) {
      y[k + j] += x[k] * b[j];
    }

    naxpy = 30017 - k;
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
// Arguments    : const double x_in[30000]
//                double y_out[30000]
// Return Type  : void
//
static void b_filtfilt(const double x_in[30000], double y_out[30000])
{
  double d0;
  double d1;
  int i;
  static double y[30036];
  static double b_y[30036];
  double a[6];
  static const double b_a[6] = { 0.22275347859979613, 0.16989850397289177,
    0.33991371041886664, 0.34619414482388972, 0.12656228167104569,
    0.17313682189292717 };

  static const double dv0[7] = { 0.777246521400202, -0.295149620198606,
    2.36909935327861, -0.591875563889248, 2.36909935327861, -0.295149620198606,
    0.777246521400202 };

  static const double dv1[7] = { 1.0, -0.348004594825511, 2.53911455972459,
    -0.585595129484226, 2.14946749012577, -0.248575079976725, 0.604109699507276
  };

  d0 = 2.0 * x_in[0];
  d1 = 2.0 * x_in[29999];
  for (i = 0; i < 18; i++) {
    y[i] = d0 - x_in[18 - i];
  }

  memcpy(&y[18], &x_in[0], 30000U * sizeof(double));
  for (i = 0; i < 18; i++) {
    y[i + 30018] = d1 - x_in[29998 - i];
  }

  for (i = 0; i < 6; i++) {
    a[i] = b_a[i] * y[0];
  }

  memcpy(&b_y[0], &y[0], 30036U * sizeof(double));
  filter(dv0, dv1, b_y, a, y);
  flipud(y);
  for (i = 0; i < 6; i++) {
    a[i] = b_a[i] * y[0];
  }

  memcpy(&b_y[0], &y[0], 30036U * sizeof(double));
  filter(dv0, dv1, b_y, a, y);
  flipud(y);
  memcpy(&y_out[0], &y[18], 30000U * sizeof(double));
}

//
// Arguments    : double x[30018]
// Return Type  : void
//
static void b_flipud(double x[30018])
{
  int i;
  double xtmp;
  for (i = 0; i < 15009; i++) {
    xtmp = x[i];
    x[i] = x[30017 - i];
    x[30017 - i] = xtmp;
  }
}

//
// Arguments    : const double x_in[90000]
//                double y_out[90000]
// Return Type  : void
//
static void c_filtfilt(const double x_in[90000], double y_out[90000])
{
  int i;
  for (i = 0; i < 3; i++) {
    d_filtfilt(*(double (*)[30000])&x_in[30000 * i], *(double (*)[30000])&y_out
               [30000 * i]);
  }
}

//
// Arguments    : const double x_in[30000]
//                double y_out[30000]
// Return Type  : void
//
static void d_filtfilt(const double x_in[30000], double y_out[30000])
{
  double d2;
  double d3;
  int i;
  static double y[30018];
  static double b_y[30018];
  double a[3];
  static const double b_a[3] = { -0.95097188792826548, 1.9019437758560462,
    -0.95097188792780118 };

  static const double dv2[4] = { 0.950971887923409, -2.85291566377023,
    2.85291566377023, -0.950971887923409 };

  static const double dv3[4] = { 1.0, -2.89947959461186, 2.803947977383,
    -0.904347531392409 };

  d2 = 2.0 * x_in[0];
  d3 = 2.0 * x_in[29999];
  for (i = 0; i < 9; i++) {
    y[i] = d2 - x_in[9 - i];
  }

  memcpy(&y[9], &x_in[0], 30000U * sizeof(double));
  for (i = 0; i < 9; i++) {
    y[i + 30009] = d3 - x_in[29998 - i];
  }

  for (i = 0; i < 3; i++) {
    a[i] = b_a[i] * y[0];
  }

  memcpy(&b_y[0], &y[0], 30018U * sizeof(double));
  b_filter(dv2, dv3, b_y, a, y);
  b_flipud(y);
  for (i = 0; i < 3; i++) {
    a[i] = b_a[i] * y[0];
  }

  memcpy(&b_y[0], &y[0], 30018U * sizeof(double));
  b_filter(dv2, dv3, b_y, a, y);
  b_flipud(y);
  memcpy(&y_out[0], &y[9], 30000U * sizeof(double));
}

//
// Arguments    : const double b[7]
//                const double a[7]
//                const double x[30036]
//                const double zi[6]
//                double y[30036]
// Return Type  : void
//
static void filter(const double b[7], const double a[7], const double x[30036],
                   const double zi[6], double y[30036])
{
  int k;
  int naxpy;
  int j;
  double as;
  for (k = 0; k < 6; k++) {
    y[k] = zi[k];
  }

  memset(&y[6], 0, 30030U * sizeof(double));
  for (k = 0; k < 30036; k++) {
    naxpy = 30036 - k;
    if (!(naxpy < 7)) {
      naxpy = 7;
    }

    for (j = 0; j + 1 <= naxpy; j++) {
      y[k + j] += x[k] * b[j];
    }

    naxpy = 30035 - k;
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
// Arguments    : const double x_in[90000]
//                double y_out[90000]
// Return Type  : void
//
static void filtfilt(const double x_in[90000], double y_out[90000])
{
  int i;
  for (i = 0; i < 3; i++) {
    b_filtfilt(*(double (*)[30000])&x_in[30000 * i], *(double (*)[30000])&y_out
               [30000 * i]);
  }
}

//
// Arguments    : double x[30036]
// Return Type  : void
//
static void flipud(double x[30036])
{
  int i;
  double xtmp;
  for (i = 0; i < 15018; i++) {
    xtmp = x[i];
    x[i] = x[30035 - i];
    x[30035 - i] = xtmp;
  }
}

//
// Arguments    : const double x_data[]
//                const int x_size[1]
// Return Type  : double
//
static double mean(const double x_data[], const int x_size[1])
{
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

  y /= (double)x_size[0];
  return y;
}

//
// Arguments    : int idx[250]
//                double x[250]
//                int offset
//                int np
//                int nq
//                int iwork[250]
//                double xwork[250]
// Return Type  : void
//
static void merge(int idx[250], double x[250], int offset, int np, int nq, int
                  iwork[250], double xwork[250])
{
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
// Arguments    : const double a[259]
//                double y[259]
// Return Type  : void
//
static void power(const double a[259], double y[259])
{
  int k;
  for (k = 0; k < 259; k++) {
    y[k] = a[k] * a[k];
  }
}

//
// Arguments    : const double x[250]
// Return Type  : double
//
static double rms(const double x[250])
{
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
static void sig_rms_pad_fixed(const double b_signal[250], double y[250])
{
  int i;
  double c_signal[259];
  double S[259];
  int b_index;
  static const unsigned char uv0[250] = { 1U, 2U, 3U, 4U, 5U, 6U, 7U, 8U, 9U,
    10U, 11U, 12U, 13U, 14U, 15U, 16U, 17U, 18U, 19U, 20U, 21U, 22U, 23U, 24U,
    25U, 26U, 27U, 28U, 29U, 30U, 31U, 32U, 33U, 34U, 35U, 36U, 37U, 38U, 39U,
    40U, 41U, 42U, 43U, 44U, 45U, 46U, 47U, 48U, 49U, 50U, 51U, 52U, 53U, 54U,
    55U, 56U, 57U, 58U, 59U, 60U, 61U, 62U, 63U, 64U, 65U, 66U, 67U, 68U, 69U,
    70U, 71U, 72U, 73U, 74U, 75U, 76U, 77U, 78U, 79U, 80U, 81U, 82U, 83U, 84U,
    85U, 86U, 87U, 88U, 89U, 90U, 91U, 92U, 93U, 94U, 95U, 96U, 97U, 98U, 99U,
    100U, 101U, 102U, 103U, 104U, 105U, 106U, 107U, 108U, 109U, 110U, 111U, 112U,
    113U, 114U, 115U, 116U, 117U, 118U, 119U, 120U, 121U, 122U, 123U, 124U, 125U,
    126U, 127U, 128U, 129U, 130U, 131U, 132U, 133U, 134U, 135U, 136U, 137U, 138U,
    139U, 140U, 141U, 142U, 143U, 144U, 145U, 146U, 147U, 148U, 149U, 150U, 151U,
    152U, 153U, 154U, 155U, 156U, 157U, 158U, 159U, 160U, 161U, 162U, 163U, 164U,
    165U, 166U, 167U, 168U, 169U, 170U, 171U, 172U, 173U, 174U, 175U, 176U, 177U,
    178U, 179U, 180U, 181U, 182U, 183U, 184U, 185U, 186U, 187U, 188U, 189U, 190U,
    191U, 192U, 193U, 194U, 195U, 196U, 197U, 198U, 199U, 200U, 201U, 202U, 203U,
    204U, 205U, 206U, 207U, 208U, 209U, 210U, 211U, 212U, 213U, 214U, 215U, 216U,
    217U, 218U, 219U, 220U, 221U, 222U, 223U, 224U, 225U, 226U, 227U, 228U, 229U,
    230U, 231U, 232U, 233U, 234U, 235U, 236U, 237U, 238U, 239U, 240U, 241U, 242U,
    243U, 244U, 245U, 246U, 247U, 248U, 249U, 250U };

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
// Arguments    : double x[250]
// Return Type  : void
//
static void sort(double x[250])
{
  int idx[250];
  int i;
  double xwork[250];
  double x4[4];
  int nNaNs;
  unsigned char idx4[4];
  int ib;
  int k;
  signed char perm[4];
  int bLen;
  int iwork[250];
  int nPairs;
  int i4;
  memset(&idx[0], 0, 250U * sizeof(int));
  for (i = 0; i < 4; i++) {
    x4[i] = 0.0;
    idx4[i] = 0;
  }

  memset(&xwork[0], 0, 250U * sizeof(double));
  nNaNs = -249;
  ib = 0;
  for (k = 0; k < 250; k++) {
    if (rtIsNaN(x[k])) {
      idx[-nNaNs] = k + 1;
      xwork[-nNaNs] = x[k];
      nNaNs++;
    } else {
      ib++;
      idx4[ib - 1] = (unsigned char)(k + 1);
      x4[ib - 1] = x[k];
      if (ib == 4) {
        i = (k - nNaNs) - 252;
        if (x4[0] <= x4[1]) {
          ib = 1;
          bLen = 2;
        } else {
          ib = 2;
          bLen = 1;
        }

        if (x4[2] <= x4[3]) {
          nPairs = 3;
          i4 = 4;
        } else {
          nPairs = 4;
          i4 = 3;
        }

        if (x4[ib - 1] <= x4[nPairs - 1]) {
          if (x4[bLen - 1] <= x4[nPairs - 1]) {
            perm[0] = (signed char)ib;
            perm[1] = (signed char)bLen;
            perm[2] = (signed char)nPairs;
            perm[3] = (signed char)i4;
          } else if (x4[bLen - 1] <= x4[i4 - 1]) {
            perm[0] = (signed char)ib;
            perm[1] = (signed char)nPairs;
            perm[2] = (signed char)bLen;
            perm[3] = (signed char)i4;
          } else {
            perm[0] = (signed char)ib;
            perm[1] = (signed char)nPairs;
            perm[2] = (signed char)i4;
            perm[3] = (signed char)bLen;
          }
        } else if (x4[ib - 1] <= x4[i4 - 1]) {
          if (x4[bLen - 1] <= x4[i4 - 1]) {
            perm[0] = (signed char)nPairs;
            perm[1] = (signed char)ib;
            perm[2] = (signed char)bLen;
            perm[3] = (signed char)i4;
          } else {
            perm[0] = (signed char)nPairs;
            perm[1] = (signed char)ib;
            perm[2] = (signed char)i4;
            perm[3] = (signed char)bLen;
          }
        } else {
          perm[0] = (signed char)nPairs;
          perm[1] = (signed char)i4;
          perm[2] = (signed char)ib;
          perm[3] = (signed char)bLen;
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

  i = (nNaNs + 249) >> 1;
  for (k = 1; k <= i; k++) {
    ib = idx[k - nNaNs];
    idx[k - nNaNs] = idx[250 - k];
    idx[250 - k] = ib;
    x[k - nNaNs] = xwork[250 - k];
    x[250 - k] = xwork[k - nNaNs];
  }

  if (((nNaNs + 249) & 1) != 0) {
    x[(i - nNaNs) + 1] = xwork[(i - nNaNs) + 1];
  }

  if (1 - nNaNs > 1) {
    memset(&iwork[0], 0, 250U * sizeof(int));
    nPairs = (1 - nNaNs) >> 2;
    bLen = 4;
    while (nPairs > 1) {
      if ((nPairs & 1) != 0) {
        nPairs--;
        i = bLen * nPairs;
        ib = 1 - (nNaNs + i);
        if (ib > bLen) {
          merge(idx, x, i, bLen, ib - bLen, iwork, xwork);
        }
      }

      i = bLen << 1;
      nPairs >>= 1;
      for (k = 1; k <= nPairs; k++) {
        merge(idx, x, (k - 1) * i, bLen, bLen, iwork, xwork);
      }

      bLen = i;
    }

    if (1 - nNaNs > bLen) {
      merge(idx, x, 0, bLen, 1 - (nNaNs + bLen), iwork, xwork);
    }
  }
}

//
// Arguments    : const double x[250]
// Return Type  : double
//
static double trapz(const double x[250])
{
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
// Inputs:
//  dW = data array : [4 x 30000]
//  Outputs
//  P = [11 x 1] Contains double size parameters
//  .Index..,1....2....3....4....5....6....7..%
// Arguments    : const double dW[120000]
//                double F[4960]
// Return Type  : void
//
void ctrainingRoutineKNN(const double dW[120000], double F[4960])
{
  double dWF[750];
  static double FILT_FULL[90000];
  static double b_FILT_FULL[90000];
  int i;
  int ftmp;
  int j;
  double RMS[4960];
  double CLASS[496];
  int ixstart;
  double b_RMS[1488];
  double sigRMSIntegral[1488];
  double x[250];
  double MAX[1488];
  double sigRMS[250];
  double b_sigRMS[750];
  double mtmp;
  boolean_T exitg1;
  double M;
  int k;

  //  2Hz High Pass:
  // Param declaration:
  // window separation
  // Other var decs:
  memset(&dWF[0], 0, 750U * sizeof(double));

  //  average out data from "0" class
  //  FILT ENTIRE SIG?:
  filtfilt(*(double (*)[90000])&dW[0], FILT_FULL);
  memcpy(&b_FILT_FULL[0], &FILT_FULL[0], 90000U * sizeof(double));
  c_filtfilt(b_FILT_FULL, FILT_FULL);
  for (i = 0; i < 3; i++) {
    // select chunk of 250:
    for (j = 0; j < 496; j++) {
      ftmp = 60 * j;
      memcpy(&dWF[i * 250], &FILT_FULL[i * 30000 + ftmp], 250U * sizeof(double));
      sig_rms_pad_fixed(*(double (*)[250])&dWF[250 * i], x);
      for (ftmp = 0; ftmp < 250; ftmp++) {
        b_sigRMS[i + 3 * ftmp] = x[ftmp];
        sigRMS[ftmp] = b_sigRMS[i + 3 * ftmp];
      }

      sigRMSIntegral[i + 3 * j] = trapz(sigRMS);
      b_RMS[i + 3 * j] = rms(*(double (*)[250])&dWF[250 * i]);
      ixstart = 1;
      mtmp = dWF[250 * i];
      if (rtIsNaN(dWF[250 * i])) {
        ftmp = 2;
        exitg1 = false;
        while ((!exitg1) && (ftmp < 251)) {
          ixstart = ftmp;
          if (!rtIsNaN(dWF[(ftmp + 250 * i) - 1])) {
            mtmp = dWF[(ftmp + 250 * i) - 1];
            exitg1 = true;
          } else {
            ftmp++;
          }
        }
      }

      if (ixstart < 250) {
        while (ixstart + 1 < 251) {
          if (dWF[ixstart + 250 * i] > mtmp) {
            mtmp = dWF[ixstart + 250 * i];
          }

          ixstart++;
        }
      }

      MAX[i + 3 * j] = mtmp;
      ftmp = 60 * j;
      memcpy(&x[0], &dW[ftmp + 90000], 250U * sizeof(double));
      sort(x);
      M = x[0];
      ixstart = 1;
      mtmp = x[0];
      ftmp = 1;
      for (k = 0; k < 249; k++) {
        if (x[k + 1] == mtmp) {
          ftmp++;
        } else {
          if (ftmp > ixstart) {
            M = mtmp;
            ixstart = ftmp;
          }

          mtmp = x[k + 1];
          ftmp = 1;
        }
      }

      if (ftmp > ixstart) {
        M = mtmp;
      }

      CLASS[j] = M;
    }
  }

  for (ftmp = 0; ftmp < 3; ftmp++) {
    for (ixstart = 0; ixstart < 496; ixstart++) {
      RMS[ixstart + 496 * ftmp] = b_RMS[ftmp + 3 * ixstart];
      RMS[ixstart + 496 * (ftmp + 3)] = sigRMSIntegral[ftmp + 3 * ixstart];
      RMS[ixstart + 496 * (ftmp + 6)] = MAX[ftmp + 3 * ixstart];
    }
  }

  memcpy(&RMS[4464], &CLASS[0], 496U * sizeof(double));
  memcpy(&F[0], &RMS[0], 4960U * sizeof(double));
}

//
// Arguments    : void
// Return Type  : void
//
void ctrainingRoutineKNN_initialize()
{
  rt_InitInfAndNaN(8U);
}

//
// Arguments    : void
// Return Type  : void
//
void ctrainingRoutineKNN_terminate()
{
  // (no terminate code required)
}

//
// File trailer for ctrainingRoutineKNN.cpp
//
// [EOF]
//
