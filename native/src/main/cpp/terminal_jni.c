/*
 * terminal_jni.c
 * Pont JNI pour l'exécution de commandes dans le PTY.
 */

#include <jni.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <signal.h>
#include <errno.h>
#include <android/log.h>

#define LOG_TAG "VoidTermJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

JNIEXPORT jint JNICALL
Java_com_voidlinux_core_native_NativeBridge_execInPty(JNIEnv *env, jclass clazz,
                                                     jint master_fd, jint slave_fd,
                                                     jobjectArray arguments,
                                                     jobjectArray environment,
                                                     jstring working_directory) {
    jsize argc = (*env)->GetArrayLength(env, arguments);
    jsize envc = (*env)->GetArrayLength(env, environment);
    if (argc == 0) return -1;

    char **argv = calloc((size_t) argc + 1, sizeof(char *));
    char **envp = calloc((size_t) envc + 1, sizeof(char *));
    if (!argv || !envp) {
        free(argv);
        free(envp);
        return -1;
    }

    for (jsize i = 0; i < argc; i++) {
        jstring value = (jstring) (*env)->GetObjectArrayElement(env, arguments, i);
        if (!value) goto cleanup;
        const char *characters = (*env)->GetStringUTFChars(env, value, NULL);
        if (characters) argv[i] = strdup(characters);
        if (characters) (*env)->ReleaseStringUTFChars(env, value, characters);
        (*env)->DeleteLocalRef(env, value);
        if (!argv[i]) goto cleanup;
    }
    for (jsize i = 0; i < envc; i++) {
        jstring value = (jstring) (*env)->GetObjectArrayElement(env, environment, i);
        if (!value) goto cleanup;
        const char *characters = (*env)->GetStringUTFChars(env, value, NULL);
        if (characters) envp[i] = strdup(characters);
        if (characters) (*env)->ReleaseStringUTFChars(env, value, characters);
        (*env)->DeleteLocalRef(env, value);
        if (!envp[i]) goto cleanup;
    }

    const char *cwd_chars = (*env)->GetStringUTFChars(env, working_directory, NULL);
    if (!cwd_chars) goto cleanup;
    char *cwd = strdup(cwd_chars);
    (*env)->ReleaseStringUTFChars(env, working_directory, cwd_chars);
    if (!cwd) goto cleanup;

    pid_t pid = fork();
    if (pid == 0) {
        if (setsid() < 0) {
            dprintf(slave_fd, "\r\n[terminal] setsid failed: %s\r\n", strerror(errno));
            _exit(126);
        }

        if (ioctl(slave_fd, TIOCSCTTY, 0) < 0) {
            dprintf(slave_fd, "\r\n[terminal] TIOCSCTTY failed: %s\r\n", strerror(errno));
            _exit(126);
        }

        if (dup2(slave_fd, STDIN_FILENO) < 0 ||
            dup2(slave_fd, STDOUT_FILENO) < 0 ||
            dup2(slave_fd, STDERR_FILENO) < 0) {
            dprintf(slave_fd, "\r\n[terminal] dup2 failed: %s\r\n", strerror(errno));
            _exit(126);
        }
        close(master_fd);
        if (slave_fd > STDERR_FILENO) close(slave_fd);
        if (chdir(cwd) < 0) {
            dprintf(STDERR_FILENO, "\r\n[terminal] chdir failed: %s\r\n", strerror(errno));
            _exit(126);
        }

        execve(argv[0], argv, envp);
        dprintf(STDERR_FILENO, "\r\n[terminal] execve(%s) failed: %s\r\n", argv[0], strerror(errno));
        _exit(127);
    }

    for (jsize i = 0; i < argc; i++) {
        free(argv[i]);
    }
    for (jsize i = 0; i < envc; i++) {
        free(envp[i]);
    }
    free(cwd);
    free(argv);
    free(envp);
    if (pid < 0) return -1;

    LOGI("execInPty pid=%d", pid);
    return (jint) pid;

cleanup:
    for (jsize i = 0; i < argc; i++) {
        free(argv[i]);
    }
    for (jsize i = 0; i < envc; i++) {
        free(envp[i]);
    }
    free(argv);
    free(envp);
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_voidlinux_core_native_NativeBridge_waitForProcess(JNIEnv *env, jclass clazz, jint pid) {
    int status;
    pid_t result;
    do {
        result = waitpid((pid_t) pid, &status, 0);
    } while (result < 0 && errno == EINTR);

    if (result < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

JNIEXPORT void JNICALL
Java_com_voidlinux_core_native_NativeBridge_killProcess(JNIEnv *env, jclass clazz, jint pid) {
    if (pid > 0 && kill(-(pid_t) pid, SIGKILL) < 0 && errno == ESRCH) {
        kill((pid_t) pid, SIGKILL);
    }
}

JNIEXPORT jint JNICALL
Java_com_voidlinux_core_native_NativeBridge_writeToPty(JNIEnv *env, jclass clazz,
                                                      jint fd, jbyteArray data) {
    if (fd < 0 || data == NULL) return -1;

    jsize len = (*env)->GetArrayLength(env, data);
    if (len <= 0) return 0;

    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (!bytes) return -1;

    ssize_t written;
    do {
        written = write(fd, bytes, (size_t) len);
    } while (written < 0 && errno == EINTR);

    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);

    if (written < 0) {
        LOGI("PTY write failed fd=%d errno=%d (%s)", fd, errno, strerror(errno));
        return -1;
    }
    return (jint) written;
}

JNIEXPORT jbyteArray JNICALL
Java_com_voidlinux_core_native_NativeBridge_readFromPty(JNIEnv *env, jclass clazz,
                                                       jint fd, jint max_bytes) {
    if (max_bytes <= 0 || max_bytes > 65536) return NULL;

    char *buffer = malloc(max_bytes);
    if (!buffer) return NULL;

    ssize_t n;
    do {
        n = read(fd, buffer, max_bytes);
    } while (n < 0 && errno == EINTR);
    if (n <= 0) {
        free(buffer);
        return NULL;
    }

    jbyteArray result = (*env)->NewByteArray(env, n);
    if (!result) {
        free(buffer);
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, result, 0, n, (jbyte *) buffer);
    free(buffer);
    return result;
}