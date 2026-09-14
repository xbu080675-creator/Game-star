package com.xbu.esportscenter.core.boot;

/**
 * Process-local semantic input rendezvous for first-boot shoulder calibration.
 *
 * Core owns only LEFT/RIGHT + DOWN/UP semantics. Platform adapters may publish input without
 * exposing Android, REDMAGIC, Shizuku, device paths or shell concepts to the state machine.
 */
public final class ShoulderBootInputHub {
    public interface Sink {
        void onShoulderInput(ShoulderBootStateMachine.Side side, boolean down, long nowMs);
    }

    private static volatile Sink activeSink;

    private ShoulderBootInputHub() {
    }

    public static synchronized void attach(Sink sink) {
        activeSink = sink;
    }

    public static synchronized void detach(Sink sink) {
        if (activeSink == sink) activeSink = null;
    }

    public static boolean publish(ShoulderBootStateMachine.Side side, boolean down, long nowMs) {
        if (side == null) return false;
        Sink sink = activeSink;
        if (sink == null) return false;
        sink.onShoulderInput(side, down, nowMs);
        return true;
    }
}
