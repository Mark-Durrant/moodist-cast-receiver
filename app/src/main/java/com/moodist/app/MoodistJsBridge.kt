package com.moodist.app

import android.webkit.JavascriptInterface
import org.json.JSONObject

class MoodistJsBridge(
    private val onStateChanged: (MoodistState) -> Unit,
    private val onReady: () -> Unit
) {
    @JavascriptInterface
    fun onAppReady() {
        onReady()
    }

    @JavascriptInterface
    fun pushState(stateJson: String) {
        val state = MoodistState.fromJson(stateJson)
        if (state != null) {
            onStateChanged(state)
        }
    }
}

/**
 * Injected into the Moodist WebView after each page load.
 *
 * Reads state from localStorage (Zustand persist key "moodist-sounds") and polls
 * every 500 ms for changes, pushing them to the Android bridge via pushState().
 *
 * This avoids needing access to Zustand's internal store API.
 */
const val MOODIST_INJECTED_JS = """
(function() {
    if (window.__moodistBridgeInstalled) return;
    window.__moodistBridgeInstalled = true;

    var lastJson = '';

    function readState() {
        try {
            // Moodist persists its sound store under "moodist-sounds" in localStorage.
            var raw = localStorage.getItem('moodist-sounds');
            if (!raw) return null;
            var parsed = JSON.parse(raw);
            // Zustand persist wraps state under a "state" key
            var stored = parsed.state || parsed;
            var sounds = stored.sounds || {};
            var globalVolume = stored.globalVolume !== undefined ? stored.globalVolume : 1;

            // Build a simplified sounds map with only selected+volume
            var simplified = {};
            Object.keys(sounds).forEach(function(id) {
                var s = sounds[id];
                if (s && s.selected) {
                    simplified[id] = {
                        selected: true,
                        volume: s.volume !== undefined ? s.volume : 1
                    };
                }
            });

            return JSON.stringify({
                isPlaying: true,
                globalVolume: globalVolume,
                sounds: simplified
            });
        } catch(e) {
            return null;
        }
    }

    function pushIfChanged() {
        var json = readState();
        if (json && json !== lastJson) {
            lastJson = json;
            if (window.MoodistAndroid) {
                window.MoodistAndroid.pushState(json);
            }
        }
    }

    // Push immediately and then poll for changes every 500ms
    pushIfChanged();
    setInterval(pushIfChanged, 500);

    // Signal ready
    if (window.MoodistAndroid) {
        window.MoodistAndroid.onAppReady();
    }
})();
"""
