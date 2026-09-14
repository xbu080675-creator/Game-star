package com.xbu.esportscenter.platform.android;

import android.view.KeyEvent;

import com.xbu.esportscenter.core.boot.ShoulderBootStateMachine;

/**
 * Narrow Android input adapter for standard gamepad shoulder buttons.
 * REDMAGIC capacitive shoulder keys are intentionally not guessed here; unknown key codes are
 * logged by the Activity during first-boot testing so a vendor adapter can be added from evidence.
 */
public final class AndroidShoulderKeyAdapter {
    public ShoulderBootStateMachine.Side map(KeyEvent event) {
        if (event == null) return null;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_L2:
                return ShoulderBootStateMachine.Side.LEFT;
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_R2:
                return ShoulderBootStateMachine.Side.RIGHT;
            default:
                return null;
        }
    }
}
