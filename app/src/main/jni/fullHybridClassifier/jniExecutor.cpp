//
// Created by mahmoodms on 4/3/2017.
//
// PACKAGE NAME: com.mahmoodms.bluetooth.emghandcontrol
#include "rt_nonfinite.h"
#include "ctrainingRoutineKNN.h"
#include "classifyArmEMG2.h"
#include "ctrainingRoutine.h"
#include "classifyArmEMG3.h"
/*Additional Includes*/
#include <jni.h>
#include <android/log.h>

#define  LOG_TAG "jniExecutor-cpp"
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Function Definitions

//extern "C" {
//JNIEXPORT jdouble JNICALL
//Java_com_mahmoodms_bluetooth_emghandcontrol_DeviceControlActivity_jClassify(
//        JNIEnv *env, jobject jobject1, jdoubleArray ch123Data, jdouble Y) {
//    jdouble *X1 = env->GetDoubleArrayElements(ch123Data, NULL);
//    if (X1 == NULL) LOGE("ERROR - C_ARRAY IS NULL");
//    return classifyArmEMG(X1, Y);
//}
//}

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
JNIEXPORT jdouble JNICALL
/**
 *
 * @param env
 * @param jobject1
 * @param allData 750x3 vector of data
 * @param params KNN features 1x4960
 * @param LastY
 * @return
 */
Java_com_mahmoodms_bluetooth_emghandcontrol_DeviceControlActivity_jClassifyUsingKNN(
        JNIEnv *env, jobject jobject1, jdoubleArray allData, jdoubleArray params) {
    jdouble *X1 = env->GetDoubleArrayElements(allData, NULL);
    jdouble *PARAMS = env->GetDoubleArrayElements(params, NULL);
    if (X1 == NULL) LOGE("ERROR - C_ARRAY IS NULL");
    return classifyArmEMG3(X1, PARAMS);
}
}

extern "C" {
JNIEXPORT jint JNICALL
Java_com_mahmoodms_bluetooth_emghandcontrol_DeviceControlActivity_jmainInitialization(
        JNIEnv *env, jobject obj, jboolean terminate) {
    if (!(bool) terminate) {
        classifyArmEMG2_initialize();
        classifyArmEMG3_initialize();
        ctrainingRoutine_initialize();
        ctrainingRoutineKNN_initialize();
        return 0;
    } else {
        return -1;
    }
}
}

extern "C" {
JNIEXPORT jdoubleArray JNICALL
Java_com_mahmoodms_bluetooth_emghandcontrol_DeviceControlActivity_jTrainingRoutine(JNIEnv *env, jobject obj, jdoubleArray allData) {
    jdouble *X = env->GetDoubleArrayElements(allData, NULL);
    if (X==NULL) LOGE("ERROR - C_ARRAY");
    double PARAMS[11];
    for (int i = 0; i < 11; ++i) {
        PARAMS[i] = 0.0;
    }
    //TODO: INSERT ANALYSIS FUNCTION HERE
    ctrainingRoutine(X,PARAMS);
    jdoubleArray mReturnArray = env->NewDoubleArray(11);
    env->SetDoubleArrayRegion(mReturnArray, 0, 11, &PARAMS[0]);
    return mReturnArray;
}
}

extern "C" {
JNIEXPORT jdoubleArray JNICALL
Java_com_mahmoodms_bluetooth_emghandcontrol_DeviceControlActivity_jTrainingRoutineKNN(JNIEnv *env, jobject obj, jdoubleArray allData) {
    jdouble *X = env->GetDoubleArrayElements(allData, NULL);
    if (X==NULL) LOGE("ERROR - C_ARRAY");
    double KNNPARAMS[4960];
    //TODO: INSERT ANALYSIS FUNCTION HERE
    ctrainingRoutineKNN(X,KNNPARAMS);
    jdoubleArray mReturnArray = env->NewDoubleArray(4960);
    env->SetDoubleArrayRegion(mReturnArray, 0, 4960, &KNNPARAMS[0]);
    return mReturnArray;
}
}