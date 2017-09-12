//
// Created by mahmoodms on 4/3/2017.
//
// PACKAGE NAME: com.mahmoodms.bluetooth.emghandcontrol
#include "rt_nonfinite.h"
//#include "classifySSVEP.h"
#include "classifyArmEMG.h"
#include "classifyArmEMG2.h"
/*Additional Includes*/
#include <jni.h>
#include <android/log.h>

#define  LOG_TAG "jniExecutor-cpp"
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Function Definitions

extern "C" {
JNIEXPORT jdouble JNICALL
Java_com_mahmoodms_bluetooth_emghandcontrol_DeviceControlActivity_jClassify(
        JNIEnv *env, jobject jobject1, jdoubleArray ch123Data, jdouble Y) {
    jdouble *X1 = env->GetDoubleArrayElements(ch123Data, NULL);
    if (X1 == NULL) LOGE("ERROR - C_ARRAY IS NULL");
    return classifyArmEMG(X1, Y);
}
}

extern "C" {
JNIEXPORT jdouble JNICALL
Java_com_mahmoodms_bluetooth_emghandcontrol_DeviceControlActivity_jClassifyWithParams(
        JNIEnv *env, jobject jobject1, jdoubleArray ch123Data, jdoubleArray params, jdouble LastY) {
    jdouble *X1 = env->GetDoubleArrayElements(ch123Data, NULL);
    jdouble *PARAMS = env->GetDoubleArrayElements(params, NULL);
    if (X1 == NULL) LOGE("ERROR - C_ARRAY IS NULL");
    double Y;
    double F[9];
    classifyArmEMG2(X1, LastY, PARAMS, &Y, F );
    return Y;
}
}

extern "C" {
JNIEXPORT jint JNICALL
Java_com_mahmoodms_bluetooth_emghandcontrol_DeviceControlActivity_jmainInitialization(
        JNIEnv *env, jobject obj, jboolean terminate) {
    if (!(bool) terminate) {
        classifyArmEMG_initialize();
        return 0;
    } else {
        return -1;
    }
}
}
