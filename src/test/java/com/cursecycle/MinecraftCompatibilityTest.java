package com.cursecycle;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftCompatibilityTest {
    @Test
    void metadataRejectsOtherMinecraftPatchesIncluding12111() throws Exception {
        try (var input = getClass().getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(input);
            var metadata = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            var supported = VersionPredicate.parse(metadata.getAsJsonObject("depends").get("minecraft").getAsString());
            assertTrue(supported.test(Version.parse("1.21.1")));
            for (String version : new String[]{"1.21", "1.21.2", "1.21.4", "1.21.10", "1.21.11"}) {
                assertFalse(supported.test(Version.parse(version)), version + " must be rejected before Mixin loading");
            }
        }
    }
}
