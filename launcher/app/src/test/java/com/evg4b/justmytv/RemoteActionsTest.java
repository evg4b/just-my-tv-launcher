package com.evg4b.justmytv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
