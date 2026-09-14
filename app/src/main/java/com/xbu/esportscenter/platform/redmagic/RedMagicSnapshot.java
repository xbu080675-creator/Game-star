package com.xbu.esportscenter.platform.redmagic;

import org.json.JSONException;
import org.json.JSONObject;

/** Read-only snapshot of REDMAGIC/Nubia game-related system state. */
public final class RedMagicSnapshot {
    public final boolean likelyRedMagic;
    public final String manufacturer;
    public final String model;
    public final Integer gameSpaceSwitch;
    public final Integer gameScene;
    public final Integer gameMode;

    public RedMagicSnapshot(
            boolean likelyRedMagic,
            String manufacturer,
            String model,
            Integer gameSpaceSwitch,
            Integer gameScene,
            Integer gameMode
    ) {
        this.likelyRedMagic = likelyRedMagic;
        this.manufacturer = manufacturer == null ? "" : manufacturer;
        this.model = model == null ? "" : model;
        this.gameSpaceSwitch = gameSpaceSwitch;
        this.gameScene = gameScene;
        this.gameMode = gameMode;
    }

    public boolean isCompetitiveSwitchOn() {
        return gameSpaceSwitch != null && gameSpaceSwitch == 0;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("likelyRedMagic", likelyRedMagic);
        json.put("manufacturer", manufacturer);
        json.put("model", model);
        json.put("competitiveSwitchOn", isCompetitiveSwitchOn());
        json.put("gcsNeedKillGameLauncher", gameSpaceSwitch == null ? JSONObject.NULL : gameSpaceSwitch);
        json.put("nubiaGameScene", gameScene == null ? JSONObject.NULL : gameScene);
        json.put("nubiaGameMode", gameMode == null ? JSONObject.NULL : gameMode);
        return json;
    }
}
