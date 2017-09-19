//
// Academic License - for use in teaching, academic research, and meeting
// course requirements at degree granting institutions only.  Not for
// government, commercial, or other organizational use.
// File: classifyArmEMG2.h
//
// MATLAB Coder version            : 3.3
// C/C++ source code generated on  : 12-Sep-2017 20:33:37
//
#ifndef CLASSIFYARMEMG2_H
#define CLASSIFYARMEMG2_H

// Include Files
#include <cmath>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include "rt_nonfinite.h"
#include "rtwtypes.h"
#include "classifyArmEMG2_types.h"

// Function Declarations
extern void classifyArmEMG2(const double dW[2250], double LastY, const double
  PARAMS[11], double *Y, double F[9]);
extern void classifyArmEMG2_initialize();
extern void classifyArmEMG2_terminate();

#endif

//
// File trailer for classifyArmEMG2.h
//
// [EOF]
//
