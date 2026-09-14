package com.xbu.esportscenter.privileged;

import com.xbu.esportscenter.privileged.IRedMagicShoulderCallback;

interface IRedMagicShoulderReader {
    String detectDevices();
    void startReading(IRedMagicShoulderCallback callback);
    void stopReading();
}
