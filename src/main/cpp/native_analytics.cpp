#include <jni.h>
#include <cmath>
#include <vector>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_telecom_analytics_platform_infrastructure_adapter_outbound_nativeopt_NativeAnalyticsAdapter
 * Method:    calculateStdDevNative
 * Signature: ([J)D
 */
JNIEXPORT jdouble JNICALL Java_com_telecom_analytics_platform_infrastructure_adapter_outbound_nativeopt_NativeAnalyticsAdapter_calculateStdDevNative(
    JNIEnv *env, jobject thisObj, jlongArray durations) {
    
    if (durations == NULL) {
        return 0.0;
    }

    jsize len = env->GetArrayLength(durations);
    if (len == 0) {
        return 0.0;
    }

    jlong *body = env->GetLongArrayElements(durations, 0);
    if (body == NULL) {
        return 0.0;
    }

    // Calculate mean
    double sum = 0.0;
    for (int i = 0; i < len; i++) {
        sum += body[i];
    }
    double mean = sum / len;

    // Calculate sum of squared differences
    double sumSquaredDiff = 0.0;
    for (int i = 0; i < len; i++) {
        double diff = body[i] - mean;
        sumSquaredDiff += diff * diff;
    }

    // Release native resources
    env->ReleaseLongArrayElements(durations, body, JNI_ABORT);

    double variance = sumSquaredDiff / len;
    return std::sqrt(variance);
}

#ifdef __cplusplus
}
#endif
