package com.xbu.esportscenter.platform.android;

import android.view.KeyEvent;

import com.xbu.esportscenter.core.boot.ShoulderBootStateMachine;

/**
 * Narrow Android input adapter for standard gamepad shoulder buttons plus evidence-backed
 * REDMAGIC/Nubia F7/F8 shoulder KeyEvents when a ROM chooses to expose them to the app.
 * Raw SAR input that is intercepted before Android dispatch is handled by the separate
 * privileged REDMAGIC reader, not by widening this adapter.
 */
public final class AndroidShoulderKeyAdapter {
    public ShoulderBootStateMachine.Side map(KeyEvent event) {
        if (event == null) return null;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_F7:
                return ShoulderBootStateMachine.Side.LEFT;
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_F8:
                return ShoulderBootStateMachine.Side.RIGHT;
            default:
                return null;
        }
    }
}
