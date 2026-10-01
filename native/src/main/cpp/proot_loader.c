/*
 * proot_loader.c
 * Loader minimal pour proot sous Android.
 * Contourne la politique W^X en chargeant les ELF depuis un fd mémoire.
 */

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <android/log.h>
#include <errno.h>
#include <elf.h>

/*
 * memfd_create() n'est déclarée dans la libc Android qu'à partir de l'API 30,
 * alors que minSdk = 29. On passe donc par l'appel système directement.
 */
#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC 0x0001U
#endif

static int void_memfd_create(const char *name, unsigned int flags) {
    return (int) syscall(__NR_memfd_create, name, flags);
}

#define LOG_TAG "VoidProotLoader"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/*
 * Charge un ELF en mémoire et retourne un fd exécutable,
 * contournant la restriction W^X des répertoires data.
 */
static int load_elf_to_memfd(const char *path) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        LOGE("open(%s) : %s", path, strerror(errno));
        return -1;
    }

    struct stat st;
    if (fstat(fd, &st) < 0) {
        LOGE("fstat : %s", strerror(errno));
        close(fd);
        return -1;
    }

    size_t size = (size_t) st.st_size;
    void *mem = mmap(NULL, size, PROT_READ | PROT_WRITE,
                     MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (mem == MAP_FAILED) {
        LOGE("mmap : %s", strerror(errno));
        close(fd);
        return -1;
    }

    ssize_t read_bytes = read(fd, mem, size);
    close(fd);
    if (read_bytes != (ssize_t) size) {
        LOGE("read incomplet");
        munmap(mem, size);
        return -1;
    }

    // Créer un memfd
    int memfd = void_memfd_create("proot_loader", MFD_CLOEXEC);
    if (memfd < 0) {
        LOGE("memfd_create : %s", strerror(errno));
        munmap(mem, size);
        return -1;
    }

    if (write(memfd, mem, size) != (ssize_t) size) {
        LOGE("write memfd : %s", strerror(errno));
        close(memfd);
        munmap(mem, size);
        return -1;
    }

    munmap(mem, size);
    lseek(memfd, 0, SEEK_SET);
    return memfd;
}

JNIEXPORT jint JNICALL
Java_com_voidlinux_core_native_NativeBridge_loadElf(JNIEnv *env, jclass clazz, jstring path) {
    const char *cpath = (*env)->GetStringUTFChars(env, path, NULL);
    if (!cpath) return -1;

    int fd = load_elf_to_memfd(cpath);

    (*env)->ReleaseStringUTFChars(env, path, cpath);
    LOGI("loadElf -> fd=%d", fd);
    return fd;
}

JNIEXPORT jint JNICALL
Java_com_voidlinux_core_native_NativeBridge_checkWxSupported(JNIEnv *env, jclass clazz) {
    // Android 10+ : W^X est appliqué
    // Retourne 1 si le contournement est nécessaire
    return 1;
}