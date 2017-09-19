//
// Academic License - for use in teaching, academic research, and meeting
// course requirements at degree granting institutions only.  Not for
// government, commercial, or other organizational use.
// File: classifyArmEMG2.cpp
//
// MATLAB Coder version            : 3.3
// C/C++ source code generated on  : 12-Sep-2017 20:33:37
//

// Include Files
#include "rt_nonfinite.h"
#include "classifyArmEMG2.h"

// Function Declarations
static void b_filter(const double b[4], const double a[4], const double x[768],
                     const double zi[3], double y[768]);
static void b_filtfilt(const double x_in[750], double y_out[750]);
static void b_flipud(double x[768]);
static void filter(const double b[7], const double a[7], const double x[786],
                   const double zi[6], double y[786]);
static void filtfilt(const double x_in[750], double y_out[750]);
static void flipud(double x[786]);
static double mean(const double x_data[], const int x_size[1]);
static void power(const double a[259], double y[259]);
static double rms(const double x[250]);
static void sig_rms_pad_fixed(const double b_signal[250], double y[250]);
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
                     const double zi[3], double y[768])
{
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
static void b_filtfilt(const double x_in[750], double y_out[750])
{
  double d2;
  double d3;
  int i;
  double y[768];
  double b_y[768];
  double a[3];
  static const double b_a[3] = { -0.95097188792826548, 1.9019437758560462,
    -0.95097188792780118 };

  static const double dv3[4] = { 0.950971887923409, -2.85291566377023,
    2.85291566377023, -0.950971887923409 };

  static const double dv4[4] = { 1.0, -2.89947959461186, 2.803947977383,
    -0.904347531392409 };

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
static void b_flipud(double x[768])
{
  int i;
  double xtmp;
  for (i = 0; i < 384; i++) {
    xtmp = x[i];
    x[i] = x[767 - i];
    x[767 - i] = xtmp;
  }
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
                   const double zi[6], double y[786])
{
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
static void filtfilt(const double x_in[750], double y_out[750])
{
  double d0;
  double d1;
  int i;
  double y[786];
  double b_y[786];
  double a[6];
  static const double b_a[6] = { 0.22275347859979613, 0.16989850397289177,
    0.33991371041886664, 0.34619414482388972, 0.12656228167104569,
    0.17313682189292717 };

  static const double dv1[7] = { 0.777246521400202, -0.295149620198606,
    2.36909935327861, -0.591875563889248, 2.36909935327861, -0.295149620198606,
    0.777246521400202 };

  static const double dv2[7] = { 1.0, -0.348004594825511, 2.53911455972459,
    -0.585595129484226, 2.14946749012577, -0.248575079976725, 0.604109699507276
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
static void flipud(double x[786])
{
  int i;
  double xtmp;
  for (i = 0; i < 393; i++) {
    xtmp = x[i];
    x[i] = x[785 - i];
    x[785 - i] = xtmp;
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
// , RMS, COMBMAX, sigRMSIntegral
// Arguments    : const double dW[2250]
//                double LastY
//                const double PARAMS[11]
//                double *Y
//                double F[9]
// Return Type  : void
//
void classifyArmEMG2(const double dW[2250], double LastY, const double PARAMS[11],
                     double *Y, double F[9])
{
  int i;
  double dWF0[2250];
  double sigRMSIntegral[3];
  double b_dWF0[750];
  double RMS[3];
  double dWF[750];
  double dv0[250];
  double b_RMS[9];
  int ix;
  int ixstart;
  double mtmp;
  double sigRMS[250];
  double b_sigRMS[750];
  double MAX[3];
  int b_ix;
  boolean_T exitg1;
  boolean_T THR_EXC;
  boolean_T B7_4_C1;
  boolean_T B6;
  boolean_T B5;
  boolean_T B4;
  boolean_T B3;

  // classifyArmEMG
  //  Wn = [55. 65]*2/Fs;
  //  [b,a] = butter(3, Wn, 'stop');
  //  Wn2 = (2)*2/Fs; %high pass:
  //  [b1,a1] = butter(3, Wn2, 'high');
  //  2Hz High Pass:
  //  LAST 1s / 6s
  for (i = 0; i < 3; i++) {
    filtfilt(*(double (*)[750])&dW[750 * i], *(double (*)[750])&dWF0[750 * i]);
    memcpy(&b_dWF0[0], &dWF0[i * 750], 750U * sizeof(double));
    b_filtfilt(b_dWF0, *(double (*)[750])&dWF0[750 * i]);

    //      dWF(:,i) = dWF0(end-249:end,i);
    memcpy(&dWF[i * 250], &dWF0[i * 750 + 375], 250U * sizeof(double));

    //  Feature Extraction
    sig_rms_pad_fixed(*(double (*)[250])&dWF[250 * i], dv0);
    for (ix = 0; ix < 250; ix++) {
      b_sigRMS[i + 3 * ix] = dv0[ix];
      sigRMS[ix] = b_sigRMS[i + 3 * ix];
    }

    sigRMSIntegral[i] = trapz(sigRMS);
    RMS[i] = rms(*(double (*)[250])&dWF[250 * i]);
  }

  if (LastY == 1.0) {
    if (sigRMSIntegral[0] > PARAMS[1]) {
      // 0.035
      // Check for Ripple
      *Y = 1.0;

      // hand still closed
    } else {
      *Y = 0.0;
    }
  } else if ((LastY == 7.0) || (LastY == 4.0)) {
    if (RMS[2] >= PARAMS[2]) {
      *Y = LastY;
    } else {
      *Y = 0.0;
    }
  } else if ((LastY == 6.0) || (LastY == 5.0) || (LastY == 3.0)) {
    if (RMS[0] >= PARAMS[3]) {
      *Y = LastY;
    } else {
      *Y = 0.0;
    }
  } else {
    *Y = 0.0;
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
    b_RMS[i] = RMS[i];
    b_RMS[i + 3] = MAX[i];
    b_RMS[i + 6] = sigRMSIntegral[i];
  }

  memcpy(&F[0], &b_RMS[0], 9U * sizeof(double));

  // Analysis:
  // +/- 2E-3
  if ((MAX[0] > PARAMS[4]) && (MAX[1] > PARAMS[4]) && (MAX[2] > PARAMS[4])) {
    *Y = 1.0;
  }

  //  1. Set minimum threshold for activity:
  // 1.5E-4;
  //  2. Is that threshold exceeded?
  if ((RMS[0] > PARAMS[0]) || (RMS[1] > PARAMS[0]) || (RMS[2] > PARAMS[0])) {
    THR_EXC = true;
  } else {
    THR_EXC = false;
  }

  //  3. checks to see if rms(ch3) > ch2 and ch1; also checks RMS(ch3) > 3E-4
  if ((RMS[2] > RMS[0]) && (RMS[2] > RMS[1]) && (RMS[2] > PARAMS[5])) {
    B7_4_C1 = true;
  } else {
    B7_4_C1 = false;
  }

  //  4. a large gap between rms(ch3) and rms(ch1) means it is pinky
  //  5. ch1>3>2 ? 6
  if ((RMS[0] > RMS[2]) && (RMS[2] > RMS[1])) {
    B6 = true;
  } else {
    B6 = false;
  }

  //  6. ch1>2>3 with a small gap between 1 and 3 ? 5
  if ((RMS[1] > RMS[2]) && (RMS[0] - RMS[2] < PARAMS[7])) {
    B5 = true;
  } else {
    B5 = false;
  }

  // RMS(1) > RMS(2) &&
  //  7. small gap between 1 and 2, RMS(1) is < 0.3mV
  if ((RMS[0] - RMS[1] < PARAMS[8]) && (RMS[0] < PARAMS[9])) {
    B4 = true;
  } else {
    B4 = false;
  }

  //  8. ch1>ch2, and MAX(3) > 0.7mV
  if ((RMS[0] > RMS[1]) && (MAX[2] < PARAMS[10])) {
    B3 = true;
  } else {
    B3 = false;
  }

  if ((*Y == 0.0) && THR_EXC) {
    // digit classification.
    if (B7_4_C1) {
      //  can be 7 or 4
      if (RMS[2] - RMS[0] > PARAMS[6]) {
        *Y = 7.0;
      } else {
        *Y = 4.0;
      }
    } else if (B6) {
      *Y = 6.0;
    } else if (B5) {
      *Y = 5.0;
    } else if (B4) {
      *Y = 4.0;
    } else if (B3) {
      *Y = 3.0;
    } else {
      *Y = 0.0;
    }
  }
}

//
// Arguments    : void
// Return Type  : void
//
void classifyArmEMG2_initialize()
{
  rt_InitInfAndNaN(8U);
}

//
// Arguments    : void
// Return Type  : void
//
void classifyArmEMG2_terminate()
{
  // (no terminate code required)
}

//
// File trailer for classifyArmEMG2.cpp
//
// [EOF]
//
