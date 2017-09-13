//
// Academic License - for use in teaching, academic research, and meeting
// course requirements at degree granting institutions only.  Not for
// government, commercial, or other organizational use.
// File: ctrainingRoutine.cpp
//
// MATLAB Coder version            : 3.3
// C/C++ source code generated on  : 12-Sep-2017 20:28:40
//

// Include Files
#include "rt_nonfinite.h"
#include "ctrainingRoutine.h"

// Function Declarations
static void b_filter(const double b[4], const double a[4], const double x[30018],
                     const double zi[3], double y[30018]);
static void b_filtfilt(const double x_in[30000], double y_out[30000]);
static void b_flipud(double x[30018]);
static void b_mean(const double x_data[], const int x_size[2], double y_data[],
                   int y_size[2]);
static void c_filtfilt(const double x_in[90000], double y_out[90000]);
static double c_mean(const double x_data[], const int x_size[2]);
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
// Arguments    : const double x_data[]
//                const int x_size[2]
//                double y_data[]
//                int y_size[2]
// Return Type  : void
//
static void b_mean(const double x_data[], const int x_size[2], double y_data[],
                   int y_size[2])
{
  int i;
  int xoffset;
  double s;
  int k;
  y_size[1] = (signed char)x_size[1];
  if (x_size[1] != 0) {
    for (i = 0; i + 1 <= x_size[1]; i++) {
      xoffset = i * 3;
      s = x_data[xoffset];
      for (k = 0; k < 2; k++) {
        s += x_data[(xoffset + k) + 1];
      }

      y_data[i] = s;
    }
  }

  y_size[0] = 1;
  i = (signed char)x_size[1];
  for (xoffset = 0; xoffset < i; xoffset++) {
    y_data[xoffset] /= 3.0;
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
// Arguments    : const double x_data[]
//                const int x_size[2]
// Return Type  : double
//
static double c_mean(const double x_data[], const int x_size[2])
{
  double y;
  int k;
  if (x_size[1] == 0) {
    y = 0.0;
  } else {
    y = x_data[0];
    for (k = 2; k <= x_size[1]; k++) {
      y += x_data[k - 1];
    }
  }

  y /= (double)x_size[1];
  return y;
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

  int i1;
  int i2;
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
      i1 = 0;
      i2 = 0;
    } else {
      i1 = i;
      i2 = i + 10;
    }

    S_size[0] = i2 - i1;
    loop_ub = i2 - i1;
    for (i2 = 0; i2 < loop_ub; i2++) {
      c_signal[i2] = S[i1 + i2];
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
//                double P[11]
// Return Type  : void
//
void ctrainingRoutine(const double dW[120000], double P[11])
{
  double dWF[750];
  static double FILT_FULL[90000];
  static double b_FILT_FULL[90000];
  int i;
  int j;
  int i0;
  int trueCount;
  boolean_T S[840];
  double CLASS[120];
  static const signed char iv0[7] = { 0, 1, 3, 4, 5, 6, 7 };

  double x[250];
  int ixstart;
  double sigRMSIntegral[360];
  double sigRMS[250];
  double b_sigRMS[750];
  double RMS[360];
  int RMS_size[2];
  signed char tmp_data[120];
  double mtmp;
  int ftmp;
  double RMS_data[360];
  int C_size[2];
  boolean_T exitg1;
  double MAX[360];
  double b_mtmp;
  int sigRMSIntegral_size[2];
  signed char b_tmp_data[120];
  double sigRMSIntegral_data[120];
  signed char c_tmp_data[120];
  signed char d_tmp_data[120];
  signed char e_tmp_data[120];
  signed char f_tmp_data[120];
  double A[3];
  signed char g_tmp_data[120];
  signed char h_tmp_data[120];
  signed char i_tmp_data[120];
  signed char j_tmp_data[120];
  signed char k_tmp_data[120];
  signed char l_tmp_data[120];
  signed char m_tmp_data[120];
  double c_mtmp;
  signed char n_tmp_data[120];
  signed char o_tmp_data[120];
  signed char p_tmp_data[120];
  signed char q_tmp_data[120];

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
    for (j = 0; j < 120; j++) {
      i0 = 250 * j;
      memcpy(&dWF[i * 250], &FILT_FULL[i * 30000 + i0], 250U * sizeof(double));
      sig_rms_pad_fixed(*(double (*)[250])&dWF[250 * i], x);
      for (i0 = 0; i0 < 250; i0++) {
        b_sigRMS[i + 3 * i0] = x[i0];
        sigRMS[i0] = b_sigRMS[i + 3 * i0];
      }

      sigRMSIntegral[i + 3 * j] = trapz(sigRMS);
      RMS[i + 3 * j] = rms(*(double (*)[250])&dWF[250 * i]);
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
      i0 = 250 * j;
      memcpy(&x[0], &dW[i0 + 90000], 250U * sizeof(double));
      sort(x);
      b_mtmp = x[0];
      ixstart = 1;
      mtmp = x[0];
      ftmp = 1;
      for (trueCount = 0; trueCount < 249; trueCount++) {
        if (x[trueCount + 1] == mtmp) {
          ftmp++;
        } else {
          if (ftmp > ixstart) {
            b_mtmp = mtmp;
            ixstart = ftmp;
          }

          mtmp = x[trueCount + 1];
          ftmp = 1;
        }
      }

      if (ftmp > ixstart) {
        b_mtmp = mtmp;
      }

      CLASS[j] = b_mtmp;
    }
  }

  // selectVectors:
  for (i = 0; i < 7; i++) {
    for (i0 = 0; i0 < 120; i0++) {
      S[i + 7 * i0] = (CLASS[i0] == iv0[i]);
    }
  }

  //  ALL CLASSES: Min threshold for activity:
  trueCount = 0;
  for (i = 0; i < 120; i++) {
    if (S[7 * i]) {
      trueCount++;
    }
  }

  ixstart = 0;
  for (i = 0; i < 120; i++) {
    if (S[7 * i]) {
      tmp_data[ixstart] = (signed char)(i + 1);
      ixstart++;
    }
  }

  RMS_size[0] = 3;
  RMS_size[1] = trueCount;
  for (i0 = 0; i0 < trueCount; i0++) {
    for (ixstart = 0; ixstart < 3; ixstart++) {
      RMS_data[ixstart + 3 * i0] = RMS[ixstart + 3 * (tmp_data[i0] - 1)];
    }
  }

  b_mean(RMS_data, RMS_size, CLASS, C_size);
  P[0] = c_mean(CLASS, C_size) * 2.1;

  //  CLASS 1: HOLD, Ripple Detection for closed hand:
  trueCount = 0;
  for (i = 0; i < 120; i++) {
    if (S[1 + 7 * i]) {
      trueCount++;
    }
  }

  ixstart = 0;
  for (i = 0; i < 120; i++) {
    if (S[1 + 7 * i]) {
      b_tmp_data[ixstart] = (signed char)(i + 1);
      ixstart++;
    }
  }

  sigRMSIntegral_size[0] = 1;
  sigRMSIntegral_size[1] = trueCount;
  for (i0 = 0; i0 < trueCount; i0++) {
    sigRMSIntegral_data[i0] = sigRMSIntegral[1 + 3 * (b_tmp_data[i0] - 1)];
  }

  P[1] = c_mean(sigRMSIntegral_data, sigRMSIntegral_size) * 0.7;

  //  CLASS 4,7 HOLD, Ripple detection for C4,7
  //      R(1) = mean(RMS(3,S(7,:))); R(2) = mean(RMS(3,S(4,:)));
  trueCount = 0;
  for (i = 0; i < 120; i++) {
    if (S[6 + 7 * i]) {
      trueCount++;
    }
  }

  ixstart = 0;
  for (i = 0; i < 120; i++) {
    if (S[6 + 7 * i]) {
      c_tmp_data[ixstart] = (signed char)(i + 1);
      ixstart++;
    }
  }

  ixstart = 1;
  for (i0 = 0; i0 < trueCount; i0++) {
    d_tmp_data[i0] = c_tmp_data[i0];
  }

  mtmp = RMS[2 + 3 * (d_tmp_data[0] - 1)];
  if (trueCount > 1) {
    if (rtIsNaN(RMS[2 + 3 * (d_tmp_data[0] - 1)])) {
      ftmp = 2;
      exitg1 = false;
      while ((!exitg1) && (ftmp <= trueCount)) {
        ixstart = ftmp;
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = c_tmp_data[i0];
        }

        if (!rtIsNaN(RMS[2 + 3 * (d_tmp_data[ftmp - 1] - 1)])) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = c_tmp_data[i0];
          }

          mtmp = RMS[2 + 3 * (d_tmp_data[ftmp - 1] - 1)];
          exitg1 = true;
        } else {
          ftmp++;
        }
      }
    }

    if (ixstart < trueCount) {
      while (ixstart + 1 <= trueCount) {
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = c_tmp_data[i0];
        }

        if (RMS[2 + 3 * (d_tmp_data[ixstart] - 1)] < mtmp) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = c_tmp_data[i0];
          }

          mtmp = RMS[2 + 3 * (d_tmp_data[ixstart] - 1)];
        }

        ixstart++;
      }
    }
  }

  trueCount = 0;
  for (i = 0; i < 120; i++) {
    if (S[3 + 7 * i]) {
      trueCount++;
    }
  }

  ixstart = 0;
  for (i = 0; i < 120; i++) {
    if (S[3 + 7 * i]) {
      e_tmp_data[ixstart] = (signed char)(i + 1);
      ixstart++;
    }
  }

  ixstart = 1;
  for (i0 = 0; i0 < trueCount; i0++) {
    d_tmp_data[i0] = e_tmp_data[i0];
  }

  b_mtmp = RMS[2 + 3 * (d_tmp_data[0] - 1)];
  if (trueCount > 1) {
    if (rtIsNaN(RMS[2 + 3 * (d_tmp_data[0] - 1)])) {
      ftmp = 2;
      exitg1 = false;
      while ((!exitg1) && (ftmp <= trueCount)) {
        ixstart = ftmp;
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = e_tmp_data[i0];
        }

        if (!rtIsNaN(RMS[2 + 3 * (d_tmp_data[ftmp - 1] - 1)])) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = e_tmp_data[i0];
          }

          b_mtmp = RMS[2 + 3 * (d_tmp_data[ftmp - 1] - 1)];
          exitg1 = true;
        } else {
          ftmp++;
        }
      }
    }

    if (ixstart < trueCount) {
      while (ixstart + 1 <= trueCount) {
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = e_tmp_data[i0];
        }

        if (RMS[2 + 3 * (d_tmp_data[ixstart] - 1)] < b_mtmp) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = e_tmp_data[i0];
          }

          b_mtmp = RMS[2 + 3 * (d_tmp_data[ixstart] - 1)];
        }

        ixstart++;
      }
    }
  }

  ixstart = 1;
  if (rtIsNaN(mtmp)) {
    ftmp = 2;
    exitg1 = false;
    while ((!exitg1) && (ftmp < 3)) {
      ixstart = 2;
      if (!rtIsNaN(b_mtmp)) {
        mtmp = b_mtmp;
        exitg1 = true;
      } else {
        ftmp = 3;
      }
    }
  }

  if ((ixstart < 2) && (b_mtmp < mtmp)) {
    mtmp = b_mtmp;
  }

  P[2] = mtmp * 0.8;

  //  CLASS 3,5,6: HOLD, Ripple detection for C3,5,6
  trueCount = 0;
  for (i = 0; i < 120; i++) {
    if (S[5 + 7 * i]) {
      trueCount++;
    }
  }

  ixstart = 0;
  for (i = 0; i < 120; i++) {
    if (S[5 + 7 * i]) {
      f_tmp_data[ixstart] = (signed char)(i + 1);
      ixstart++;
    }
  }

  ixstart = 1;
  for (i0 = 0; i0 < trueCount; i0++) {
    d_tmp_data[i0] = f_tmp_data[i0];
  }

  mtmp = RMS[3 * (d_tmp_data[0] - 1)];
  if (trueCount > 1) {
    if (rtIsNaN(RMS[3 * (d_tmp_data[0] - 1)])) {
      ftmp = 2;
      exitg1 = false;
      while ((!exitg1) && (ftmp <= trueCount)) {
        ixstart = ftmp;
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = f_tmp_data[i0];
        }

        if (!rtIsNaN(RMS[3 * (d_tmp_data[ftmp - 1] - 1)])) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = f_tmp_data[i0];
          }

          mtmp = RMS[3 * (d_tmp_data[ftmp - 1] - 1)];
          exitg1 = true;
        } else {
          ftmp++;
        }
      }
    }

    if (ixstart < trueCount) {
      while (ixstart + 1 <= trueCount) {
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = f_tmp_data[i0];
        }

        if (RMS[3 * (d_tmp_data[ixstart] - 1)] < mtmp) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = f_tmp_data[i0];
          }

          mtmp = RMS[3 * (d_tmp_data[ixstart] - 1)];
        }

        ixstart++;
      }
    }
  }

  A[0] = mtmp;
  trueCount = 0;
  for (i = 0; i < 120; i++) {
    if (S[4 + 7 * i]) {
      trueCount++;
    }
  }

  ixstart = 0;
  for (i = 0; i < 120; i++) {
    if (S[4 + 7 * i]) {
      g_tmp_data[ixstart] = (signed char)(i + 1);
      ixstart++;
    }
  }

  ixstart = 1;
  for (i0 = 0; i0 < trueCount; i0++) {
    d_tmp_data[i0] = g_tmp_data[i0];
  }

  b_mtmp = RMS[3 * (d_tmp_data[0] - 1)];
  if (trueCount > 1) {
    if (rtIsNaN(RMS[3 * (d_tmp_data[0] - 1)])) {
      ftmp = 2;
      exitg1 = false;
      while ((!exitg1) && (ftmp <= trueCount)) {
        ixstart = ftmp;
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = g_tmp_data[i0];
        }

        if (!rtIsNaN(RMS[3 * (d_tmp_data[ftmp - 1] - 1)])) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = g_tmp_data[i0];
          }

          b_mtmp = RMS[3 * (d_tmp_data[ftmp - 1] - 1)];
          exitg1 = true;
        } else {
          ftmp++;
        }
      }
    }

    if (ixstart < trueCount) {
      while (ixstart + 1 <= trueCount) {
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = g_tmp_data[i0];
        }

        if (RMS[3 * (d_tmp_data[ixstart] - 1)] < b_mtmp) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = g_tmp_data[i0];
          }

          b_mtmp = RMS[3 * (d_tmp_data[ixstart] - 1)];
        }

        ixstart++;
      }
    }
  }

  A[1] = b_mtmp;
  trueCount = 0;
  for (i = 0; i < 120; i++) {
    if (S[2 + 7 * i]) {
      trueCount++;
    }
  }

  ixstart = 0;
  for (i = 0; i < 120; i++) {
    if (S[2 + 7 * i]) {
      h_tmp_data[ixstart] = (signed char)(i + 1);
      ixstart++;
    }
  }

  ixstart = 1;
  for (i0 = 0; i0 < trueCount; i0++) {
    d_tmp_data[i0] = h_tmp_data[i0];
  }

  b_mtmp = RMS[3 * (d_tmp_data[0] - 1)];
  if (trueCount > 1) {
    if (rtIsNaN(RMS[3 * (d_tmp_data[0] - 1)])) {
      ftmp = 2;
      exitg1 = false;
      while ((!exitg1) && (ftmp <= trueCount)) {
        ixstart = ftmp;
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = h_tmp_data[i0];
        }

        if (!rtIsNaN(RMS[3 * (d_tmp_data[ftmp - 1] - 1)])) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = h_tmp_data[i0];
          }

          b_mtmp = RMS[3 * (d_tmp_data[ftmp - 1] - 1)];
          exitg1 = true;
        } else {
          ftmp++;
        }
      }
    }

    if (ixstart < trueCount) {
      while (ixstart + 1 <= trueCount) {
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = h_tmp_data[i0];
        }

        if (RMS[3 * (d_tmp_data[ixstart] - 1)] < b_mtmp) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = h_tmp_data[i0];
          }

          b_mtmp = RMS[3 * (d_tmp_data[ixstart] - 1)];
        }

        ixstart++;
      }
    }
  }

  A[2] = b_mtmp;
  ixstart = 1;
  if (rtIsNaN(mtmp)) {
    ftmp = 2;
    exitg1 = false;
    while ((!exitg1) && (ftmp < 4)) {
      ixstart = ftmp;
      if (!rtIsNaN(A[ftmp - 1])) {
        mtmp = A[ftmp - 1];
        exitg1 = true;
      } else {
        ftmp++;
      }
    }
  }

  if (ixstart < 3) {
    while (ixstart + 1 < 4) {
      if (A[ixstart] < mtmp) {
        mtmp = A[ixstart];
      }

      ixstart++;
    }
  }

  P[3] = mtmp * 0.9;

  //  ---
  //  CLASS: 1, Hand CLOSE Initial (MAX THRESHOLD, ALL CHANNELS):
  trueCount = 0;
  for (i = 0; i < 120; i++) {
    if (S[1 + 7 * i]) {
      trueCount++;
    }
  }

  ixstart = 0;
  for (i = 0; i < 120; i++) {
    if (S[1 + 7 * i]) {
      i_tmp_data[ixstart] = (signed char)(i + 1);
      ixstart++;
    }
  }

  ixstart = 1;
  for (i0 = 0; i0 < trueCount; i0++) {
    d_tmp_data[i0] = i_tmp_data[i0];
  }

  mtmp = MAX[3 * (d_tmp_data[0] - 1)];
  if (trueCount > 1) {
    if (rtIsNaN(MAX[3 * (d_tmp_data[0] - 1)])) {
      ftmp = 2;
      exitg1 = false;
      while ((!exitg1) && (ftmp <= trueCount)) {
        ixstart = ftmp;
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = i_tmp_data[i0];
        }

        if (!rtIsNaN(MAX[3 * (d_tmp_data[ftmp - 1] - 1)])) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = i_tmp_data[i0];
          }

          mtmp = MAX[3 * (d_tmp_data[ftmp - 1] - 1)];
          exitg1 = true;
        } else {
          ftmp++;
        }
      }
    }

    if (ixstart < trueCount) {
      while (ixstart + 1 <= trueCount) {
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = i_tmp_data[i0];
        }

        if (MAX[3 * (d_tmp_data[ixstart] - 1)] > mtmp) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = i_tmp_data[i0];
          }

          mtmp = MAX[3 * (d_tmp_data[ixstart] - 1)];
        }

        ixstart++;
      }
    }
  }

  A[0] = mtmp;
  trueCount = 0;
  for (i = 0; i < 120; i++) {
    if (S[1 + 7 * i]) {
      trueCount++;
    }
  }

  ixstart = 0;
  for (i = 0; i < 120; i++) {
    if (S[1 + 7 * i]) {
      j_tmp_data[ixstart] = (signed char)(i + 1);
      ixstart++;
    }
  }

  ixstart = 1;
  for (i0 = 0; i0 < trueCount; i0++) {
    d_tmp_data[i0] = j_tmp_data[i0];
  }

  b_mtmp = MAX[1 + 3 * (d_tmp_data[0] - 1)];
  if (trueCount > 1) {
    if (rtIsNaN(MAX[1 + 3 * (d_tmp_data[0] - 1)])) {
      ftmp = 2;
      exitg1 = false;
      while ((!exitg1) && (ftmp <= trueCount)) {
        ixstart = ftmp;
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = j_tmp_data[i0];
        }

        if (!rtIsNaN(MAX[1 + 3 * (d_tmp_data[ftmp - 1] - 1)])) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = j_tmp_data[i0];
          }

          b_mtmp = MAX[1 + 3 * (d_tmp_data[ftmp - 1] - 1)];
          exitg1 = true;
        } else {
          ftmp++;
        }
      }
    }

    if (ixstart < trueCount) {
      while (ixstart + 1 <= trueCount) {
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = j_tmp_data[i0];
        }

        if (MAX[1 + 3 * (d_tmp_data[ixstart] - 1)] > b_mtmp) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = j_tmp_data[i0];
          }

          b_mtmp = MAX[1 + 3 * (d_tmp_data[ixstart] - 1)];
        }

        ixstart++;
      }
    }
  }

  A[1] = b_mtmp;
  trueCount = 0;
  for (i = 0; i < 120; i++) {
    if (S[1 + 7 * i]) {
      trueCount++;
    }
  }

  ixstart = 0;
  for (i = 0; i < 120; i++) {
    if (S[1 + 7 * i]) {
      k_tmp_data[ixstart] = (signed char)(i + 1);
      ixstart++;
    }
  }

  ixstart = 1;
  for (i0 = 0; i0 < trueCount; i0++) {
    d_tmp_data[i0] = k_tmp_data[i0];
  }

  b_mtmp = MAX[2 + 3 * (d_tmp_data[0] - 1)];
  if (trueCount > 1) {
    if (rtIsNaN(MAX[2 + 3 * (d_tmp_data[0] - 1)])) {
      ftmp = 2;
      exitg1 = false;
      while ((!exitg1) && (ftmp <= trueCount)) {
        ixstart = ftmp;
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = k_tmp_data[i0];
        }

        if (!rtIsNaN(MAX[2 + 3 * (d_tmp_data[ftmp - 1] - 1)])) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = k_tmp_data[i0];
          }

          b_mtmp = MAX[2 + 3 * (d_tmp_data[ftmp - 1] - 1)];
          exitg1 = true;
        } else {
          ftmp++;
        }
      }
    }

    if (ixstart < trueCount) {
      while (ixstart + 1 <= trueCount) {
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = k_tmp_data[i0];
        }

        if (MAX[2 + 3 * (d_tmp_data[ixstart] - 1)] > b_mtmp) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = k_tmp_data[i0];
          }

          b_mtmp = MAX[2 + 3 * (d_tmp_data[ixstart] - 1)];
        }

        ixstart++;
      }
    }
  }

  A[2] = b_mtmp;
  ixstart = 1;
  if (rtIsNaN(mtmp)) {
    ftmp = 2;
    exitg1 = false;
    while ((!exitg1) && (ftmp < 4)) {
      ixstart = ftmp;
      if (!rtIsNaN(A[ftmp - 1])) {
        mtmp = A[ftmp - 1];
        exitg1 = true;
      } else {
        ftmp++;
      }
    }
  }

  if (ixstart < 3) {
    while (ixstart + 1 < 4) {
      if (A[ixstart] < mtmp) {
        mtmp = A[ixstart];
      }

      ixstart++;
    }
  }

  P[4] = mtmp * 0.75;

  //  CLASS: 4,7 CLOSE Initial Threshold for detection RMS(3) > P(6)
  trueCount = 0;
  for (i = 0; i < 120; i++) {
    if (S[6 + 7 * i]) {
      trueCount++;
    }
  }

  ixstart = 0;
  for (i = 0; i < 120; i++) {
    if (S[6 + 7 * i]) {
      l_tmp_data[ixstart] = (signed char)(i + 1);
      ixstart++;
    }
  }

  ixstart = 1;
  for (i0 = 0; i0 < trueCount; i0++) {
    d_tmp_data[i0] = l_tmp_data[i0];
  }

  mtmp = RMS[2 + 3 * (d_tmp_data[0] - 1)];
  if (trueCount > 1) {
    if (rtIsNaN(RMS[2 + 3 * (d_tmp_data[0] - 1)])) {
      ftmp = 2;
      exitg1 = false;
      while ((!exitg1) && (ftmp <= trueCount)) {
        ixstart = ftmp;
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = l_tmp_data[i0];
        }

        if (!rtIsNaN(RMS[2 + 3 * (d_tmp_data[ftmp - 1] - 1)])) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = l_tmp_data[i0];
          }

          mtmp = RMS[2 + 3 * (d_tmp_data[ftmp - 1] - 1)];
          exitg1 = true;
        } else {
          ftmp++;
        }
      }
    }

    if (ixstart < trueCount) {
      while (ixstart + 1 <= trueCount) {
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = l_tmp_data[i0];
        }

        if (RMS[2 + 3 * (d_tmp_data[ixstart] - 1)] > mtmp) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = l_tmp_data[i0];
          }

          mtmp = RMS[2 + 3 * (d_tmp_data[ixstart] - 1)];
        }

        ixstart++;
      }
    }
  }

  trueCount = 0;
  for (i = 0; i < 120; i++) {
    if (S[3 + 7 * i]) {
      trueCount++;
    }
  }

  ixstart = 0;
  for (i = 0; i < 120; i++) {
    if (S[3 + 7 * i]) {
      m_tmp_data[ixstart] = (signed char)(i + 1);
      ixstart++;
    }
  }

  ixstart = 1;
  for (i0 = 0; i0 < trueCount; i0++) {
    d_tmp_data[i0] = m_tmp_data[i0];
  }

  c_mtmp = RMS[2 + 3 * (d_tmp_data[0] - 1)];
  if (trueCount > 1) {
    if (rtIsNaN(RMS[2 + 3 * (d_tmp_data[0] - 1)])) {
      ftmp = 2;
      exitg1 = false;
      while ((!exitg1) && (ftmp <= trueCount)) {
        ixstart = ftmp;
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = m_tmp_data[i0];
        }

        if (!rtIsNaN(RMS[2 + 3 * (d_tmp_data[ftmp - 1] - 1)])) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = m_tmp_data[i0];
          }

          c_mtmp = RMS[2 + 3 * (d_tmp_data[ftmp - 1] - 1)];
          exitg1 = true;
        } else {
          ftmp++;
        }
      }
    }

    if (ixstart < trueCount) {
      while (ixstart + 1 <= trueCount) {
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = m_tmp_data[i0];
        }

        if (RMS[2 + 3 * (d_tmp_data[ixstart] - 1)] > c_mtmp) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = m_tmp_data[i0];
          }

          c_mtmp = RMS[2 + 3 * (d_tmp_data[ixstart] - 1)];
        }

        ixstart++;
      }
    }
  }

  ixstart = 1;
  if (rtIsNaN(mtmp)) {
    ftmp = 2;
    exitg1 = false;
    while ((!exitg1) && (ftmp < 3)) {
      ixstart = 2;
      if (!rtIsNaN(c_mtmp)) {
        mtmp = c_mtmp;
        exitg1 = true;
      } else {
        ftmp = 3;
      }
    }
  }

  if ((ixstart < 2) && (c_mtmp < mtmp)) {
    mtmp = c_mtmp;
  }

  P[5] = mtmp * 1.1;

  //  CLASS: 7, Close initial detection, RMS(3)-RMS(1) > P(7);
  trueCount = 0;
  for (i = 0; i < 120; i++) {
    if (S[6 + 7 * i]) {
      trueCount++;
    }
  }

  ixstart = 0;
  for (i = 0; i < 120; i++) {
    if (S[6 + 7 * i]) {
      n_tmp_data[ixstart] = (signed char)(i + 1);
      ixstart++;
    }
  }

  for (i0 = 0; i0 < trueCount; i0++) {
    CLASS[i0] = RMS[2 + 3 * (n_tmp_data[i0] - 1)] - RMS[3 * (n_tmp_data[i0] - 1)];
  }

  //  L = RMS(3,~S(7,:)) - RMS(1,~S(7,:)); % all other:
  ixstart = 1;
  mtmp = CLASS[0];
  if (trueCount > 1) {
    if (rtIsNaN(CLASS[0])) {
      ftmp = 2;
      exitg1 = false;
      while ((!exitg1) && (ftmp <= trueCount)) {
        ixstart = ftmp;
        if (!rtIsNaN(CLASS[ftmp - 1])) {
          mtmp = CLASS[ftmp - 1];
          exitg1 = true;
        } else {
          ftmp++;
        }
      }
    }

    if (ixstart < trueCount) {
      while (ixstart + 1 <= trueCount) {
        if (CLASS[ixstart] < mtmp) {
          mtmp = CLASS[ixstart];
        }

        ixstart++;
      }
    }
  }

  P[6] = mtmp * 1.25;

  //  CLASS: 5, close detection: gap less than RMS(1)-RMS(3)
  trueCount = 0;
  for (i = 0; i < 120; i++) {
    if (S[4 + 7 * i]) {
      trueCount++;
    }
  }

  ixstart = 0;
  for (i = 0; i < 120; i++) {
    if (S[4 + 7 * i]) {
      o_tmp_data[ixstart] = (signed char)(i + 1);
      ixstart++;
    }
  }

  for (i0 = 0; i0 < trueCount; i0++) {
    CLASS[i0] = RMS[3 * (o_tmp_data[i0] - 1)] - RMS[2 + 3 * (o_tmp_data[i0] - 1)];
  }

  ixstart = 1;
  mtmp = CLASS[0];
  if (trueCount > 1) {
    if (rtIsNaN(CLASS[0])) {
      ftmp = 2;
      exitg1 = false;
      while ((!exitg1) && (ftmp <= trueCount)) {
        ixstart = ftmp;
        if (!rtIsNaN(CLASS[ftmp - 1])) {
          mtmp = CLASS[ftmp - 1];
          exitg1 = true;
        } else {
          ftmp++;
        }
      }
    }

    if (ixstart < trueCount) {
      while (ixstart + 1 <= trueCount) {
        if (CLASS[ixstart] > mtmp) {
          mtmp = CLASS[ixstart];
        }

        ixstart++;
      }
    }
  }

  P[7] = mtmp * 1.125;

  //  CLASS: 4, close detection gap less than RMS(1) - RMS(2)
  trueCount = 0;
  for (i = 0; i < 120; i++) {
    if (S[3 + 7 * i]) {
      trueCount++;
    }
  }

  ixstart = 0;
  for (i = 0; i < 120; i++) {
    if (S[3 + 7 * i]) {
      p_tmp_data[ixstart] = (signed char)(i + 1);
      ixstart++;
    }
  }

  for (i0 = 0; i0 < trueCount; i0++) {
    CLASS[i0] = RMS[3 * (p_tmp_data[i0] - 1)] - RMS[1 + 3 * (p_tmp_data[i0] - 1)];
  }

  ixstart = 1;
  mtmp = CLASS[0];
  if (trueCount > 1) {
    if (rtIsNaN(CLASS[0])) {
      ftmp = 2;
      exitg1 = false;
      while ((!exitg1) && (ftmp <= trueCount)) {
        ixstart = ftmp;
        if (!rtIsNaN(CLASS[ftmp - 1])) {
          mtmp = CLASS[ftmp - 1];
          exitg1 = true;
        } else {
          ftmp++;
        }
      }
    }

    if (ixstart < trueCount) {
      while (ixstart + 1 <= trueCount) {
        if (CLASS[ixstart] > mtmp) {
          mtmp = CLASS[ixstart];
        }

        ixstart++;
      }
    }
  }

  P[8] = mtmp * 1.1;

  // CLASS: 4, close detection RMS(1) < P(10);
  trueCount = 0;
  for (i = 0; i < 120; i++) {
    if (S[3 + 7 * i]) {
      trueCount++;
    }
  }

  ixstart = 0;
  for (i = 0; i < 120; i++) {
    if (S[3 + 7 * i]) {
      q_tmp_data[ixstart] = (signed char)(i + 1);
      ixstart++;
    }
  }

  ixstart = 1;
  for (i0 = 0; i0 < trueCount; i0++) {
    d_tmp_data[i0] = q_tmp_data[i0];
  }

  mtmp = RMS[3 * (d_tmp_data[0] - 1)];
  if (trueCount > 1) {
    if (rtIsNaN(RMS[3 * (d_tmp_data[0] - 1)])) {
      ftmp = 2;
      exitg1 = false;
      while ((!exitg1) && (ftmp <= trueCount)) {
        ixstart = ftmp;
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = q_tmp_data[i0];
        }

        if (!rtIsNaN(RMS[3 * (d_tmp_data[ftmp - 1] - 1)])) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = q_tmp_data[i0];
          }

          mtmp = RMS[3 * (d_tmp_data[ftmp - 1] - 1)];
          exitg1 = true;
        } else {
          ftmp++;
        }
      }
    }

    if (ixstart < trueCount) {
      while (ixstart + 1 <= trueCount) {
        for (i0 = 0; i0 < trueCount; i0++) {
          d_tmp_data[i0] = q_tmp_data[i0];
        }

        if (RMS[3 * (d_tmp_data[ixstart] - 1)] > mtmp) {
          for (i0 = 0; i0 < trueCount; i0++) {
            d_tmp_data[i0] = q_tmp_data[i0];
          }

          mtmp = RMS[3 * (d_tmp_data[ixstart] - 1)];
        }

        ixstart++;
      }
    }
  }

  P[9] = mtmp * 1.1;

  // CLASS: 3, close detection.
  P[10] = 0.75 * b_mtmp;
}

//
// Arguments    : void
// Return Type  : void
//
void ctrainingRoutine_initialize()
{
  rt_InitInfAndNaN(8U);
}

//
// Arguments    : void
// Return Type  : void
//
void ctrainingRoutine_terminate()
{
  // (no terminate code required)
}

//
// File trailer for ctrainingRoutine.cpp
//
// [EOF]
//
