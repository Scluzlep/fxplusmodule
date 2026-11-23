package com.scluzlep.fxplusmodule.shizuku;

import android.os.ParcelFileDescriptor;

interface IPrivilegedStorageService {
    String listEntriesJson(String path);
    String statJson(String path);
    ParcelFileDescriptor openRead(String path);
    ParcelFileDescriptor openWrite(String path, boolean append);
    boolean mkdirs(String path);
    boolean deleteEntry(String path, boolean recursive);
    boolean renameEntry(String srcPath, String dstPath);
    void destroy();
}
