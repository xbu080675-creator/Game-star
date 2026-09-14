package com.xbu.esportscenter.privileged;

import com.xbu.esportscenter.privileged.IRedMagicShoulderCallback;

interface IRedMagicShoulderReader {
    int enableCalibrationScene();
    int restoreCalibrationScene();
    String detectDevices();
    void startReading(IRedMagicShoulderCallback callback);
    void stopReading();
}
