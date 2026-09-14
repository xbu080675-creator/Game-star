package com.xbu.esportscenter.privileged;

/**
 * Narrow privileged surface for Game Star Box self-update only.
 * No arbitrary command, path, package or shell execution API is exposed.
 */
interface IPrivilegedInstaller {
    void destroy() = 16777114; // Reserved destroy transaction used by Shizuku UserService.

    int beginInstall(long expectedSize, String expectedSha256, String packageName);
    int writeChunk(in byte[] data, int length);
    String finishInstall();
    void cancelInstall();
}
