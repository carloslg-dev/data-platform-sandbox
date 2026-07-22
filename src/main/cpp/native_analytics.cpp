#include <jni.h>
#include <cmath>
#include <vector>
#include <cstdint>

#ifdef _WIN32
#define EXPORT __declspec(dllexport)
#else
#define EXPORT
#endif

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Class:     com_telecom_analytics_platform_infrastructure_adapter_outbound_nativeopt_NativeAnalyticsAdapter
 * Method:    calculateStdDevNative
 * Signature: ([J)D
 * 
 * JNI path for calculating Standard Deviation.
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

/**
 * Method:    calculate_mean_panama
 * Parameter: durations - pointer to a contiguous array of 64-bit integer values
 * Parameter: size - length of the array
 * Return:    double - calculated mean value
 * 
 * Panama (FFM API) path for calculating the mean of event durations.
 * 
 * Why int64_t is used instead of long:
 * In C/C++, 'long' has a platform-dependent size:
 * - 32-bit (4 bytes) on Windows 64-bit (LLP64 model)
 * - 64-bit (8 bytes) on Linux/macOS 64-bit (LP64 model)
 * However, Java's 'long' primitive is strictly defined as a 64-bit signed integer on all platforms.
 * Utilizing 'int64_t' from <cstdint> guarantees that the compiler maps this parameter to an exact 64-bit width,
 * matching Java's memory layout and preventing memory alignment crashes across OS architectures (Windows/Linux).
 */
EXPORT double calculate_mean_panama(const int64_t* durations, int32_t size) {
    if (durations == nullptr || size <= 0) {
        return 0.0;
    }
    double sum = 0.0;
    for (int32_t i = 0; i < size; i++) {
        sum += durations[i];
    }
    return sum / size;
}

#ifdef __cplusplus
}
#endif
