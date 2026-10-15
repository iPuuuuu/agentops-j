package com.ipuuuuu.agentops.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class RedisConfigurationTest {
    @Test
    void springConfigurationClassCanBeProxied() {
        assertFalse(Modifier.isFinal(RedisConfiguration.class.getModifiers()));
    }
}
