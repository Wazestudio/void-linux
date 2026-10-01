/*
 * pty_helper.c
 * Crée un pseudo-terminal (PTY) pour le terminal intégré.
 */

#include <jni.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <android/log.h>

#define LOG_TAG "VoidPty"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Implémentation alternative de openpty pour la libc Android (bionic)
static int android_openpty(int *amaster, int *aslave, struct winsize *winp) {
    int master, slave;
    char slave_name[64];

    master = posix_openpt(O_RDWR | O_NOCTTY);
    if (master < 0) return -1;

    if (grantpt(master) < 0 || unlockpt(master) < 0) {
        close(master);
        return -1;
    }

    if (ptsname_r(master, slave_name, sizeof(slave_name)) != 0) {
        close(master);
        return -1;
    }

    slave = open(slave_name, O_RDWR | O_NOCTTY);
    if (slave < 0) {
        close(master);
        return -1;
    }

    if (winp) {
        ioctl(slave, TIOCSWINSZ, winp);
    }

    *amaster = master;
    *aslave = slave;
    return 0;
}

JNIEXPORT jintArray JNICALL
Java_com_voidlinux_core_1native_NativeBridge_createPty(JNIEnv *env, jclass clazz,
                                                     jint cols, jint rows) {
    int master, slave;
    struct winsize ws;

    ws.ws_col = (unsigned short) (cols > 0 ? cols : 80);
    ws.ws_row = (unsigned short) (rows > 0 ? rows : 24);
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;

    if (android_openpty(&master, &slave, &ws) < 0) {
        LOGI("openpty échoué");
        return NULL;
    }
    fcntl(master, F_SETFD, FD_CLOEXEC);
    fcntl(slave, F_SETFD, FD_CLOEXEC);

    jintArray result = (*env)->NewIntArray(env, 2);
    if (!result) {
        close(master);
        close(slave);
        return NULL;
    }
    jint values[2] = { master, slave };
    (*env)->SetIntArrayRegion(env, result, 0, 2, values);

    LOGI("PTY créé master=%d slave=%d", master, slave);
    return result;
}

JNIEXPORT void JNICALL
Java_com_voidlinux_core_1native_NativeBridge_resizePty(JNIEnv *env, jclass clazz,
                                                     jint fd, jint cols, jint rows) {
    struct winsize ws;
    ws.ws_col = (unsigned short) (cols > 0 ? cols : 80);
    ws.ws_row = (unsigned short) (rows > 0 ? rows : 24);
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;
    ioctl(fd, TIOCSWINSZ, &ws);
}

JNIEXPORT void JNICALL
Java_com_voidlinux_core_1native_NativeBridge_closePty(JNIEnv *env, jclass clazz, jint fd) {
    if (fd >= 0) close(fd);
}
