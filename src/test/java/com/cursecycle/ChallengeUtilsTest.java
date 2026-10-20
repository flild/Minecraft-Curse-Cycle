package com.cursecycle;

import com.cursecycle.challenge.ChallengeUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ChallengeUtilsTest {

    @Test
    public void testIsDoubleChallenge() {
        assertTrue(ChallengeUtils.isDoubleChallenge("double_challenge"));
        assertTrue(ChallengeUtils.isDoubleChallenge("double_challenge:no_sprint,test_easy"));
        assertFalse(ChallengeUtils.isDoubleChallenge("no_sprint"));
        assertFalse(ChallengeUtils.isDoubleChallenge(null));
    }

    @Test
    public void testGetBaseChallengeId() {
        assertEquals("double_challenge", ChallengeUtils.getBaseChallengeId("double_challenge:no_sprint,test_easy"));
        assertEquals("no_sprint", ChallengeUtils.getBaseChallengeId("no_sprint"));
        assertEquals("", ChallengeUtils.getBaseChallengeId(null));
    }

    @Test
    public void testParseSubChallengeIds() {
        List<String> subIds = ChallengeUtils.parseSubChallengeIds("double_challenge:no_sprint, test_easy ");
        assertEquals(2, subIds.size());
        assertEquals("no_sprint", subIds.get(0));
        assertEquals("test_easy", subIds.get(1));

        assertTrue(ChallengeUtils.parseSubChallengeIds("no_sprint").isEmpty());
    }

    @Test
    public void testEncodeDoubleChallenge() {
        String encoded = ChallengeUtils.encodeDoubleChallenge(List.of("no_sprint", "test_easy"));
        assertEquals("double_challenge:no_sprint,test_easy", encoded);
    }
}
