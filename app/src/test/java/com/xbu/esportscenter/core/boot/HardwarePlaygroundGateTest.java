package com.xbu.esportscenter.core.boot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class HardwarePlaygroundGateTest {
    @Test
    public void interactiveIgnitionRequiresHardwarePlaygroundAndProvisioning() {
        IgnitionGate gate = new IgnitionGate(IgnitionGate.SessionMode.INTERACTIVE);
        gate.setHardwareReady(true);
        gate.setProvisioningReady(true);
        assertFalse(gate.snapshot().open);

        gate.setPlaygroundReady(true);
        assertTrue(gate.snapshot().open);
        assertEquals(IgnitionGate.SessionMode.INTERACTIVE, gate.snapshot().mode);
    }

    @Test
    public void fastModeBypassesOnlyInteractivePlayground() {
        IgnitionGate gate = new IgnitionGate(IgnitionGate.SessionMode.FAST);
        assertTrue(gate.snapshot().playgroundReady);
        assertFalse(gate.snapshot().open);

        gate.setProvisioningReady(true);
        assertFalse(gate.snapshot().open);

        gate.setHardwareReady(true);
        assertTrue(gate.snapshot().open);
        assertEquals(IgnitionGate.SessionMode.FAST, gate.snapshot().mode);
    }

    @Test
    public void playgroundRejectsSkippedOrOutOfOrderStages() {
        HardwarePlaygroundProgress progress = new HardwarePlaygroundProgress(
                HardwarePlaygroundProgress.SessionMode.INTERACTIVE
        );
        assertEquals(HardwarePlaygroundProgress.Stage.SHOULDERS, progress.snapshot().stage);

        progress.complete(HardwarePlaygroundProgress.Stage.GLASS);
        assertEquals(HardwarePlaygroundProgress.Stage.SHOULDERS, progress.snapshot().stage);
        assertEquals(0, progress.snapshot().completedStages);

        progress.complete(HardwarePlaygroundProgress.Stage.SHOULDERS);
        progress.complete(HardwarePlaygroundProgress.Stage.MOTION);
        progress.complete(HardwarePlaygroundProgress.Stage.GLASS);
        progress.complete(HardwarePlaygroundProgress.Stage.MECHANICS);
        progress.complete(HardwarePlaygroundProgress.Stage.THERMAL);

        assertEquals(HardwarePlaygroundProgress.Stage.FINAL_GRIP, progress.snapshot().stage);
        assertTrue(progress.snapshot().finalGripEnabled);
        assertFalse(progress.snapshot().complete);

        progress.complete(HardwarePlaygroundProgress.Stage.FINAL_GRIP);
        assertEquals(HardwarePlaygroundProgress.Stage.COMPLETE, progress.snapshot().stage);
        assertTrue(progress.snapshot().complete);
    }

    @Test
    public void earlyBothHoldCannotArmAndFinalGripRequiresFreshPress() {
        ShoulderBootStateMachine machine = calibratedToBothHold();
        try {
            assertEquals(ShoulderBootStateMachine.Phase.BOTH_HOLD, machine.snapshot().phase);
            assertFalse(machine.snapshot().finalGripEnabled);

            machine.onInput(ShoulderBootStateMachine.Side.LEFT, true, 3000L);
            machine.onInput(ShoulderBootStateMachine.Side.RIGHT, true, 3000L);
            machine.tick(5000L);
            assertEquals(ShoulderBootStateMachine.Phase.BOTH_HOLD, machine.snapshot().phase);
            assertEquals(0, machine.snapshot().progress);

            machine.setFinalGripEnabled(true);
            assertTrue(machine.snapshot().finalGripEnabled);
            assertTrue(machine.snapshot().freshBothRequired);
            machine.tick(7000L);
            assertEquals(ShoulderBootStateMachine.Phase.BOTH_HOLD, machine.snapshot().phase);
            assertEquals(0, machine.snapshot().progress);

            machine.onInput(ShoulderBootStateMachine.Side.LEFT, false, 7100L);
            machine.onInput(ShoulderBootStateMachine.Side.RIGHT, false, 7110L);
            assertFalse(machine.snapshot().freshBothRequired);

            machine.onInput(ShoulderBootStateMachine.Side.LEFT, true, 7200L);
            machine.onInput(ShoulderBootStateMachine.Side.RIGHT, true, 7200L);
            machine.tick(7200L + ShoulderBootStateMachine.IGNITION_HOLD_MS);
            assertEquals(ShoulderBootStateMachine.Phase.ARMED, machine.snapshot().phase);
        } finally {
            machine.complete();
        }
    }

    private static ShoulderBootStateMachine calibratedToBothHold() {
        ShoulderBootStateMachine machine = new ShoulderBootStateMachine();

        machine.onInput(ShoulderBootStateMachine.Side.LEFT, true, 0L);
        machine.onInput(ShoulderBootStateMachine.Side.LEFT, false, 100L);
        assertEquals(ShoulderBootStateMachine.Phase.LEFT_HOLD, machine.snapshot().phase);

        machine.onInput(ShoulderBootStateMachine.Side.LEFT, true, 200L);
        machine.tick(200L + ShoulderBootStateMachine.CALIBRATION_HOLD_MS);
        machine.onInput(
                ShoulderBootStateMachine.Side.LEFT,
                false,
                200L + ShoulderBootStateMachine.CALIBRATION_HOLD_MS
        );
        assertEquals(ShoulderBootStateMachine.Phase.RIGHT_TAP, machine.snapshot().phase);

        machine.onInput(ShoulderBootStateMachine.Side.RIGHT, true, 1500L);
        machine.onInput(ShoulderBootStateMachine.Side.RIGHT, false, 1600L);
        assertEquals(ShoulderBootStateMachine.Phase.RIGHT_HOLD, machine.snapshot().phase);

        machine.onInput(ShoulderBootStateMachine.Side.RIGHT, true, 1700L);
        machine.tick(1700L + ShoulderBootStateMachine.CALIBRATION_HOLD_MS);
        machine.onInput(
                ShoulderBootStateMachine.Side.RIGHT,
                false,
                1700L + ShoulderBootStateMachine.CALIBRATION_HOLD_MS
        );
        return machine;
    }
}
