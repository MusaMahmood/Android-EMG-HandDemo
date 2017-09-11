//
// Academic License - for use in teaching, academic research, and meeting
// course requirements at degree granting institutions only.  Not for
// government, commercial, or other organizational use.
// File: classifyArmEMG.h
//
// MATLAB Coder version            : 3.3
// C/C++ source code generated on  : 11-Sep-2017 00:13:56
//
#ifndef CLASSIFYARMEMG_H
#define CLASSIFYARMEMG_H

// Include Files
#include <cmath>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include "rt_nonfinite.h"
#include "rtwtypes.h"
#include "classifyArmEMG_types.h"

// Function Declarations
extern double classifyArmEMG(const double dW[2250], double LastY);
extern void classifyArmEMG_initialize();
extern void classifyArmEMG_terminate();

#endif

//
// File trailer for classifyArmEMG.h
//
// [EOF]
//
