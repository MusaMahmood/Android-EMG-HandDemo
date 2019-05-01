//
// Academic License - for use in teaching, academic research, and meeting
// course requirements at degree granting institutions only.  Not for
// government, commercial, or other organizational use.
// File: ctrainingRoutine.h
//
// MATLAB Coder version            : 3.3
// C/C++ source code generated on  : 12-Sep-2017 20:28:40
//
#ifndef CTRAININGROUTINE_H
#define CTRAININGROUTINE_H

// Include Files
#include <cmath>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include "rt_nonfinite.h"
#include "rtwtypes.h"
#include "ctrainingRoutine_types.h"

// Function Declarations
extern void ctrainingRoutine(const double dW[120000], double P[11]);
extern void ctrainingRoutine_initialize();
extern void ctrainingRoutine_terminate();

#endif

//
// File trailer for ctrainingRoutine.h
//
// [EOF]
//
