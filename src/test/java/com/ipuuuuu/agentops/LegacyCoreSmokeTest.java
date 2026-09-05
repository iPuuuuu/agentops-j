package com.ipuuuuu.agentops;

import org.junit.jupiter.api.Test;

/** Runs the original dependency-free core tests under Maven/Surefire. */
class LegacyCoreSmokeTest {
    @Test
    void coreBehaviorRemainsCovered() throws Exception {
        AgentOpsApplicationTest.main(new String[0]);
    }
}
