//
// Academic License - for use in teaching, academic research, and meeting
// course requirements at degree granting institutions only.  Not for
// government, commercial, or other organizational use.
// File: classifyArmEMG4.h
//
// MATLAB Coder version            : 3.3
// C/C++ source code generated on  : 13-Sep-2017 16:20:45
//
#ifndef CLASSIFYARMEMG4_H
#define CLASSIFYARMEMG4_H

// Include Files
#include <cmath>
#include <math.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include "rt_nonfinite.h"
#include "rtwtypes.h"
#include "classifyArmEMG4_types.h"

// Function Declarations
extern double classifyArmEMG4(const double dW[2250], const double F[9920],
  double k);
extern void classifyArmEMG4_initialize();
extern void classifyArmEMG4_terminate();

#endif

//
// File trailer for classifyArmEMG4.h
//
// [EOF]
//
