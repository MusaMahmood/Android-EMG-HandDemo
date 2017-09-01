//
// Created by mahmoodms on 4/3/2017.
//
// PACKAGE NAME: com.mahmoodms.bluetooth.emghandcontrol
#include "rt_nonfinite.h"
//#include "classifySSVEP.h"
#include "classifyArmEMGv2.h"
/*Additional Includes*/
#include <jni.h>
#include <android/log.h>

#define  LOG_TAG "jniExecutor-cpp"
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Function Definitions

extern "C" {
JNIEXPORT jdouble JNICALL
Java_com_mahmoodms_bluetooth_emghandcontrol_DeviceControlActivity_jClassify(
        JNIEnv *env, jobject jobject1, jdoubleArray ch123Data) {
    jdouble *X1 = env->GetDoubleArrayElements(ch123Data, NULL);
    if (X1 == NULL) LOGE("ERROR - C_ARRAY IS NULL");
    return classifyArmEMGv2(X1);
}
}

//extern "C" {
//JNIEXPORT jdoubleArray JNICALL
//Java_com_mahmoodms_bluetooth_emghandcontrol_DeviceControlActivity_jClassifySSVEP2(
//        JNIEnv *env, jobject jobject1, jdoubleArray ch1, jdoubleArray ch2, jdouble threshold) {
//    jdouble *X1 = env->GetDoubleArrayElements(ch1, NULL);
//    jdouble *X2 = env->GetDoubleArrayElements(ch2, NULL);
//    double Y[2];
//    if (X1 == NULL) LOGE("ERROR - C_ARRAY IS NULL");
//    if (X2 == NULL) LOGE("ERROR - C_ARRAY IS NULL");
//    jdoubleArray m_result = env->NewDoubleArray(2);
//    classifySSVEP2(X1,X2,threshold,&Y[0],&Y[1]);
//    env->SetDoubleArrayRegion(m_result, 0, 2, Y);
//    return m_result;
//}
//}


extern "C" {
JNIEXPORT jint JNICALL
Java_com_mahmoodms_bluetooth_emghandcontrol_DeviceControlActivity_jmainInitialization(
        JNIEnv *env, jobject obj, jboolean terminate) {
    if (!(bool) terminate) {
//        classifySSVEP_initialize();
        classifyArmEMGv2_initialize();
        return 0;
    } else {
        return -1;
    }
}
}
