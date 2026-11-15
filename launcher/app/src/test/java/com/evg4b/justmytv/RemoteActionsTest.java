package com.evg4b.justmytv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class RemoteActionsTest {
    @Test public void everyActionHasALabelAndThePrefix() {
        assertEquals(RemoteActions.IDS.length, RemoteActions.LABELS.length);
        for (int i = 0; i < RemoteActions.IDS.length; i++) {
            assertTrue(RemoteActions.isAction(RemoteActions.IDS[i]));
            assertEquals(i, RemoteActions.index(RemoteActions.IDS[i]));
        }
    }

    @Test public void tellsActionsFromAppsAndUnknownOnes() {
        assertFalse(RemoteActions.isAction("org.smarttube.stable"));
        assertFalse(RemoteActions.isAction(RemoteKeyService.DISABLED));
        assertEquals(-1, RemoteActions.index("@from_a_newer_version"));   // the button keeps its own job
        assertEquals(-1, RemoteActions.index("org.smarttube.stable"));
    }

    @Test public void anActionSurvivesTheStateFile() {
        StateJson s = new StateJson();
        s.remote.put("139", "@launcher_settings");
        assertEquals("@launcher_settings", StateJson.parse(s.toJson()).remote.get("139"));
    }

    /** The ids are stored in the state file: renaming one would silently unassign a button. */
    @Test public void storedIdsDoNotChange() {
        assertEquals("@launcher_settings", RemoteActions.LAUNCHER_SETTINGS);
        assertEquals("@inputs", RemoteActions.INPUTS);
    }

    @Test public void onlyTheLauncherPanelsGoThroughHome() {
        assertEquals(RemoteActions.PANEL_SETTINGS, RemoteActions.panelFor("@launcher_settings"));
        assertEquals(RemoteActions.PANEL_INPUTS, RemoteActions.panelFor("@inputs"));
        for (String id : RemoteActions.IDS) {
            if (!id.equals("@launcher_settings") && !id.equals("@inputs")) assertNull(id, RemoteActions.panelFor(id));
        }
        assertNull(RemoteActions.panelFor("org.smarttube.stable"));
        assertNull(RemoteActions.panelFor(RemoteKeyService.DISABLED));
    }

    @Test public void opensSettingsOnlyWithTheSettingsAction() {
        Map<String, String> m = new HashMap<>();
        assertFalse(RemoteActions.opensSettings(m));
        m.put("245", "org.smarttube.stable");
        m.put("242", RemoteKeyService.DISABLED);
        m.put("233", "@tv_settings");                              // the TV's settings, not the launcher's
        m.put("204", "@inputs");
        assertFalse(RemoteActions.opensSettings(m));
        m.put("139", "@launcher_settings");
        assertTrue(RemoteActions.opensSettings(m));
    }

    /** The gear hides only when wanted, a button opens the settings, and the service that runs it is bound. */
    @Test public void settingsIconNeverLeavesTheSettingsOutOfReach() {
        Map<String, String> none = new HashMap<>(), mapped = new HashMap<>();
        mapped.put("233", "@launcher_settings");
        for (boolean hide : new boolean[]{false, true}) {
            for (boolean running : new boolean[]{false, true}) {
                assertTrue(RemoteActions.settingsIconShown(hide, none, running));
                assertEquals(!(hide && running), RemoteActions.settingsIconShown(hide, mapped, running));
            }
        }
    }
}
