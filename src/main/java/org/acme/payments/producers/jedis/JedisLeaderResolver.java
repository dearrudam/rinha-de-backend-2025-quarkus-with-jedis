package org.acme.payments.producers.jedis;

import org.acme.payments.domain.LeaderResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.commands.StringCommands;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;

public record JedisLeaderResolver(StringCommands jedisStringCommands) implements LeaderResolver {

    public final static Logger logger= LoggerFactory.getLogger(JedisLeaderResolver.class);

    public static final String LEADER = "leader";

    @Override
    public boolean amILeader(String myId, Duration ttl) {
        var ret = jedisStringCommands.set(LEADER, myId, SetParams.setParams().nx().ex(ttl.getSeconds()));
        if ("OK".equals(ret)) {
            // this id is the leader
            logger.info("Successfully set leader for '{}'", myId);
            return true;
        }
        return myId.equals(jedisStringCommands.get(LEADER));
    }
}
