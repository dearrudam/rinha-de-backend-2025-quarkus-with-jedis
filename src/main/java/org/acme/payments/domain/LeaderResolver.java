package org.acme.payments.domain;

import java.time.Duration;

public interface LeaderResolver {
    boolean amILeader(String myId, Duration ttl);
}